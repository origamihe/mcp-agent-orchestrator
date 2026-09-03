package com.mcp.gateway.controller;

import com.mcp.core.entity.KnowledgeChunkEntity;
import com.mcp.core.entity.KnowledgeCollectionEntity;
import com.mcp.core.entity.KnowledgeDocumentEntity;
import com.mcp.core.repository.KnowledgeChunkRepository;
import com.mcp.core.repository.KnowledgeCollectionRepository;
import com.mcp.core.repository.KnowledgeDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeCollectionRepository collectionRepository;
    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeChunkRepository chunkRepository;

    // ==================== Collections ====================

    @GetMapping("/collections")
    public ResponseEntity<List<Map<String, Object>>> listCollections() {
        List<Map<String, Object>> collections = collectionRepository.findAll().stream()
                .map(this::toCollectionMap)
                .collect(Collectors.toList());
        return ResponseEntity.ok(collections);
    }

    @GetMapping("/collections/{id}")
    public ResponseEntity<Map<String, Object>> getCollection(@PathVariable String id) {
        return collectionRepository.findById(id)
                .map(this::toCollectionMap)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/collections")
    public ResponseEntity<Map<String, Object>> createCollection(@RequestBody Map<String, String> body) {
        KnowledgeCollectionEntity entity = new KnowledgeCollectionEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setName(body.getOrDefault("name", "Unnamed"));
        entity.setDescription(body.getOrDefault("description", ""));
        entity.setEmbeddingModel(body.getOrDefault("embeddingModel", "text-embedding-3-small"));
        collectionRepository.save(entity);
        return ResponseEntity.ok(toCollectionMap(entity));
    }

    @DeleteMapping("/collections/{id}")
    public ResponseEntity<Void> deleteCollection(@PathVariable String id) {
        if (!collectionRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        collectionRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== Documents ====================

    @GetMapping("/collections/{collectionId}/documents")
    public ResponseEntity<List<Map<String, Object>>> listDocuments(@PathVariable String collectionId) {
        if (!collectionRepository.existsById(collectionId)) {
            return ResponseEntity.notFound().build();
        }
        List<Map<String, Object>> docs = documentRepository.findByCollectionIdOrderByCreatedAtDesc(collectionId).stream()
                .map(this::toDocumentMap)
                .collect(Collectors.toList());
        return ResponseEntity.ok(docs);
    }

    @GetMapping("/{collectionId}/docs")
    public ResponseEntity<List<Map<String, Object>>> listDocs(@PathVariable String collectionId) {
        return listDocuments(collectionId);
    }

    @GetMapping("/documents/{id}")
    public ResponseEntity<Map<String, Object>> getDocument(@PathVariable String id) {
        return documentRepository.findById(id)
                .map(this::toDocumentMap)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ==================== Chunks ====================

    @GetMapping("/documents/{documentId}/chunks")
    public ResponseEntity<List<Map<String, Object>>> listChunks(@PathVariable String documentId) {
        if (!documentRepository.existsById(documentId)) {
            return ResponseEntity.notFound().build();
        }
        List<Map<String, Object>> chunks = chunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId).stream()
                .map(this::toChunkMap)
                .collect(Collectors.toList());
        return ResponseEntity.ok(chunks);
    }

    // ==================== Stats ====================

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        long totalDocuments = documentRepository.count();
        long totalChunks = chunkRepository.count();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalDocuments", totalDocuments);
        stats.put("totalChunks", totalChunks);
        stats.put("totalCollections", collectionRepository.count());
        return ResponseEntity.ok(stats);
    }

    // ==================== Mappers ====================

    private Map<String, Object> toCollectionMap(KnowledgeCollectionEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entity.getId());
        map.put("name", entity.getName());
        map.put("description", entity.getDescription());
        map.put("embeddingModel", entity.getEmbeddingModel());
        map.put("documentCount", documentRepository.countByCollectionId(entity.getId()));
        map.put("chunkCount", entity.getId());
        map.put("createdAt", entity.getCreatedAt());
        map.put("updatedAt", entity.getUpdatedAt());
        return map;
    }

    private Map<String, Object> toDocumentMap(KnowledgeDocumentEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entity.getId());
        map.put("collectionId", entity.getCollectionId());
        map.put("title", entity.getTitle());
        map.put("source", entity.getSource());
        map.put("format", entity.getFormat());
        map.put("size", entity.getSize());
        map.put("chunkCount", chunkRepository.countByDocumentId(entity.getId()));
        map.put("metadata", entity.getMetadata());
        map.put("createdAt", entity.getCreatedAt());
        return map;
    }

    private Map<String, Object> toChunkMap(KnowledgeChunkEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entity.getId());
        map.put("documentId", entity.getDocumentId());
        map.put("content", entity.getContent());
        map.put("index", entity.getChunkIndex());
        map.put("metadata", entity.getMetadata());
        return map;
    }
}