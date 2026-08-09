package com.mcp.engine.memory;

import com.mcp.core.entity.MemoryPackageEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MemoryConflictResolver {

    public record ConflictGroup(
            String topic,                          // 冲突主题
            List<MemoryPackageEntity> conflicting,  // 冲突的记忆
            Resolution resolution,                  // 解决策略
            String mergedContent                    // 合并后的内容
    ) {}

    public enum Resolution {
        KEEP_LATEST,     // 保留最新
        KEEP_HIGHEST,    // 保留优先级最高的
        MERGE,           // 合并
        MARK_INACTIVE,   // 标记旧记忆为非活跃
        NONE             // 无冲突
    }

    public List<ConflictGroup> detectAndResolve(List<MemoryPackageEntity> memories) {
        Map<String, List<MemoryPackageEntity>> topicGroups = groupByTopic(memories);
        List<ConflictGroup> resolved = new ArrayList<>();

        for (var entry : topicGroups.entrySet()) {
            List<MemoryPackageEntity> group = entry.getValue();
            if (group.size() <= 1) continue;

            ConflictGroup cg = resolveConflict(entry.getKey(), group);
            resolved.add(cg);
        }

        return resolved;
    }

    private Map<String, List<MemoryPackageEntity>> groupByTopic(List<MemoryPackageEntity> memories) {
        Map<String, List<MemoryPackageEntity>> groups = new LinkedHashMap<>();
        for (MemoryPackageEntity mem : memories) {
            String topic = extractTopic(mem.getContent());
            groups.computeIfAbsent(topic, k -> new ArrayList<>()).add(mem);
        }
        return groups.entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));
    }

    private String extractTopic(String content) {
        if (content == null || content.isBlank()) return "empty";
        String[] words = content.split("[\\s，,。.!！?？]+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(3, words.length); i++) {
            if (words[i].length() >= 2) {
                sb.append(words[i]);
            }
        }
        return sb.length() > 0 ? sb.toString() : content.substring(0, Math.min(10, content.length()));
    }

    private ConflictGroup resolveConflict(String topic, List<MemoryPackageEntity> group) {
        group.sort((a, b) -> b.getLastAccessedAt().compareTo(a.getLastAccessedAt()));

        MemoryPackageEntity latest = group.get(0);
        MemoryPackageEntity highestImportance = group.stream()
                .max((a, b) -> Integer.compare(a.getImportance(), b.getImportance()))
                .orElse(latest);

        if (latest.getId().equals(highestImportance.getId())) {
            for (int i = 1; i < group.size(); i++) {
                group.get(i).setActive(false);
            }
            return new ConflictGroup(topic, group, Resolution.KEEP_LATEST, latest.getContent());
        }

        if (highestImportance.getImportance() - latest.getImportance() >= 30) {
            for (MemoryPackageEntity m : group) {
                if (!m.getId().equals(highestImportance.getId())) {
                    m.setActive(false);
                }
            }
            return new ConflictGroup(topic, group, Resolution.KEEP_HIGHEST,
                    highestImportance.getContent());
        }

        String merged = mergeContent(latest.getContent(), highestImportance.getContent());
        for (MemoryPackageEntity m : group) {
            m.setActive(false);
        }
        latest.setContent(merged);
        latest.setActive(true);
        return new ConflictGroup(topic, group, Resolution.MERGE, merged);
    }

    private String mergeContent(String content1, String content2) {
        if (content1 == null) return content2;
        if (content2 == null) return content1;
        if (content1.equals(content2)) return content1;

        if (content1.length() > content2.length()) {
            return content1;
        }
        return content1 + "；" + content2;
    }
}