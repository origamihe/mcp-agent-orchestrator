package com.mcp.engine.reflection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LearningBudgetManagerTest {

    private LearningBudgetManager manager;

    @BeforeEach
    void setUp() {
        manager = new LearningBudgetManager();
    }

    @Test
    @DisplayName("正常任务请求应该允许 Reflection")
    void shouldAllowReflectionForNormalTask() {
        assertThat(manager.shouldReflect("session-1", "帮我在项目中搜索所有 Java 文件"))
                .isTrue();
    }

    @Test
    @DisplayName("闲聊请求应该被过滤")
    void shouldFilterChitchat() {
        assertThat(manager.shouldReflect("session-1", "你好")).isFalse();
        assertThat(manager.shouldReflect("session-1", "谢谢")).isFalse();
        assertThat(manager.shouldReflect("session-1", "好的")).isFalse();
        assertThat(manager.shouldReflect("session-1", "嗯")).isFalse();
        assertThat(manager.shouldReflect("session-1", "？")).isFalse();
    }

    @Test
    @DisplayName("过短请求应该被过滤")
    void shouldFilterTooShortRequest() {
        assertThat(manager.shouldReflect("session-1", "ab")).isFalse();
        assertThat(manager.shouldReflect("session-1", "abc")).isFalse();
    }

    @Test
    @DisplayName("recordReflection 后立即检查应被间隔限制阻止")
    void shouldBlockByIntervalAfterRecord() {
        String sessionId = "session-1";
        assertThat(manager.shouldReflect(sessionId, "帮我搜索文件")).isTrue();
        manager.recordReflection(sessionId);
        assertThat(manager.shouldReflect(sessionId, "帮我重构代码")).isFalse();
    }

    @Test
    @DisplayName("每会话最多 3 次 Reflection")
    void shouldLimitToMaxReflectionsPerSession() {
        String sessionId = "session-max";
        for (int i = 0; i < 3; i++) {
            manager.recordReflection(sessionId);
        }
        assertThat(manager.shouldReflect(sessionId, "帮我搜索文件")).isFalse();
    }

    @Test
    @DisplayName("不同会话不受频率限制影响")
    void shouldAllowDifferentSessions() {
        assertThat(manager.shouldReflect("session-1", "帮我搜索文件")).isTrue();
        manager.recordReflection("session-1");
        assertThat(manager.shouldReflect("session-2", "帮我重构代码")).isTrue();
    }

    @Test
    @DisplayName("null 请求应该被过滤")
    void shouldFilterNullRequest() {
        assertThat(manager.shouldReflect("session-1", null)).isFalse();
    }

    @Test
    @DisplayName("空请求应该被过滤")
    void shouldFilterEmptyRequest() {
        assertThat(manager.shouldReflect("session-1", "")).isFalse();
        assertThat(manager.shouldReflect("session-1", "   ")).isFalse();
    }
}