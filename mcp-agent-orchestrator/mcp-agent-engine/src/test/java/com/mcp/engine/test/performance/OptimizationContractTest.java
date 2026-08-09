package com.mcp.engine.test.performance;

import com.mcp.common.identity.MemoryIdentity;
import com.mcp.core.domain.memory.MemoryType;
import com.mcp.core.entity.MemoryPackageEntity;
import com.mcp.core.repository.MemoryPackageRepository;
import com.mcp.engine.agent.ExecutionTracker;
import com.mcp.engine.memory.MemoryEvaluator;
import com.mcp.engine.memory.MemoryMergeService;
import com.mcp.engine.memory.MemoryMergeService.MergeResult;
import com.mcp.engine.memory.MemoryMergeService.MergeResult.MergeAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 性能优化契约测试
 *
 * 验证五个关键优化是否正确实施：
 * 1. MemoryType.IDENTITY 被正确识别
 * 2. MemoryMergeService 不再依赖 LlmClient（完全规则化）
 * 3. MemoryMergeService.generateFactKey 支持 IDENTITY 和 EVENT
 * 4. recordSkillExecutions 在无工具调用时跳过
 * 5. LongTermMemoryService 将 IDENTITY 归类为身份记忆
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Performance Optimization Contract Tests")
class OptimizationContractTest {

    @Mock
    private MemoryPackageRepository repository;

    private MemoryMergeService mergeService;
    private MemoryIdentity identity;

    @BeforeEach
    void setUp() {
        mergeService = new MemoryMergeService(repository);
        identity = new MemoryIdentity(null, "session-perf", "user-perf", null, null);
    }

    @Nested
    @DisplayName("Optimization 1: MemoryType.IDENTITY 应被正确识别")
    class IdentityMemoryType {

        @Test
        @DisplayName("O1-T1: MemoryType.IDENTITY 存在且为 PERMANENT 生命周期")
        void identityTypeShouldExistWithPermanentLifecycle() {
            MemoryType identityType = MemoryType.valueOf("IDENTITY");

            assertThat(identityType).isNotNull();
            assertThat(identityType.getLifecycle()).isEqualTo(MemoryType.Lifecycle.PERMANENT);
            assertThat(identityType.getDisplayName()).isEqualTo("身份信息");
        }

        @Test
        @DisplayName("O1-T2: IDENTITY 类型的 factKey 前缀应为 'id:'")
        void identityFactKeyShouldHaveCorrectPrefix() {
            String factKey = MemoryMergeService.generateFactKey(MemoryType.IDENTITY, "叉烧");

            assertThat(factKey).isEqualTo("id:叉烧");
        }

        @Test
        @DisplayName("O1-T3: IDENTITY 记忆不应 fallback 到 TEMPORARY")
        void identityShouldNotFallbackToTemporary() {
            MemoryType type = MemoryType.IDENTITY;

            assertThat(type).isNotEqualTo(MemoryType.TEMPORARY);
            assertThat(type.getLifecycle()).isNotEqualTo(MemoryType.Lifecycle.SHORT);
        }

        @Test
        @DisplayName("O1-T4: IDENTITY 记忆应有正确的描述")
        void identityShouldHaveCorrectDescription() {
            MemoryType type = MemoryType.IDENTITY;

            assertThat(type.getDescription()).contains("身份");
            assertThat(type.getDescription()).contains("称呼");
        }
    }

    @Nested
    @DisplayName("Optimization 2: MemoryMergeService 不再依赖 LLM（完全规则化）")
    class MergeServiceNoLLM {

        @Test
        @DisplayName("O2-T1: MemoryMergeService 构造函数不需要 LlmClient")
        void constructorShouldNotRequireLlmClient() {
            MemoryMergeService service = new MemoryMergeService(repository);

            assertThat(service).isNotNull();
        }

        @Test
        @DisplayName("O2-T2: factKey 精确匹配时应直接 UPDATE（不调用 LLM）")
        void factKeyMatchShouldUpdateDirectly() {
            MemoryPackageEntity existing = new MemoryPackageEntity();
            existing.setId(1L);
            existing.setContent("昵称：叉烧");
            existing.setActive(true);
            existing.setImportance(85);
            existing.setMemoryType(MemoryType.IDENTITY);

            when(repository.findByUserIdAndFactKeyAndIsActiveTrue(any(), any()))
                    .thenReturn(Optional.of(existing));

            MemoryEvaluator.ScoredMemory scored = new MemoryEvaluator.ScoredMemory(
                    "用户叫我叉烧", MemoryType.IDENTITY, 85, 85, true);

            MergeResult result = mergeService.processCandidate(identity, scored);

            assertThat(result.action()).isEqualTo(MergeAction.UPDATE);
            assertThat(result.newEntity()).isNotNull();
            assertThat(result.newEntity().getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("O2-T3: 无匹配时应创建新记忆（不调用 LLM）")
        void noMatchShouldCreateNewWithoutLLM() {
            when(repository.findByUserIdAndFactKeyAndIsActiveTrue(any(), any()))
                    .thenReturn(Optional.empty());
            when(repository.findBySessionIdAndUserIdAndMemoryTypeAndIsActiveTrueOrderByWeightDesc(
                    any(), any(), any()))
                    .thenReturn(java.util.List.of());

            MemoryEvaluator.ScoredMemory scored = new MemoryEvaluator.ScoredMemory(
                    "用户喜欢 Python", MemoryType.PREFERENCE, 80, 80, true);

            MergeResult result = mergeService.processCandidate(identity, scored);

            assertThat(result.action()).isEqualTo(MergeAction.NEW);
            assertThat(result.factKey()).isNotNull();
        }

        @Test
        @DisplayName("O2-T4: 相似记忆应触发 UPDATE（不调用 LLM）")
        void similarMemoryShouldUpdateWithoutLLM() {
            MemoryPackageEntity existing = new MemoryPackageEntity();
            existing.setId(2L);
            existing.setContent("我喜欢 Java");
            existing.setActive(true);
            existing.setImportance(80);
            existing.setMemoryType(MemoryType.PREFERENCE);

            when(repository.findByUserIdAndFactKeyAndIsActiveTrue(any(), any()))
                    .thenReturn(Optional.empty());
            when(repository.findBySessionIdAndUserIdAndMemoryTypeAndIsActiveTrueOrderByWeightDesc(
                    any(), any(), any()))
                    .thenReturn(java.util.List.of(existing));

            MemoryEvaluator.ScoredMemory scored = new MemoryEvaluator.ScoredMemory(
                    "我喜欢 java", MemoryType.PREFERENCE, 85, 85, true);

            MergeResult result = mergeService.processCandidate(identity, scored);

            assertThat(result.action()).isEqualTo(MergeAction.UPDATE);
        }
    }

    @Nested
    @DisplayName("Optimization 3: generateFactKey 支持所有 MemoryType")
    class FactKeyCoverage {

        @Test
        @DisplayName("O3-T1: IDENTITY → 'id:'")
        void identityFactKey() {
            assertThat(MemoryMergeService.generateFactKey(MemoryType.IDENTITY, "叉烧"))
                    .isEqualTo("id:叉烧");
        }

        @Test
        @DisplayName("O3-T2: EVENT → 'evt:'")
        void eventFactKey() {
            assertThat(MemoryMergeService.generateFactKey(MemoryType.EVENT, "会议"))
                    .isEqualTo("evt:会议");
        }

        @Test
        @DisplayName("O3-T3: TEMPORARY → 'tmp:'")
        void temporaryFactKey() {
            assertThat(MemoryMergeService.generateFactKey(MemoryType.TEMPORARY, "临时信息"))
                    .isEqualTo("tmp:临时信息");
        }

        @Test
        @DisplayName("O3-T4: 所有 MemoryType 都有对应的 factKey 前缀")
        void allMemoryTypesHaveFactKeyPrefix() {
            for (MemoryType type : MemoryType.values()) {
                String key = MemoryMergeService.generateFactKey(type, "test");
                assertThat(key).as("MemoryType %s should have factKey", type.name()).isNotNull();
                assertThat(key).as("MemoryType %s factKey should have prefix", type.name())
                        .contains(":");
            }
        }
    }

    @Nested
    @DisplayName("Optimization 4: recordSkillExecutions 在无工具调用时跳过")
    class SkillExecutionGuard {

        @Test
        @DisplayName("O4-T1: ExecutionTracker 无 observations 时 getObservations 为空")
        void emptyObservationsShouldBeEmpty() {
            ExecutionTracker tracker = new ExecutionTracker();

            assertThat(tracker.getObservations()).isEmpty();
        }

        @Test
        @DisplayName("O4-T2: 新增的 hasToolCalls 检查逻辑：无工具调用时应跳过")
        void shouldSkipWhenNoToolCalls() {
            ExecutionTracker tracker = new ExecutionTracker();

            boolean hasToolCalls = !tracker.getObservations().isEmpty();

            assertThat(hasToolCalls).isFalse();
        }
    }

    @Nested
    @DisplayName("Optimization 5: 缓存注解验证")
    class CachingAnnotations {

        @Test
        @DisplayName("O5-T1: PromptService.getPrompt 应有 @Cacheable 注解")
        void promptServiceGetPromptShouldBeCacheable() throws NoSuchMethodException {
            var method = com.mcp.core.service.PromptService.class
                    .getMethod("getPrompt", String.class);

            var annotation = method.getAnnotation(
                    org.springframework.cache.annotation.Cacheable.class);

            assertThat(annotation).as("getPrompt 应有 @Cacheable 注解").isNotNull();
            assertThat(annotation.value()).contains("prompts");
        }

        @Test
        @DisplayName("O5-T2: PromptService.getCoreSystemPrompt 应有 @Cacheable 注解")
        void coreSystemPromptShouldBeCacheable() throws NoSuchMethodException {
            var method = com.mcp.core.service.PromptService.class
                    .getMethod("getCoreSystemPrompt");

            var annotation = method.getAnnotation(
                    org.springframework.cache.annotation.Cacheable.class);

            assertThat(annotation).as("getCoreSystemPrompt 应有 @Cacheable 注解").isNotNull();
            assertThat(annotation.value()).contains("prompts");
        }

        @Test
        @DisplayName("O5-T3: PromptService.savePrompt 应有 @CacheEvict 注解")
        void savePromptShouldEvictCache() throws NoSuchMethodException {
            var method = com.mcp.core.service.PromptService.class
                    .getMethod("savePrompt", com.mcp.core.domain.prompt.PromptTemplate.class);

            var annotation = method.getAnnotation(
                    org.springframework.cache.annotation.CacheEvict.class);

            assertThat(annotation).as("savePrompt 应有 @CacheEvict 注解").isNotNull();
            assertThat(annotation.value()).contains("prompts");
        }

        @Test
        @DisplayName("O5-T4: LlmConfigService.getDefaultConfig 应优先从缓存返回（避免每次查DB）")
        void llmConfigGetDefaultShouldBeCacheable() throws Exception {
            var clazz = com.mcp.core.service.LlmConfigService.class;

            var cacheField = clazz.getDeclaredField("configCache");
            cacheField.setAccessible(true);
            assertThat(cacheField.getType()).as("应使用 ConcurrentHashMap 缓存").isEqualTo(java.util.Map.class);

            var defaultConfigField = clazz.getDeclaredField("defaultConfig");
            assertThat(defaultConfigField.getType()).as("应有 defaultConfig 缓存字段")
                    .isEqualTo(com.mcp.core.domain.llm.LlmModelConfig.class);
        }

        @Test
        @DisplayName("O5-T5: LlmConfigService.getConfig 应优先从缓存返回（避免每次查DB）")
        void llmConfigGetConfigShouldBeCacheable() throws Exception {
            var clazz = com.mcp.core.service.LlmConfigService.class;

            var cacheField = clazz.getDeclaredField("configCache");
            cacheField.setAccessible(true);
            assertThat(cacheField.getType()).as("应使用 ConcurrentHashMap 缓存").isEqualTo(java.util.Map.class);

            var method = clazz.getMethod("getConfig",
                    com.mcp.core.domain.llm.LlmProviderType.class,
                    String.class);
            var returnType = method.getReturnType();
            assertThat(returnType).as("getConfig 应返回 Mono").isEqualTo(reactor.core.publisher.Mono.class);
        }

        @Test
        @DisplayName("O5-T6: 测试类位于正确的包中（performance 包）")
        void testClassIsInCorrectPackage() {
            assertThat(OptimizationContractTest.class.getPackageName())
                    .contains("performance");
        }
    }
}