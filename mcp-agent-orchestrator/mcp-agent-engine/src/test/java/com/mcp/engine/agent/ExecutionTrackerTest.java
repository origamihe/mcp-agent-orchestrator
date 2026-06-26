package com.mcp.engine.agent;

import com.mcp.engine.agent.ExecutionTracker.ToolObservation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionTrackerTest {

    private ExecutionTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new ExecutionTracker();
    }

    @Test
    @DisplayName("记录成功的工具调用")
    void shouldRecordSuccessfulToolCall() {
        tracker.recordToolCall("read_file", "path=/home/test.txt", true,
                "文件内容: Hello World", null, 120);

        assertThat(tracker.getObservations()).hasSize(1);
        ToolObservation obs = tracker.getObservations().get(0);
        assertThat(obs.toolName()).isEqualTo("read_file");
        assertThat(obs.success()).isTrue();
        assertThat(obs.durationMs()).isEqualTo(120);
        assertThat(obs.errorMessage()).isNull();
    }

    @Test
    @DisplayName("记录失败的工具调用")
    void shouldRecordFailedToolCall() {
        tracker.recordToolCall("search", "query=xxx", false,
                "无结果", "FileNotFoundException", 350);

        assertThat(tracker.getObservations()).hasSize(1);
        ToolObservation obs = tracker.getObservations().get(0);
        assertThat(obs.toolName()).isEqualTo("search");
        assertThat(obs.success()).isFalse();
        assertThat(obs.errorMessage()).isEqualTo("FileNotFoundException");
    }

    @Test
    @DisplayName("多次工具调用后生成执行摘要")
    void shouldBuildExecutionSummary() {
        tracker.recordToolCall("read_file", "path=/a.txt", true, "content A", null, 100);
        tracker.recordToolCall("search", "query=test", false, "无结果", "NotFound", 200);
        tracker.recordToolCall("write_file", "path=/b.txt", true, "写入成功", null, 150);

        String summary = tracker.buildExecutionSummary();
        assertThat(summary).contains("3 次");
        assertThat(summary).contains("成功: 2");
        assertThat(summary).contains("失败: 1");
        assertThat(summary).contains("read_file");
        assertThat(summary).contains("search");
        assertThat(summary).contains("write_file");
        assertThat(summary).contains("NotFound");
    }

    @Test
    @DisplayName("生成工具使用摘要")
    void shouldBuildToolsUsedSummary() {
        tracker.recordToolCall("read_file", "path=/a.txt", true, "ok", null, 100);
        tracker.recordToolCall("read_file", "path=/b.txt", true, "ok", null, 100);
        tracker.recordToolCall("search", "query=test", true, "ok", null, 100);

        String summary = tracker.buildToolsUsedSummary();
        assertThat(summary).contains("read_file");
        assertThat(summary).contains("search");
    }

    @Test
    @DisplayName("没有工具调用时摘要为空")
    void shouldReturnEmptySummaryWhenNoObservations() {
        assertThat(tracker.buildExecutionSummary()).contains("0 次");
        assertThat(tracker.buildToolsUsedSummary()).contains("无");
        assertThat(tracker.buildErrorSummary()).isNull();
    }

    @Test
    @DisplayName("成功时错误摘要返回 null")
    void shouldReturnNullErrorSummaryWhenAllSuccess() {
        tracker.recordToolCall("read_file", "path=/a.txt", true, "ok", null, 100);
        assertThat(tracker.buildErrorSummary()).isNull();
        assertThat(tracker.hasFailures()).isFalse();
    }

    @Test
    @DisplayName("失败时错误摘要包含错误信息")
    void shouldReturnErrorSummaryWhenHasFailures() {
        tracker.recordToolCall("read_file", "path=/a.txt", false, "", "Timeout", 5000);
        tracker.recordToolCall("search", "query=test", false, "", "Permission denied", 200);

        String errorSummary = tracker.buildErrorSummary();
        assertThat(errorSummary).contains("read_file");
        assertThat(errorSummary).contains("Timeout");
        assertThat(errorSummary).contains("search");
        assertThat(errorSummary).contains("Permission denied");
        assertThat(tracker.hasFailures()).isTrue();
    }

    @Test
    @DisplayName("记录匹配的 Skill 和 Failure ID")
    void shouldTrackMatchedSkillAndFailureIds() {
        tracker.addMatchedSkill(1L);
        tracker.addMatchedSkill(2L);
        tracker.addMatchedSkill(1L);
        tracker.addMatchedFailure(100L);

        assertThat(tracker.getMatchedSkillIds()).containsExactly(1L, 2L, 1L);
        assertThat(tracker.getMatchedFailureIds()).containsExactly(100L);
    }

    @Test
    @DisplayName("buildExecutionSummary 截断超长参数")
    void shouldTruncateLongArgumentsInSummary() {
        String longArgs = "a".repeat(200);
        tracker.recordToolCall("read_file", longArgs, true, "ok", null, 100);

        String summary = tracker.buildExecutionSummary();
        assertThat(summary).doesNotContain(longArgs);
        assertThat(summary).contains("...");
    }
}