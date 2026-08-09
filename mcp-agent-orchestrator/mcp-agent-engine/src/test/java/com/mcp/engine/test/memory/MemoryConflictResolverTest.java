package com.mcp.engine.test.memory;

import com.mcp.core.entity.MemoryPackageEntity;
import com.mcp.engine.memory.MemoryConflictResolver;
import com.mcp.engine.memory.MemoryConflictResolver.ConflictGroup;
import com.mcp.engine.memory.MemoryConflictResolver.Resolution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryConflictResolverTest {

    private MemoryConflictResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new MemoryConflictResolver();
    }

    @Test
    @DisplayName("Case1: 无冲突 - 单个记忆不分组")
    void shouldNotDetectConflictWhenSingleMemory() {
        MemoryPackageEntity mem = createMemory(1L, "我喜欢 Terraria", 80, now());
        List<ConflictGroup> conflicts = resolver.detectAndResolve(List.of(mem));
        assertThat(conflicts).isEmpty();
    }

    @Test
    @DisplayName("Case2: 同一主题多个记忆 - 检测到冲突")
    void shouldDetectConflictWhenMultipleMemoriesOnSameTopic() {
        MemoryPackageEntity m1 = createMemory(1L, "游戏 偏好 Terraria 很好玩", 80, now().minusDays(3));
        MemoryPackageEntity m2 = createMemory(2L, "游戏 偏好 Terraria 也不错", 80, now().minusDays(2));
        MemoryPackageEntity m3 = createMemory(3L, "游戏 偏好 Terraria 还可以", 80, now());

        List<ConflictGroup> conflicts = resolver.detectAndResolve(List.of(m1, m2, m3));

        assertThat(conflicts).hasSize(1);
        ConflictGroup group = conflicts.get(0);
        assertThat(group.conflicting()).hasSize(3);
    }

    @Test
    @DisplayName("Case3: 多主题冲突 - 分多个冲突组")
    void shouldGroupByDifferentTopics() {
        MemoryPackageEntity m1 = createMemory(1L, "游戏 偏好 Terraria 很好玩", 80, now());
        MemoryPackageEntity m2 = createMemory(2L, "游戏 偏好 Terraria 也不错", 80, now());
        MemoryPackageEntity m3 = createMemory(3L, "水果 价格 苹果 100", 70, now());
        MemoryPackageEntity m4 = createMemory(4L, "水果 价格 苹果 150", 70, now());

        List<ConflictGroup> conflicts = resolver.detectAndResolve(List.of(m1, m2, m3, m4));

        assertThat(conflicts).hasSize(2);
    }

    @Test
    @DisplayName("Case4: 保留最新 - 时间最新的保留活跃，旧的标记为非活跃")
    void shouldKeepLatestWhenSameImportance() {
        MemoryPackageEntity m1 = createMemory(1L, "游戏 偏好 Terraria 很好玩", 80, now().minusDays(3));
        MemoryPackageEntity m2 = createMemory(2L, "游戏 偏好 Terraria 也不错", 80, now().minusDays(2));
        MemoryPackageEntity m3 = createMemory(3L, "游戏 偏好 Terraria 还可以", 80, now());

        List<ConflictGroup> conflicts = resolver.detectAndResolve(List.of(m1, m2, m3));

        ConflictGroup group = conflicts.get(0);
        assertThat(group.resolution()).isEqualTo(Resolution.KEEP_LATEST);
        assertThat(m1.isActive()).isFalse();
        assertThat(m2.isActive()).isFalse();
        assertThat(m3.isActive()).isTrue();
        assertThat(group.mergedContent()).isEqualTo("游戏 偏好 Terraria 还可以");
    }

    @Test
    @DisplayName("Case5: 保留最高优先级 - 优先级差大于等于30时保留优先级最高")
    void shouldKeepHighestImportanceWhenDifferenceLargeEnough() {
        MemoryPackageEntity mLatest = createMemory(1L, "游戏 偏好 Terraria 一般", 50, now());
        MemoryPackageEntity mHigh = createMemory(2L, "游戏 偏好 Terraria 最好玩", 90, now().minusDays(1));

        List<ConflictGroup> conflicts = resolver.detectAndResolve(List.of(mLatest, mHigh));

        ConflictGroup group = conflicts.get(0);
        assertThat(group.resolution()).isEqualTo(Resolution.KEEP_HIGHEST);
        assertThat(mLatest.isActive()).isFalse();
        assertThat(mHigh.isActive()).isTrue();
    }

    @Test
    @DisplayName("Case6: 优先级差不足 - 合并内容")
    void shouldMergeWhenImportanceDifferenceSmall() {
        MemoryPackageEntity m1 = createMemory(1L, "游戏 偏好 Terraria 很好玩", 70, now());
        MemoryPackageEntity m2 = createMemory(2L, "游戏 偏好 Terraria 也不错", 80, now().minusDays(2));

        List<ConflictGroup> conflicts = resolver.detectAndResolve(List.of(m1, m2));

        ConflictGroup group = conflicts.get(0);
        assertThat(group.resolution()).isEqualTo(Resolution.MERGE);
        assertThat(m1.isActive()).isTrue();
        assertThat(m2.isActive()).isFalse();
        assertThat(group.mergedContent()).contains("Terraria");
    }

    @Test
    @DisplayName("Case7: 空列表返回空结果")
    void shouldReturnEmptyForEmptyList() {
        List<ConflictGroup> conflicts = resolver.detectAndResolve(List.of());
        assertThat(conflicts).isEmpty();
    }

    @Test
    @DisplayName("Case8: 不同主题不冲突")
    void shouldNotConflictWhenDifferentTopics() {
        MemoryPackageEntity m1 = createMemory(1L, "游戏 偏好 Terraria 很好玩", 80, now());
        MemoryPackageEntity m2 = createMemory(2L, "水果 价格 苹果 100", 70, now());

        List<ConflictGroup> conflicts = resolver.detectAndResolve(List.of(m1, m2));

        assertThat(conflicts).isEmpty();
        assertThat(m1.isActive()).isTrue();
        assertThat(m2.isActive()).isTrue();
    }

    private MemoryPackageEntity createMemory(Long id, String content, int importance, LocalDateTime time) {
        MemoryPackageEntity mem = new MemoryPackageEntity();
        mem.setId(id);
        mem.setContent(content);
        mem.setImportance(importance);
        mem.setActive(true);
        mem.setLastAccessedAt(time);
        return mem;
    }

    private LocalDateTime now() {
        return LocalDateTime.now();
    }
}