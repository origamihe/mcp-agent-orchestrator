package com.mcp.engine.artifact;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcp.common.artifact.Artifact;
import com.mcp.common.artifact.ArtifactType;
import com.mcp.core.entity.ArtifactEntity;
import com.mcp.core.repository.ArtifactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Artifact 服务 — 管理临时可编辑对象的生命周期。
 * Artifact 与 Memory 完全解耦：Memory 存长期知识，Artifact 存临时工作对象。
 *
 * 生命周期：create → modify → snapshot → delete
 * （不同于 Memory 的 create → merge → compress → forget）
 *
 * P0 增强：支持 title、mimeType、metadata、createdBy 字段。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArtifactService {

    private final ArtifactRepository artifactRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ArtifactRecallStrategy recallStrategy;

    private static final TypeReference<Map<String, Object>> METADATA_TYPE = new TypeReference<>() {};

    @Transactional
    public Artifact create(String sessionId, String path, ArtifactType type, String content, String encoding) {
        Artifact artifact = new Artifact(path, type, content, encoding, content != null ? content.length() : 0);
        ArtifactEntity entity = toEntity(artifact, sessionId);
        entity = artifactRepository.save(entity);
        log.info("[Artifact] Created: type={}, path={}, size={}, session={}",
                type, path, artifact.getSize(), sessionId);
        return toModel(entity);
    }

    @Transactional
    public Artifact saveArtifact(String sessionId, Artifact artifact) {
        ArtifactEntity entity = toEntity(artifact, sessionId);
        entity = artifactRepository.save(entity);
        log.info("[Artifact] Saved: type={}, title={}, id={}, session={}",
                artifact.getType(), artifact.getTitle(), artifact.getId(), sessionId);
        return toModel(entity);
    }

    @Transactional
    public Artifact createOrUpdate(String sessionId, String path, ArtifactType type, String content, String encoding) {
        Optional<ArtifactEntity> existing = artifactRepository.findBySessionIdAndFilePathAndDeletedAtIsNull(sessionId, path);
        if (existing.isPresent()) {
            ArtifactEntity entity = existing.get();
            Artifact artifact = toModel(entity);
            artifact.setContent(content);
            artifact.incrementVersion();
            entity.setContent(artifact.getContent());
            entity.setSizeBytes(artifact.getSize());
            entity.setVersion(artifact.getVersion());
            entity.setModifiedAt(artifact.getModifiedAt());
            entity.setIsDirty(true);
            artifactRepository.save(entity);
            log.info("[Artifact] Updated: type={}, path={}, v{}, size={}, session={}",
                    type, path, artifact.getVersion(), artifact.getSize(), sessionId);
            return artifact;
        } else {
            return create(sessionId, path, type, content, encoding);
        }
    }

    @Transactional(readOnly = true)
    public Optional<Artifact> findById(String id) {
        return artifactRepository.findByIdAndDeletedAtIsNull(id)
                .map(this::toModel);
    }

    @Transactional(readOnly = true)
    public Optional<Artifact> findByPath(String sessionId, String path) {
        return artifactRepository.findBySessionIdAndFilePathAndDeletedAtIsNull(sessionId, path)
                .map(this::toModel);
    }

    @Transactional(readOnly = true)
    public List<Artifact> findBySession(String sessionId) {
        return artifactRepository.findBySessionIdAndDeletedAtIsNullOrderByModifiedAtDesc(sessionId)
                .stream()
                .map(this::toModel)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Artifact> findByType(String sessionId, ArtifactType type) {
        return artifactRepository.findBySessionIdAndArtifactTypeAndDeletedAtIsNull(sessionId, type.name())
                .stream()
                .map(this::toModel)
                .collect(Collectors.toList());
    }

    @Transactional
    public boolean deleteByPath(String sessionId, String path) {
        int affected = artifactRepository.softDeleteByPath(sessionId, path, Instant.now());
        log.info("[Artifact] Deleted: path={}, affected={}, session={}", path, affected, sessionId);
        return affected > 0;
    }

    @Transactional
    public int deleteAllBySession(String sessionId) {
        int count = artifactRepository.softDeleteAllBySession(sessionId, Instant.now());
        log.info("[Artifact] Deleted all: count={}, session={}", count, sessionId);
        return count;
    }

    @Transactional
    public Artifact markClean(String sessionId, String path) {
        Optional<ArtifactEntity> opt = artifactRepository.findBySessionIdAndFilePathAndDeletedAtIsNull(sessionId, path);
        if (opt.isPresent()) {
            ArtifactEntity entity = opt.get();
            entity.setIsDirty(false);
            artifactRepository.save(entity);
            log.info("[Artifact] Marked clean: path={}, session={}", path, sessionId);
            return toModel(entity);
        }
        return null;
    }

    public String buildArtifactContextPrompt(String sessionId) {
        List<Artifact> artifacts = findBySession(sessionId);
        if (artifacts.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("【当前 Artifact 上下文】\n");
        sb.append("以下是你当前会话中的可编辑对象，与长期记忆（Memory）完全独立：\n\n");

        for (Artifact artifact : artifacts) {
            sb.append("--- Artifact: ");
            if (artifact.getTitle() != null && !artifact.getTitle().isBlank()) {
                sb.append("\"").append(artifact.getTitle()).append("\"");
            } else if (artifact.getPath() != null) {
                sb.append(artifact.getPath());
            } else {
                sb.append(artifact.getId());
            }
            sb.append(" (").append(artifact.getType()).append(")");
            sb.append(" v").append(artifact.getVersion());
            sb.append(" ---\n");
            sb.append(artifact.getContent()).append("\n");
            if (artifact.getModifiedAt() != null) {
                sb.append("(最后修改: ").append(artifact.getModifiedAt()).append(")\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * 增量召回：根据用户消息从文档中检索相关段落。
     * 委托给 ArtifactRecallStrategy 执行，将召回逻辑与 Prompt 组装解耦。
     *
     * @param artifactId   Artifact ID
     * @param userMessage  用户当前消息（用于相关性匹配）
     * @param summaryCache 缓存的摘要（可为 null，由 RecallStrategy 自动生成）
     * @return 召回的文本片段（已控制在 ~3000 chars 以内）
     */
    public String recallRelevantContent(String artifactId, String userMessage, String summaryCache) {
        Optional<Artifact> opt = findById(artifactId);
        if (opt.isEmpty()) {
            return "";
        }
        return recallStrategy.recall(opt.get(), userMessage, summaryCache);
    }

    public String recallByPath(String sessionId, String path, String userMessage, String summaryCache) {
        Optional<Artifact> opt = findByPath(sessionId, path);
        if (opt.isEmpty()) {
            return "";
        }
        return recallStrategy.recall(opt.get(), userMessage, summaryCache);
    }

    private ArtifactEntity toEntity(Artifact artifact, String sessionId) {
        ArtifactEntity entity = new ArtifactEntity();
        entity.setId(artifact.getId());
        entity.setSessionId(sessionId);
        entity.setArtifactType(artifact.getType() != null ? artifact.getType().name() : ArtifactType.TEXT.name());
        entity.setTitle(artifact.getTitle());
        entity.setFilePath(artifact.getPath());
        entity.setContent(artifact.getContent());
        entity.setMimeType(artifact.getMimeType());
        entity.setEncoding(artifact.getEncoding());
        entity.setMetadata(toJson(artifact.getMetadata()));
        entity.setCreatedBy(artifact.getCreatedBy());
        entity.setSizeBytes(artifact.getSize());
        entity.setVersion(artifact.getVersion());
        entity.setIsDirty(artifact.isDirty());
        entity.setCreatedAt(artifact.getCreatedAt());
        entity.setModifiedAt(artifact.getModifiedAt());
        return entity;
    }

    private Artifact toModel(ArtifactEntity entity) {
        Artifact artifact = new Artifact();
        artifact.setId(entity.getId());
        artifact.setType(tryParseType(entity.getArtifactType()));
        artifact.setTitle(entity.getTitle());
        artifact.setPath(entity.getFilePath());
        artifact.setContent(entity.getContent());
        artifact.setMimeType(entity.getMimeType());
        artifact.setEncoding(entity.getEncoding());
        artifact.setMetadata(fromJsonMetadata(entity.getMetadata()));
        artifact.setCreatedBy(entity.getCreatedBy());
        artifact.setSize(entity.getSizeBytes() != null ? entity.getSizeBytes() : 0);
        artifact.setVersion(entity.getVersion() != null ? entity.getVersion() : 1);
        artifact.setDirty(entity.getIsDirty() != null && entity.getIsDirty());
        artifact.setCreatedAt(entity.getCreatedAt());
        artifact.setModifiedAt(entity.getModifiedAt());
        return artifact;
    }

    private ArtifactType tryParseType(String typeStr) {
        try {
            return ArtifactType.valueOf(typeStr);
        } catch (Exception e) {
            return ArtifactType.TEXT;
        }
    }

    private String toJson(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            log.warn("[Artifact] Failed to serialize metadata: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, Object> fromJsonMetadata(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(json, METADATA_TYPE);
        } catch (JsonProcessingException e) {
            log.warn("[Artifact] Failed to parse metadata: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }
}