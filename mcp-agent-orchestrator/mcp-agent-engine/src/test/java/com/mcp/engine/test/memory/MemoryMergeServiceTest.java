package com.mcp.engine.test.memory;

import com.mcp.common.identity.MemoryIdentity;
import com.mcp.core.domain.memory.MemoryType;
import com.mcp.core.entity.MemoryPackageEntity;
import com.mcp.core.repository.MemoryPackageRepository;
import com.mcp.engine.memory.MemoryEvaluator;
import com.mcp.engine.memory.MemoryMergeService;
import com.mcp.engine.memory.MemoryMergeService.MergeResult;
import com.mcp.engine.memory.MemoryMergeService.MergeResult.MergeAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemoryMergeServiceTest {

    @Mock
    private MemoryPackageRepository repository;

    private MemoryMergeService service;
    private MemoryIdentity identity;

    @BeforeEach
    void setUp() {
        service = new MemoryMergeService(repository);
        identity = new MemoryIdentity(null, "session-1", "user-1", null, null);
    }

    @Test
    @DisplayName("内容规范化应该去掉引号和标点")
    void shouldNormalizeContent() {
        assertThat(MemoryMergeService.normalizeContent("\"我喜欢 Terraria\""))
                .isEqualTo("terraria");
        assertThat(MemoryMergeService.normalizeContent("《我最喜欢 Minecraft》"))
                .isEqualTo("minecraft");
        assertThat(MemoryMergeService.normalizeContent("我喜欢 Terraria。"))
                .isEqualTo("terraria");
        assertThat(MemoryMergeService.normalizeContent("以后记住：我喜欢 Stardew Valley"))
                .isEqualTo("以后记住：我喜欢 stardew valley");
    }

    @Test
    @DisplayName("生成factKey应该符合规范")
    void shouldGenerateFactKey() {
        String key = MemoryMergeService.generateFactKey(MemoryType.PREFERENCE, "terraria");
        assertThat(key).isEqualTo("pref:terraria");
    }

    @Test
    @DisplayName("创建新记忆当没有匹配时")
    void shouldCreateNewWhenNoMatch() {
        MemoryEvaluator.ScoredMemory scored = new MemoryEvaluator.ScoredMemory(
                "我喜欢 Terraria", MemoryType.PREFERENCE, 80, 80, true);

        String factKey = MemoryMergeService.generateFactKey(MemoryType.PREFERENCE,
                MemoryMergeService.normalizeContent("我喜欢 Terraria"));

        when(repository.findByUserIdAndFactKeyAndIsActiveTrue(any(), any()))
                .thenReturn(Optional.empty());
        when(repository.findBySessionIdAndUserIdAndMemoryTypeAndIsActiveTrueOrderByWeightDesc(any(), any(), any()))
                .thenReturn(java.util.List.of());

        MergeResult result = service.processCandidate(identity, scored);

        assertThat(result.action()).isEqualTo(MergeAction.NEW);
        assertThat(result.factKey()).isEqualTo(factKey);
    }

    @Test
    @DisplayName("低价值记忆应该直接丢弃")
    void shouldDropLowValueMemory() {
        MemoryEvaluator.ScoredMemory scored = new MemoryEvaluator.ScoredMemory(
                "嗯", MemoryType.FACT, 3, 10, false);

        MergeResult result = service.processCandidate(identity, scored);

        assertThat(result.action()).isEqualTo(MergeAction.DROP);
    }

    @Test
    @DisplayName("高相似度规范化内容应该触发UPDATE")
    void shouldTriggerUpdateOnHighSimilarity() {
        MemoryPackageEntity existing = new MemoryPackageEntity();
        existing.setId(1L);
        existing.setContent("我喜欢 Terraria");
        existing.setActive(true);
        existing.setImportance(80);
        existing.setMemoryType(MemoryType.PREFERENCE);

        when(repository.findByUserIdAndFactKeyAndIsActiveTrue(any(), any()))
                .thenReturn(Optional.empty());
        when(repository.findBySessionIdAndUserIdAndMemoryTypeAndIsActiveTrueOrderByWeightDesc(any(), any(), any()))
                .thenReturn(java.util.List.of(existing));

        MemoryEvaluator.ScoredMemory scored = new MemoryEvaluator.ScoredMemory(
                "我喜欢 terraria", MemoryType.PREFERENCE, 85, 85, true);

        MergeResult result = service.processCandidate(identity, scored);

        assertThat(result.action()).isEqualTo(MergeAction.UPDATE);
    }
}