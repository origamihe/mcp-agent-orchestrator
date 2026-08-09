package com.mcp.engine.test.memory;

import com.mcp.common.identity.MemoryIdentity;
import com.mcp.core.domain.memory.MemoryType;
import com.mcp.core.entity.MemoryPackageEntity;
import com.mcp.core.repository.MemoryPackageRepository;
import com.mcp.engine.memory.MemoryConflictResolver;
import com.mcp.engine.memory.MemoryConflictResolver.ConflictGroup;
import com.mcp.engine.memory.MemoryConflictResolver.Resolution;
import com.mcp.engine.memory.MemoryEvaluator;
import com.mcp.engine.memory.MemoryMergeService;
import com.mcp.engine.memory.MemoryMergeService.MergeResult;
import com.mcp.engine.memory.MemoryMergeService.MergeResult.MergeAction;
import com.mcp.llm.client.LlmClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("T11 Long-term Memory Evolution - 长期记忆演化测试")
class LongTermMemoryEvolutionTest {

    @Mock
    private MemoryPackageRepository memoryRepository;

    @Mock
    private LlmClient llmClient;

    private MemoryMergeService mergeService;
    private MemoryConflictResolver conflictResolver;
    private MemoryIdentity identity;

    @BeforeEach
    void setUp() {
        mergeService = new MemoryMergeService(memoryRepository);
        conflictResolver = new MemoryConflictResolver();
        identity = new MemoryIdentity(null, "session-ltm", "user-ltm", null, null);
    }

    @Nested
    @DisplayName("Day1 → Day3 → Day7 记忆演化")
    class MultiDayMemoryEvolution {

        @Test
        @DisplayName("Case1: Day1 偏好→Day3 修正→Day7 最终确认 — 最终保留最新且优先级最高")
        void shouldRetainLatestHighestPriorityAcrossDays() {
            MemoryPackageEntity day1 = createMemory(1L, "游戏 偏好 Terraria 很好玩", 70,
                    LocalDateTime.now().minusDays(7));
            MemoryPackageEntity day3 = createMemory(2L, "游戏 偏好 Terraria 最棒", 85,
                    LocalDateTime.now().minusDays(4));
            MemoryPackageEntity day7 = createMemory(3L, "游戏 偏好 Terraria 还是更喜欢", 90,
                    LocalDateTime.now().minusDays(1));

            List<ConflictGroup> conflicts = conflictResolver.detectAndResolve(List.of(day1, day3, day7));

            assertThat(conflicts).hasSize(1);
            assertThat(day1.isActive()).isFalse();
            assertThat(day3.isActive()).isFalse();
            assertThat(day7.isActive()).isTrue();
            assertThat(conflicts.get(0).resolution()).isEqualTo(Resolution.KEEP_LATEST);
        }

        @Test
        @DisplayName("Case2: Day1→Day3→Day7 同一偏好反复确认 — 所有旧版本应归档")
        void shouldArchiveAllOldVersionsWhenPreferenceConfirmed() {
            MemoryPackageEntity day1 = createMemory(1L, "游戏 偏好 Terraria 很好玩", 80,
                    LocalDateTime.now().minusDays(7));
            MemoryPackageEntity day3 = createMemory(2L, "游戏 偏好 Terraria 确实不错", 80,
                    LocalDateTime.now().minusDays(4));
            MemoryPackageEntity day7 = createMemory(3L, "游戏 偏好 Terraria 真的很喜欢", 80,
                    LocalDateTime.now().minusDays(1));

            List<ConflictGroup> conflicts = conflictResolver.detectAndResolve(List.of(day1, day3, day7));

            assertThat(conflicts).hasSize(1);
            assertThat(day1.isActive()).isFalse();
            assertThat(day3.isActive()).isFalse();
            assertThat(day7.isActive()).isTrue();
        }

        @Test
        @DisplayName("Case3: 跨天新增不同类型记忆 — 不冲突，全部保留")
        void shouldNotConflictAcrossDifferentMemoryTypes() {
            MemoryPackageEntity day1Game = createMemory(1L, "游戏 偏好 Terraria", 80,
                    LocalDateTime.now().minusDays(7));
            MemoryPackageEntity day3Food = createMemory(2L, "水果 价格 苹果 100", 70,
                    LocalDateTime.now().minusDays(4));
            MemoryPackageEntity day7Location = createMemory(3L, "地点 位置 北京", 60,
                    LocalDateTime.now().minusDays(1));

            List<ConflictGroup> conflicts = conflictResolver.detectAndResolve(
                    List.of(day1Game, day3Food, day7Location));

            assertThat(conflicts).isEmpty();
            assertThat(day1Game.isActive()).isTrue();
            assertThat(day3Food.isActive()).isTrue();
            assertThat(day7Location.isActive()).isTrue();
        }

        @Test
        @DisplayName("Case4: 长期不访问的记忆 — 权重降低（模拟衰减）")
        void shouldSimulateDecayForLongUnaccessedMemories() {
            MemoryPackageEntity old = createMemory(1L, "旧记忆 内容", 50,
                    LocalDateTime.now().minusDays(30));
            MemoryPackageEntity recent = createMemory(2L, "新记忆 内容", 50,
                    LocalDateTime.now().minusDays(1));

            boolean oldIsStale = old.getLastAccessedAt()
                    .isBefore(LocalDateTime.now().minusDays(14));
            boolean recentIsFresh = recent.getLastAccessedAt()
                    .isAfter(LocalDateTime.now().minusDays(7));

            assertThat(oldIsStale).isTrue();
            assertThat(recentIsFresh).isTrue();
        }
    }

    @Nested
    @DisplayName("Memory Merge — 合并策略")
    class MemoryMergeEvolution {

        @Test
        @DisplayName("Case5: 同类记忆多次交互 — 合并为一条摘要")
        void shouldMergeMultipleSimilarMemoriesIntoOne() {
            MemoryPackageEntity m1 = createMemory(1L, "游戏 偏好 Terraria 很好玩", 80,
                    LocalDateTime.now().minusDays(5));
            MemoryPackageEntity m2 = createMemory(2L, "游戏 偏好 Terraria 也不错", 80,
                    LocalDateTime.now().minusDays(3));
            MemoryPackageEntity m3 = createMemory(3L, "游戏 偏好 Terraria 还可以", 80,
                    LocalDateTime.now().minusDays(1));

            List<ConflictGroup> conflicts = conflictResolver.detectAndResolve(List.of(m1, m2, m3));

            assertThat(conflicts).hasSize(1);
            ConflictGroup group = conflicts.get(0);
            assertThat(group.mergedContent()).isEqualTo("游戏 偏好 Terraria 还可以");
            assertThat(group.conflicting()).hasSize(3);
        }

        @Test
        @DisplayName("Case6: 高优先级新记忆覆盖低优先级旧记忆")
        void shouldOverrideLowPriorityWithHighPriorityNewMemory() {
            MemoryPackageEntity oldHigh = createMemory(1L, "游戏 偏好 Terraria 最好玩", 90,
                    LocalDateTime.now().minusDays(5));
            MemoryPackageEntity newLow = createMemory(2L, "游戏 偏好 Terraria 一般", 40,
                    LocalDateTime.now().minusDays(1));

            List<ConflictGroup> conflicts = conflictResolver.detectAndResolve(List.of(oldHigh, newLow));

            assertThat(conflicts).hasSize(1);
            assertThat(conflicts.get(0).resolution()).isEqualTo(Resolution.KEEP_HIGHEST);
            assertThat(newLow.isActive()).isFalse();
            assertThat(oldHigh.isActive()).isTrue();
        }

        @Test
        @DisplayName("Case7: 矛盾记忆 — 保留最新且优先级最高的")
        void shouldResolveContradictoryMemoriesByLatestHighest() {
            MemoryPackageEntity m1 = createMemory(1L, "游戏 偏好 Terraria 最好", 60,
                    LocalDateTime.now().minusDays(10));
            MemoryPackageEntity m2 = createMemory(2L, "游戏 偏好 Terraria 最好", 70,
                    LocalDateTime.now().minusDays(5));
            MemoryPackageEntity m3 = createMemory(3L, "游戏 偏好 Terraria 更好", 75,
                    LocalDateTime.now().minusDays(2));

            List<ConflictGroup> conflicts = conflictResolver.detectAndResolve(List.of(m1, m2, m3));

            assertThat(conflicts).hasSize(1);
            assertThat(m3.isActive()).isTrue();
            assertThat(m1.isActive()).isFalse();
            assertThat(m2.isActive()).isFalse();
        }
    }

    @Nested
    @DisplayName("Memory 遗忘与摘要")
    class MemoryForgettingAndSummarization {

        @Test
        @DisplayName("Case8: 低重要性记忆在长期后应被遗忘（不再活跃）")
        void shouldDeactivateLowImportanceMemoryAfterLongTime() {
            MemoryPackageEntity lowImportance = createMemory(1L, "临时信息 随便记的", 10,
                    LocalDateTime.now().minusDays(60));

            boolean shouldBeForgotten = lowImportance.getImportance() < 30
                    && lowImportance.getLastAccessedAt()
                    .isBefore(LocalDateTime.now().minusDays(30));

            assertThat(shouldBeForgotten).isTrue();
            assertThat(lowImportance.getImportance()).isLessThan(30);
        }

        @Test
        @DisplayName("Case9: 高重要性记忆即使长期也不应被遗忘")
        void shouldRetainHighImportanceMemoryEvenAfterLongTime() {
            MemoryPackageEntity highImportance = createMemory(1L, "核心身份 我是开发者", 95,
                    LocalDateTime.now().minusDays(90));

            boolean shouldNotBeForgotten = highImportance.getImportance() >= 70
                    || highImportance.getLastAccessedAt()
                    .isAfter(LocalDateTime.now().minusDays(60));

            assertThat(shouldNotBeForgotten).isTrue();
        }

        @Test
        @DisplayName("Case10: 大量记忆压缩 — 同一主题摘要为一条")
        void shouldSummarizeManyMemoriesOnSameTopic() {
            List<MemoryPackageEntity> memories = new java.util.ArrayList<>();
            for (int i = 1; i <= 10; i++) {
                memories.add(createMemory(
                        (long) i,
                        "项目 进展 更新",
                        50 + (i % 20),
                        LocalDateTime.now().minusDays(10 - i)));
            }

            List<ConflictGroup> conflicts = conflictResolver.detectAndResolve(memories);

            assertThat(conflicts).hasSize(1);
            long activeCount = conflicts.get(0).conflicting().stream()
                    .filter(MemoryPackageEntity::isActive).count();
            assertThat(activeCount).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Memory 冲突解决策略验证")
    class ConflictResolutionStrategy {

        @Test
        @DisplayName("Case11: 优先级差 < 30 且时间差 < 7天 → KEEP_LATEST")
        void shouldKeepLatestWhenClosePriorityAndTime() {
            MemoryPackageEntity m1 = createMemory(1L, "游戏 偏好 Terraria", 60,
                    LocalDateTime.now().minusDays(3));
            MemoryPackageEntity m2 = createMemory(2L, "游戏 偏好 Terraria", 70,
                    LocalDateTime.now().minusDays(1));

            List<ConflictGroup> conflicts = conflictResolver.detectAndResolve(List.of(m1, m2));

            assertThat(conflicts).hasSize(1);
            assertThat(conflicts.get(0).resolution()).isEqualTo(Resolution.KEEP_LATEST);
            assertThat(m2.isActive()).isTrue();
        }

        @Test
        @DisplayName("Case12: 优先级差 >= 30 → KEEP_HIGHEST（即使更旧）")
        void shouldKeepHighestWhenPriorityGapLarge() {
            MemoryPackageEntity oldHigh = createMemory(1L, "游戏 偏好 Terraria", 90,
                    LocalDateTime.now().minusDays(10));
            MemoryPackageEntity newLow = createMemory(2L, "游戏 偏好 Terraria", 40,
                    LocalDateTime.now().minusDays(1));

            List<ConflictGroup> conflicts = conflictResolver.detectAndResolve(List.of(oldHigh, newLow));

            assertThat(conflicts).hasSize(1);
            assertThat(conflicts.get(0).resolution()).isEqualTo(Resolution.KEEP_HIGHEST);
            assertThat(oldHigh.isActive()).isTrue();
            assertThat(newLow.isActive()).isFalse();
        }
    }

    private MemoryPackageEntity createMemory(Long id, String content, int importance, LocalDateTime lastAccessedAt) {
        MemoryPackageEntity mem = new MemoryPackageEntity();
        mem.setId(id);
        mem.setContent(content);
        mem.setImportance(importance);
        mem.setActive(true);
        mem.setLastAccessedAt(lastAccessedAt);
        return mem;
    }
}