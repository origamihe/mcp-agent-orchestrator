package com.mcp.plugin.prompt

import org.junit.Assert.*
import org.junit.Test

/**
 * PromptRegistry 单元测试。
 *
 * 验证：
 * 1. Prompt 定义完整性
 * 2. generate_commit 特殊处理逻辑
 * 3. 所有 Prompt 都有必要字段
 */
class PromptRegistryTest {

    @Test
    fun `should have 8 prompts defined`() {
        assertEquals(8, PromptRegistry.prompts.size)
    }

    @Test
    fun `all prompts should have unique ids`() {
        val ids = PromptRegistry.prompts.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `all prompts should have non-empty id`() {
        PromptRegistry.prompts.forEach { prompt ->
            assertTrue("Prompt id should not be empty: ${prompt.label}", prompt.id.isNotBlank())
        }
    }

    @Test
    fun `all prompts should have non-empty label`() {
        PromptRegistry.prompts.forEach { prompt ->
            assertTrue("Prompt label should not be empty: ${prompt.id}", prompt.label.isNotBlank())
        }
    }

    @Test
    fun `all prompts should have non-empty description`() {
        PromptRegistry.prompts.forEach { prompt ->
            assertTrue("Prompt description should not be empty: ${prompt.id}", prompt.description.isNotBlank())
        }
    }

    @Test
    fun `all prompts should have non-empty prompt text`() {
        PromptRegistry.prompts.forEach { prompt ->
            assertTrue("Prompt text should not be empty: ${prompt.id}", prompt.prompt.isNotBlank())
        }
    }

    @Test
    fun `should contain explain prompt`() {
        val explain = PromptRegistry.prompts.find { it.id == "explain" }
        assertNotNull("Explain prompt should exist", explain)
        assertTrue(explain!!.prompt.contains("解释"))
    }

    @Test
    fun `should contain optimize prompt`() {
        val optimize = PromptRegistry.prompts.find { it.id == "optimize" }
        assertNotNull("Optimize prompt should exist", optimize)
        assertTrue(optimize!!.prompt.contains("优化"))
    }

    @Test
    fun `should contain refactor prompt`() {
        val refactor = PromptRegistry.prompts.find { it.id == "refactor" }
        assertNotNull("Refactor prompt should exist", refactor)
        assertTrue(refactor!!.prompt.contains("重构"))
    }

    @Test
    fun `should contain generate_test prompt`() {
        val genTest = PromptRegistry.prompts.find { it.id == "generate_test" }
        assertNotNull("generate_test prompt should exist", genTest)
        assertTrue(genTest!!.prompt.contains("测试"))
    }

    @Test
    fun `should contain review prompt`() {
        val review = PromptRegistry.prompts.find { it.id == "review" }
        assertNotNull("Review prompt should exist", review)
        assertTrue(review!!.prompt.contains("审查"))
    }

    @Test
    fun `should contain fix prompt`() {
        val fix = PromptRegistry.prompts.find { it.id == "fix" }
        assertNotNull("Fix prompt should exist", fix)
        assertTrue(fix!!.prompt.contains("修复"))
    }

    @Test
    fun `should contain generate_doc prompt`() {
        val genDoc = PromptRegistry.prompts.find { it.id == "generate_doc" }
        assertNotNull("generate_doc prompt should exist", genDoc)
        assertTrue(genDoc!!.prompt.contains("文档"))
    }

    @Test
    fun `should contain generate_commit prompt`() {
        val genCommit = PromptRegistry.prompts.find { it.id == "generate_commit" }
        assertNotNull("generate_commit prompt should exist", genCommit)
        assertTrue(genCommit!!.prompt.contains("commit"))
    }

    @Test
    fun `generate_commit should be the last prompt`() {
        val lastPrompt = PromptRegistry.prompts.last()
        assertEquals("generate_commit", lastPrompt.id)
    }

    @Test
    fun `generate_commit prompt should reference git diff`() {
        val genCommit = PromptRegistry.prompts.find { it.id == "generate_commit" }
        assertNotNull(genCommit)
        assertTrue(genCommit!!.prompt.contains("git diff", ignoreCase = true))
    }

    @Test
    fun `PromptDef should have correct data class structure`() {
        val prompt = PromptDef(
            id = "test_id",
            label = "Test Label",
            description = "Test Description",
            prompt = "Test Prompt"
        )
        assertEquals("test_id", prompt.id)
        assertEquals("Test Label", prompt.label)
        assertEquals("Test Description", prompt.description)
        assertEquals("Test Prompt", prompt.prompt)
    }

    @Test
    fun `PromptDef equality should work correctly`() {
        val a = PromptDef("id", "label", "desc", "prompt")
        val b = PromptDef("id", "label", "desc", "prompt")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `PromptDef should not equal different id`() {
        val a = PromptDef("id1", "label", "desc", "prompt")
        val b = PromptDef("id2", "label", "desc", "prompt")
        assertNotEquals(a, b)
    }
}