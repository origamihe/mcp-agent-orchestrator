package com.mcp.core.service;

import com.mcp.core.domain.prompt.PromptTemplate;
import com.mcp.core.domain.prompt.PromptType;
import com.mcp.core.entity.PromptTemplateEntity;
import com.mcp.core.mapper.PromptTemplateMapper;
import com.mcp.core.repository.PromptTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PromptService - stripMemoryInstructions 安全网")
class PromptServiceTest {

    @Mock
    private PromptTemplateRepository repository;

    @Mock
    private PromptTemplateMapper mapper;

    private PromptService promptService;

    @BeforeEach
    void setUp() {
        promptService = new PromptService(repository, mapper);
    }

    @Nested
    @DisplayName("getCoreSystemPrompt 应清洗 [Internal_Memory_Storage] 指令")
    class StripMemoryInstructions {

        @Test
        @DisplayName("应移除 Prompt 中的 [Internal_Memory_Storage] 指令，保留正文")
        void shouldRemoveInternalMemoryStorageFromPrompt() {
            PromptTemplateEntity entity = new PromptTemplateEntity();
            PromptTemplate template = new PromptTemplate(
                    "core_system",
                    PromptType.SYSTEM,
                    "你是一个助手。\n如果需要记忆，请输出：\n[Internal_Memory_Storage]\n{\"key\":\"nickname\",\"value\":\"$value\"}\n请友好地回复用户。",
                    "核心系统Prompt",
                    1,
                    java.time.LocalDateTime.now()
            );

            when(repository.findByName("core_system")).thenReturn(Optional.of(entity));
            when(mapper.toDomain(entity)).thenReturn(template);

            StepVerifier.create(promptService.getCoreSystemPrompt())
                    .expectNextMatches(result ->
                            result.contains("你是一个助手") &&
                                    result.contains("请友好地回复用户") &&
                                    !result.contains("[Internal_Memory_Storage]") &&
                                    !result.contains("nickname")
                    )
                    .verifyComplete();
        }

        @Test
        @DisplayName("不含 [Internal_Memory_Storage] 的 Prompt 应保持不变")
        void shouldNotModifyCleanPrompt() {
            PromptTemplateEntity entity = new PromptTemplateEntity();
            PromptTemplate template = new PromptTemplate(
                    "core_system",
                    PromptType.SYSTEM,
                    "你是一个专业、友好、高效的 AI Agent 助手。",
                    "核心系统Prompt",
                    1,
                    java.time.LocalDateTime.now()
            );

            when(repository.findByName("core_system")).thenReturn(Optional.of(entity));
            when(mapper.toDomain(entity)).thenReturn(template);

            StepVerifier.create(promptService.getCoreSystemPrompt())
                    .expectNext("你是一个专业、友好、高效的 AI Agent 助手。")
                    .verifyComplete();
        }

        @Test
        @DisplayName("多行格式的 [Internal_Memory_Storage] 指令应被清洗")
        void shouldRemoveMultiLineMemoryInstruction() {
            PromptTemplateEntity entity = new PromptTemplateEntity();
            PromptTemplate template = new PromptTemplate(
                    "core_system",
                    PromptType.SYSTEM,
                    """
                            你是一个助手。
                            
                            如果需要记忆用户信息，请输出以下格式：
                            [Internal_Memory_Storage]
                            {
                              "key": "UserNickname",
                              "value": "用户昵称"
                            }
                            
                            回复要简洁友好。""",
                    "核心系统Prompt",
                    1,
                    java.time.LocalDateTime.now()
            );

            when(repository.findByName("core_system")).thenReturn(Optional.of(entity));
            when(mapper.toDomain(entity)).thenReturn(template);

            StepVerifier.create(promptService.getCoreSystemPrompt())
                    .expectNextMatches(result ->
                            result.contains("你是一个助手") &&
                                    result.contains("回复要简洁友好") &&
                                    !result.contains("[Internal_Memory_Storage]") &&
                                    !result.contains("UserNickname")
                    )
                    .verifyComplete();
        }

        @Test
        @DisplayName("DB 中不存在 core_system 时应使用干净的默认值")
        void shouldUseCleanDefaultWhenNotFound() {
            when(repository.findByName("core_system")).thenReturn(Optional.empty());

            StepVerifier.create(promptService.getCoreSystemPrompt())
                    .expectNextMatches(result ->
                            result.contains("AI Agent 助手") &&
                                    !result.contains("[Internal_Memory_Storage]")
                    )
                    .verifyComplete();
        }
    }
}