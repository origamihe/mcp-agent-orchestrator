package com.mcp.tools.search;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EvidencePool(
        String query,
        List<EvidenceItem> evidenceItems,
        int totalSources,
        int totalKeyPoints,
        Map<String, List<EvidenceItem>> sourceGroups,
        List<TopicCluster> topicClusters
) {
    public static EvidencePool of(String query, List<EvidenceItem> items) {
        int totalKeyPoints = items.stream().mapToInt(i -> i.keyPoints() != null ? i.keyPoints().size() : 0).sum();
        Map<String, List<EvidenceItem>> sourceGroups = buildSourceGroups(items);
        List<TopicCluster> topicClusters = buildTopicClusters(items);
        return new EvidencePool(query, items, items.size(), totalKeyPoints, sourceGroups, topicClusters);
    }

    public List<EvidenceItem> topEvidence() {
        return evidenceItems.stream()
                .sorted((a, b) -> Double.compare(b.confidence(), a.confidence()))
                .toList();
    }

    public boolean hasConflicts() {
        long supportive = evidenceItems.stream()
                .filter(i -> EvidenceItem.STANCE_SUPPORTIVE.equals(i.stance())).count();
        long critical = evidenceItems.stream()
                .filter(i -> EvidenceItem.STANCE_CRITICAL.equals(i.stance())).count();
        return supportive > 0 && critical > 0;
    }

    public List<List<EvidenceItem>> getConflicts() {
        List<EvidenceItem> supportive = evidenceItems.stream()
                .filter(i -> EvidenceItem.STANCE_SUPPORTIVE.equals(i.stance())).toList();
        List<EvidenceItem> critical = evidenceItems.stream()
                .filter(i -> EvidenceItem.STANCE_CRITICAL.equals(i.stance())).toList();
        if (supportive.isEmpty() || critical.isEmpty()) {
            return List.of();
        }
        return List.of(supportive, critical);
    }

    /**
     * 计算共识度：有多少比例的来源在核心观点上达成一致。
     */
    public double consensusScore() {
        if (evidenceItems.size() < 2) return 1.0;
        long neutral = evidenceItems.stream()
                .filter(i -> EvidenceItem.STANCE_NEUTRAL.equals(i.stance())).count();
        long supportive = evidenceItems.stream()
                .filter(i -> EvidenceItem.STANCE_SUPPORTIVE.equals(i.stance())).count();
        long dominant = Math.max(neutral, supportive);
        return (double) dominant / evidenceItems.size();
    }

    /**
     * 获取高可信度来源（rating >= 4）的共识证据。
     */
    public List<EvidenceItem> highCredibilityEvidence() {
        return evidenceItems.stream()
                .filter(i -> i.sourceRating() >= EvidenceItem.RATING_HIGH)
                .toList();
    }

    private static Map<String, List<EvidenceItem>> buildSourceGroups(List<EvidenceItem> items) {
        Map<String, List<EvidenceItem>> groups = new LinkedHashMap<>();
        for (EvidenceItem item : items) {
            groups.computeIfAbsent(item.source(), k -> new ArrayList<>()).add(item);
        }
        return groups;
    }

    private static List<TopicCluster> buildTopicClusters(List<EvidenceItem> items) {
        if (items.size() <= 1) return List.of();

        List<TopicCluster> clusters = new ArrayList<>();
        List<EvidenceItem> remaining = new ArrayList<>(items);

        while (!remaining.isEmpty()) {
            EvidenceItem seed = remaining.remove(0);
            List<EvidenceItem> cluster = new ArrayList<>();
            cluster.add(seed);

            List<EvidenceItem> matched = new ArrayList<>();
            for (EvidenceItem other : remaining) {
                if (hasCommonKeywords(seed.summary(), other.summary(), 3)) {
                    cluster.add(other);
                    matched.add(other);
                }
            }
            remaining.removeAll(matched);

            String topic = extractTopic(cluster);
            clusters.add(new TopicCluster(topic, List.copyOf(cluster)));
        }

        return clusters;
    }

    private static String extractTopic(List<EvidenceItem> cluster) {
        if (cluster.isEmpty()) return "Unknown";
        String title = cluster.get(0).title();
        if (title == null || title.isBlank()) return "Unknown";
        return title.length() > 50 ? title.substring(0, 50) + "..." : title;
    }

    private static boolean hasCommonKeywords(String text1, String text2, int minCommon) {
        if (text1 == null || text2 == null) return false;
        List<String> words1 = extractWords(text1);
        List<String> words2 = extractWords(text2);
        long common = words1.stream().filter(w -> w.length() >= 2 && words2.contains(w)).distinct().count();
        return common >= minCommon;
    }

    private static List<String> extractWords(String text) {
        if (text == null || text.isBlank()) return List.of();
        return List.of(text.replaceAll("[^\\u4e00-\\u9fa5a-zA-Z0-9]", " ")
                .toLowerCase()
                .split("\\s+"));
    }

    /**
     * 主题聚类，包含该主题下的所有证据和来源。
     */
    public record TopicCluster(String topic, List<EvidenceItem> items) {
        public List<String> sources() {
            return items.stream()
                    .map(EvidenceItem::source)
                    .distinct()
                    .toList();
        }

        public int sourceCount() {
            return sources().size();
        }

        public String consensusLabel() {
            int count = sourceCount();
            if (count >= 3) return "多来源一致";
            if (count == 2) return "双来源确认";
            return "单一来源（待验证）";
        }
    }
}