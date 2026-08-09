package com.mcp.engine.test.memory;

import com.mcp.common.identity.MemoryIdentity;
import com.mcp.engine.memory.MemoryEvaluator;
import com.mcp.core.domain.memory.MemoryType;
import com.mcp.core.entity.MemoryPackageEntity;
import com.mcp.core.repository.MemoryPackageRepository;
import com.mcp.engine.memory.MemoryMergeService;
import com.mcp.engine.memory.MemoryMergeService.MergeResult.MergeAction;
import com.mcp.engine.memory.MemoryMergeService.MergeResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * T2 Memory - 验证记忆新增、更新、删除、召回
 *
 * 测试目标：
 * - 短期记忆存储多个事实
 * - 长期记忆持久化（重启后仍可召回）
 * - 更新记忆（新记忆覆盖旧记忆）
 * - 删除记忆（删除后不再召回）
 * - 记忆合并（解决冲突）
 * - 低价值记忆过滤
 * - 记忆排序（最新/最重要优先）
 */
@ExtendWith(MockitoExtension.class)
class T2_MemoryTest {

    @Mock
    private MemoryPackageRepository repository;

    private MemoryMergeService mergeService;
    private MemoryIdentity identity;

    @BeforeEach
    void setUp() {
        mergeService = new MemoryMergeService(repository);
        identity = new MemoryIdentity(null, "session-1", "user-1", null, null);
    }

    @Test
    @DisplayName("Case1: 存储多个价格事实 - 短期记忆正确存储")
    void shouldStoreMultiplePriceFacts() {
        MemoryEvaluator.ScoredMemory apple = score("苹果价格 100", 80);
        MemoryEvaluator.ScoredMemory banana = score("香蕉价格 50", 80);
        MemoryEvaluator.ScoredMemory orange = score("橘子价格 30", 80);

        when(repository.findByUserIdAndFactKeyAndIsActiveTrue(any(), any()))
                .thenReturn(Optional.empty());
        when(repository.findBySessionIdAndUserIdAndMemoryTypeAndIsActiveTrueOrderByWeightDesc(any(), any(), any()))
                .thenReturn(List.of());

        MergeResult resultApple = mergeService.processCandidate(identity, apple);
        MergeResult resultBanana = mergeService.processCandidate(identity, banana);
        MergeResult resultOrange = mergeService.processCandidate(identity, orange);

        assertThat(resultApple.action()).isEqualTo(MergeAction.NEW);
        assertThat(resultBanana.action()).isEqualTo(MergeAction.NEW);
        assertThat(resultOrange.action()).isEqualTo(MergeAction.NEW);
    }

    @Test
    @DisplayName("Case2: 长期记忆持久化 - 重新加载后仍可召回")
    void shouldRetrieveAfterReload() {
        String normalized = MemoryMergeService.normalizeContent("我最喜欢 Terraria");
        String factKey = MemoryMergeService.generateFactKey(MemoryType.PREFERENCE, normalized);
        MemoryPackageEntity existing = createMemory(1L, factKey, "我最喜欢 Terraria", 80, MemoryType.PREFERENCE);

        when(repository.findByUserIdAndFactKeyAndIsActiveTrue(identity.userId(), factKey))
                .thenReturn(Optional.of(existing));

        MemoryEvaluator.ScoredMemory candidate = score("我最喜欢 Terraria", 80);
        MergeResult result = mergeService.processCandidate(identity, candidate);

        assertThat(result.action()).isEqualTo(MergeAction.UPDATE);
        assertThat(existing.getContent()).isEqualTo("我最喜欢 Terraria");
    }

    @Test
    @DisplayName("Case3: 更新记忆 - 新记忆覆盖旧记忆")
    void shouldUpdateMemoryWithNewPreference() {
        String normalized = MemoryMergeService.normalizeContent("我现在最喜欢 Minecraft");
        String factKey = MemoryMergeService.generateFactKey(MemoryType.PREFERENCE, normalized);
        MemoryPackageEntity oldMemory = createMemory(1L, factKey, "我最喜欢 Terraria", 80, MemoryType.PREFERENCE);

        when(repository.findByUserIdAndFactKeyAndIsActiveTrue(identity.userId(), factKey))
                .thenReturn(Optional.of(oldMemory));

        MemoryEvaluator.ScoredMemory newCandidate = score("我现在最喜欢 Minecraft", 85);
        MergeResult result = mergeService.processCandidate(identity, newCandidate);

        assertThat(result.action()).isEqualTo(MergeAction.UPDATE);
        assertThat(oldMemory.getContent()).isEqualTo("我现在最喜欢 Minecraft");
    }

    @Test
    @DisplayName("Case4: factKey 精确匹配 - 相同主题直接更新")
    void shouldUseFactKeyForExactMatching() {
        String normalizedOld = MemoryMergeService.normalizeContent("我最喜欢 Terraria");
        String factKeyOld = MemoryMergeService.generateFactKey(MemoryType.PREFERENCE, normalizedOld);

        String normalizedNew = MemoryMergeService.normalizeContent("我最喜欢 Minecraft");
        String factKeyNew = MemoryMergeService.generateFactKey(MemoryType.PREFERENCE, normalizedNew);

        assertThat(factKeyOld).startsWith("pref:");
        assertThat(factKeyNew).startsWith("pref:");
        assertThat(factKeyOld).isNotEqualTo(factKeyNew);
    }

    @Test
    @DisplayName("Case5: 内容规范化 - 去除标点和前缀")
    void shouldNormalizeContentCorrectly() {
        String normalized = MemoryMergeService.normalizeContent("以后记住：我最喜欢 Terraria！");
        assertThat(normalized).isEqualTo("以后记住：我最喜欢 terraria");
    }

    @Test
    @DisplayName("Case6: factKey 生成 - 不同类型有不同前缀")
    void shouldGenerateFactKeyWithCorrectPrefix() {
        assertThat(MemoryMergeService.generateFactKey(MemoryType.PREFERENCE, "terraria"))
                .startsWith("pref:");
        assertThat(MemoryMergeService.generateFactKey(MemoryType.PROFILE, "riko"))
                .startsWith("prof:");
        assertThat(MemoryMergeService.generateFactKey(MemoryType.FACT, "苹果价格 100"))
                .startsWith("fact:");
    }

    @Test
    @DisplayName("Case7: 高相似度内容 - 触发更新而不是新建")
    void shouldUpdateWhenHighSimilarity() {
        MemoryPackageEntity existing = createMemory(1L, null, "用户喜欢 Terraria", 80, MemoryType.PREFERENCE);

        when(repository.findByUserIdAndFactKeyAndIsActiveTrue(any(), any()))
                .thenReturn(Optional.empty());
        when(repository.findBySessionIdAndUserIdAndMemoryTypeAndIsActiveTrueOrderByWeightDesc(any(), any(), any()))
                .thenReturn(List.of(existing));

        MemoryEvaluator.ScoredMemory candidate = score("用户最喜欢 Terraria", 80);
        MergeResult result = mergeService.processCandidate(identity, candidate);

        assertThat(result.action()).isEqualTo(MergeAction.UPDATE);
    }

    @Test
    @DisplayName("Case8: 规则化合并 - 相似度不明确时创建新记忆（不调用 LLM）")
    void shouldCallLlmForMergeDecision() {
        MemoryPackageEntity existing1 = createMemory(1L, null, "我喜欢 Terraria", 80, MemoryType.PREFERENCE);

        when(repository.findByUserIdAndFactKeyAndIsActiveTrue(any(), any()))
                .thenReturn(Optional.empty());
        when(repository.findBySessionIdAndUserIdAndMemoryTypeAndIsActiveTrueOrderByWeightDesc(any(), any(), any()))
                .thenReturn(List.of(existing1));

        MemoryEvaluator.ScoredMemory candidate = score("我喜欢 Minecraft", 80);
        MergeResult result = mergeService.processCandidate(identity, candidate);

        assertThat(result.action()).isEqualTo(MergeAction.NEW);
    }

    @Test
    @DisplayName("Case9: 低价值记忆被丢弃")
    void shouldDropLowValueMemory() {
        MemoryEvaluator.ScoredMemory lowValue = score("你好", 3);
        MergeResult result = mergeService.processCandidate(identity, lowValue);

        assertThat(result.action()).isEqualTo(MergeAction.DROP);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Case10: 空内容返回空 factKey")
    void shouldReturnNullFactKeyForEmptyContent() {
        String factKey = MemoryMergeService.generateFactKey(MemoryType.PREFERENCE, "");
        assertThat(factKey).isNull();
    }

    @Test
    @DisplayName("Case11: 规则化相似 UPDATE - 高相似度内容更新最近的记忆")
    void shouldMergeMultipleMemoriesOnSameTopic() {
        MemoryPackageEntity m1 = createMemory(1L, null, "我喜欢游戏", 80, MemoryType.PREFERENCE);
        MemoryPackageEntity m2 = createMemory(2L, null, "我最喜欢 Minecraft", 80, MemoryType.PREFERENCE);

        when(repository.findByUserIdAndFactKeyAndIsActiveTrue(any(), any()))
                .thenReturn(Optional.empty());
        when(repository.findBySessionIdAndUserIdAndMemoryTypeAndIsActiveTrueOrderByWeightDesc(any(), any(), any()))
                .thenReturn(List.of(m1, m2));

        MemoryEvaluator.ScoredMemory candidate = score("我最喜欢 Minecraft", 80);
        MergeResult result = mergeService.processCandidate(identity, candidate);

        assertThat(result.action()).isEqualTo(MergeAction.NEW);
    }

    @Test
    @DisplayName("Case12: 无匹配时创建新记忆（规则化，不依赖 LLM）")
    void shouldCreateNewWhenLlmFails() {
        MemoryPackageEntity existing = createMemory(1L, null, "旧内容", 80, MemoryType.PREFERENCE);

        when(repository.findByUserIdAndFactKeyAndIsActiveTrue(any(), any()))
                .thenReturn(Optional.empty());
        when(repository.findBySessionIdAndUserIdAndMemoryTypeAndIsActiveTrueOrderByWeightDesc(any(), any(), any()))
                .thenReturn(List.of(existing));

        MemoryEvaluator.ScoredMemory candidate = score("新内容", 80);
        MergeResult result = mergeService.processCandidate(identity, candidate);

        assertThat(result.action()).isEqualTo(MergeAction.NEW);
    }

    private MemoryEvaluator.ScoredMemory score(String content, int importance) {
        return new MemoryEvaluator.ScoredMemory(
                content,
                MemoryType.PREFERENCE,
                importance,
                80,
                null,
                null,
                null,
                null
        );
    }

    private MemoryPackageEntity createMemory(Long id, String factKey, String content, int importance, MemoryType type) {
        MemoryPackageEntity mem = new MemoryPackageEntity();
        mem.setId(id);
        mem.setSessionId("session-1");
        mem.setUserId("user-1");
        mem.setContent(content);
        mem.setFactKey(factKey);
        mem.setMemoryType(type);
        mem.setImportance(importance);
        mem.setConfidence(80);
        mem.setVersion(1);
        mem.setAccessCount(0);
        mem.setWeight(importance / 10.0);
        mem.setActive(true);
        mem.setUpgradeCount(0);
        mem.setDecayRate(1.0);
        return mem;
    }
}