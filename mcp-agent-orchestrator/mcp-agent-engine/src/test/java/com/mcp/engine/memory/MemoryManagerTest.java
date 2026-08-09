package com.mcp.engine.memory;

import com.mcp.common.memory.MemoryContext;
import com.mcp.core.domain.memory.MemoryType;
import com.mcp.core.entity.MemoryPackageEntity;
import com.mcp.core.repository.MemoryPackageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * P6 验证 — MemoryManager / MemorySearchService 测试。
 * 验证：
 * 1. MemoryContext 模型与 Prompt 片段生成
 * 2. MemorySearchService 检索功能
 * 3. MemoryManager 门面功能
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("P6 — Memory 重构")
class MemoryManagerTest {

    @Mock
    private MemoryPackageRepository repository;

    private MemorySearchService searchService;
    private MemoryManager memoryManager;

    @BeforeEach
    void setUp() {
        searchService = new MemorySearchService(repository);
        memoryManager = new MemoryManager(null, searchService, repository);
    }

    // ==================== MemoryContext 模型 ====================

    @Nested
    @DisplayName("MemoryContext 模型")
    class MemoryContextModel {

        @Test
        @DisplayName("空上下文应返回空字符串")
        void shouldReturnEmptyForEmptyContext() {
            MemoryContext ctx = MemoryContext.builder().totalMemories(0).build();

            assertThat(ctx.isEmpty()).isTrue();
            assertThat(ctx.toPromptFragment()).isEmpty();
        }

        @Test
        @DisplayName("应生成正确的 Prompt 片段")
        void shouldGeneratePromptFragment() {
            MemoryContext ctx = MemoryContext.builder()
                    .hotMemories(List.of(
                            MemoryContext.MemoryEntry.of(1L, "用户喜欢 Python", "PREFERENCE", 90, "HOT"),
                            MemoryContext.MemoryEntry.of(2L, "用户是软件工程师", "IDENTITY", 85, "HOT")
                    ))
                    .relevantMemories(List.of(
                            MemoryContext.MemoryEntry.of(3L, "用户在做 Java 项目", "PROJECT", 60, "WARM")
                    ))
                    .recentMemories(List.of(
                            MemoryContext.MemoryEntry.of(4L, "用户今天吃了拉面", "TEMPORARY", 30, "COLD")
                    ))
                    .totalMemories(10)
                    .build();

            assertThat(ctx.isEmpty()).isFalse();
            String fragment = ctx.toPromptFragment();
            assertThat(fragment).contains("用户记忆");
            assertThat(fragment).contains("用户喜欢 Python");
            assertThat(fragment).contains("用户是软件工程师");
            assertThat(fragment).contains("用户在做 Java 项目");
            assertThat(fragment).contains("用户今天吃了拉面");
        }

        @Test
        @DisplayName("MemoryEntry 应正确构建")
        void shouldBuildMemoryEntry() {
            MemoryContext.MemoryEntry entry = MemoryContext.MemoryEntry.of(
                    1L, "测试内容", "FACT", 50, "WARM");

            assertThat(entry.getId()).isEqualTo(1L);
            assertThat(entry.getContent()).isEqualTo("测试内容");
            assertThat(entry.getType()).isEqualTo("FACT");
            assertThat(entry.getImportance()).isEqualTo(50);
            assertThat(entry.getTier()).isEqualTo("WARM");
        }
    }

    // ==================== MemorySearchService ====================

    @Nested
    @DisplayName("MemorySearchService")
    class MemorySearchServiceTests {

        @Test
        @DisplayName("无记忆时应返回空上下文")
        void shouldReturnEmptyContextWhenNoMemories() {
            when(repository.findByUserIdOrderByWeightDesc("user1")).thenReturn(List.of());

            MemoryContext ctx = searchService.buildMemoryContext("s1", "user1", "test");

            assertThat(ctx.isEmpty()).isTrue();
            assertThat(ctx.getTotalMemories()).isEqualTo(0);
        }

        @Test
        @DisplayName("应正确分类热记忆")
        void shouldClassifyHotMemories() {
            MemoryPackageEntity hot = createMemory(1L, "用户喜欢 Python", MemoryType.PREFERENCE, 90, 90.0);
            MemoryPackageEntity cold = createMemory(2L, "今天吃了面", MemoryType.TEMPORARY, 20, 10.0);

            when(repository.findByUserIdOrderByWeightDesc("user1"))
                    .thenReturn(List.of(hot, cold));

            MemoryContext ctx = searchService.buildMemoryContext("s1", "user1", null);

            assertThat(ctx.getHotMemories()).hasSize(1);
            assertThat(ctx.getHotMemories().get(0).getContent()).isEqualTo("用户喜欢 Python");
            assertThat(ctx.getHotMemories().get(0).getTier()).isEqualTo("HOT");
        }

        @Test
        @DisplayName("应通过关键词搜索记忆")
        void shouldSearchByKeyword() {
            MemoryPackageEntity m1 = createMemory(1L, "用户喜欢 Python", MemoryType.PREFERENCE, 80, 80.0);
            MemoryPackageEntity m2 = createMemory(2L, "用户喜欢 Java", MemoryType.PREFERENCE, 70, 70.0);
            MemoryPackageEntity m3 = createMemory(3L, "用户在写代码", MemoryType.FACT, 50, 50.0);

            when(repository.findByUserIdOrderByWeightDesc("user1"))
                    .thenReturn(List.of(m1, m2, m3));

            List<MemoryContext.MemoryEntry> results = searchService.search("user1", "Python", 10);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getContent()).isEqualTo("用户喜欢 Python");
        }

        @Test
        @DisplayName("应按类型搜索记忆")
        void shouldSearchByType() {
            MemoryPackageEntity m1 = createMemory(1L, "用户喜欢 Python", MemoryType.PREFERENCE, 80, 80.0);
            MemoryPackageEntity m2 = createMemory(2L, "用户是工程师", MemoryType.IDENTITY, 70, 70.0);

            when(repository.findByUserIdOrderByWeightDesc("user1"))
                    .thenReturn(List.of(m1, m2));

            List<MemoryContext.MemoryEntry> results = searchService.searchByType("user1", "PREFERENCE", 10);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getContent()).isEqualTo("用户喜欢 Python");
        }

        @Test
        @DisplayName("应正确统计类型数量")
        void shouldCountByType() {
            MemoryPackageEntity m1 = createMemory(1L, "m1", MemoryType.PREFERENCE, 80, 80.0);
            MemoryPackageEntity m2 = createMemory(2L, "m2", MemoryType.PREFERENCE, 70, 70.0);
            MemoryPackageEntity m3 = createMemory(3L, "m3", MemoryType.FACT, 50, 50.0);

            when(repository.findByUserIdOrderByWeightDesc("user1"))
                    .thenReturn(List.of(m1, m2, m3));

            long count = searchService.countByType("user1", "PREFERENCE");

            assertThat(count).isEqualTo(2);
        }
    }

    // ==================== MemoryManager 门面 ====================

    @Nested
    @DisplayName("MemoryManager 门面")
    class MemoryManagerFacade {

        @Test
        @DisplayName("应正确召回记忆上下文")
        void shouldRecallMemoryContext() {
            MemoryPackageEntity m1 = createMemory(1L, "用户喜欢 Python", MemoryType.PREFERENCE, 90, 90.0);

            when(repository.findByUserIdOrderByWeightDesc("user1"))
                    .thenReturn(List.of(m1));

            MemoryContext ctx = memoryManager.recall("s1", "user1", "Python");

            assertThat(ctx.getHotMemories()).hasSize(1);
            assertThat(ctx.getTotalMemories()).isEqualTo(1);
        }

        @Test
        @DisplayName("应正确搜索记忆")
        void shouldSearchMemories() {
            MemoryPackageEntity m1 = createMemory(1L, "用户喜欢 Python", MemoryType.PREFERENCE, 80, 80.0);

            when(repository.findByUserIdOrderByWeightDesc("user1"))
                    .thenReturn(List.of(m1));

            List<MemoryContext.MemoryEntry> results = memoryManager.search("user1", "Python", 10);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getContent()).isEqualTo("用户喜欢 Python");
        }

        @Test
        @DisplayName("应正确统计用户记忆数")
        void shouldCountMemories() {
            MemoryPackageEntity m1 = createMemory(1L, "m1", MemoryType.FACT, 50, 50.0);
            MemoryPackageEntity m2 = createMemory(2L, "m2", MemoryType.FACT, 50, 50.0);
            MemoryPackageEntity m3 = createMemory(3L, "m3", MemoryType.FACT, 30, 10.0, false);

            when(repository.findByUserIdOrderByWeightDesc("user1"))
                    .thenReturn(List.of(m1, m2, m3));

            long count = memoryManager.countByUser("user1");

            assertThat(count).isEqualTo(2);
        }
    }

    private MemoryPackageEntity createMemory(Long id, String content, MemoryType type,
                                              int importance, double weight) {
        return createMemory(id, content, type, importance, weight, true);
    }

    private MemoryPackageEntity createMemory(Long id, String content, MemoryType type,
                                              int importance, double weight, boolean active) {
        MemoryPackageEntity entity = new MemoryPackageEntity();
        entity.setId(id);
        entity.setContent(content);
        entity.setMemoryType(type);
        entity.setImportance(importance);
        entity.setWeight(weight);
        entity.setActive(active);
        entity.setAccessCount(10);
        entity.setCreatedAt(LocalDateTime.now().minusDays(1));
        entity.setLastAccessedAt(LocalDateTime.now());
        return entity;
    }
}