package com.mcp.plugin.util

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageDetectorTest {

    @Test
    fun `should detect java from extension`() {
        val file = LightVirtualFile("Test.java")
        assertEquals("java", LanguageDetector.detect(file))
    }

    @Test
    fun `should detect kotlin from kt extension`() {
        val file = LightVirtualFile("Test.kt")
        assertEquals("kotlin", LanguageDetector.detect(file))
    }

    @Test
    fun `should detect kotlin from kts extension`() {
        val file = LightVirtualFile("build.kts")
        assertEquals("kotlin", LanguageDetector.detect(file))
    }

    @Test
    fun `should detect csharp from cs extension`() {
        val file = LightVirtualFile("Program.cs")
        assertEquals("csharp", LanguageDetector.detect(file))
    }

    @Test
    fun `should detect python from py extension`() {
        val file = LightVirtualFile("main.py")
        assertEquals("python", LanguageDetector.detect(file))
    }

    @Test
    fun `should detect javascript from js extension`() {
        val file = LightVirtualFile("app.js")
        assertEquals("javascript", LanguageDetector.detect(file))
    }

    @Test
    fun `should detect javascript from jsx extension`() {
        val file = LightVirtualFile("Component.jsx")
        assertEquals("javascript", LanguageDetector.detect(file))
    }

    @Test
    fun `should detect typescript from ts extension`() {
        val file = LightVirtualFile("index.ts")
        assertEquals("typescript", LanguageDetector.detect(file))
    }

    @Test
    fun `should detect typescript from tsx extension`() {
        val file = LightVirtualFile("Component.tsx")
        assertEquals("typescript", LanguageDetector.detect(file))
    }

    @Test
    fun `should detect go from go extension`() {
        val file = LightVirtualFile("main.go")
        assertEquals("go", LanguageDetector.detect(file))
    }

    @Test
    fun `should detect rust from rs extension`() {
        val file = LightVirtualFile("main.rs")
        assertEquals("rust", LanguageDetector.detect(file))
    }

    @Test
    fun `should detect ruby from rb extension`() {
        val file = LightVirtualFile("app.rb")
        assertEquals("ruby", LanguageDetector.detect(file))
    }

    @Test
    fun `should detect php from php extension`() {
        val file = LightVirtualFile("index.php")
        assertEquals("php", LanguageDetector.detect(file))
    }

    @Test
    fun `should detect swift from swift extension`() {
        val file = LightVirtualFile("main.swift")
        assertEquals("swift", LanguageDetector.detect(file))
    }

    @Test
    fun `should detect c from c extension`() {
        val file = LightVirtualFile("main.c")
        assertEquals("c", LanguageDetector.detect(file))
    }

    @Test
    fun `should detect cpp from cpp extension`() {
        val file = LightVirtualFile("main.cpp")
        assertEquals("cpp", LanguageDetector.detect(file))
    }

    @Test
    fun `should be case insensitive`() {
        val file = LightVirtualFile("Test.JAVA")
        assertEquals("java", LanguageDetector.detect(file))
    }

    @Test
    fun `should fallback to file type name for unknown extension`() {
        val file = LightVirtualFile("test.xyz")
        assertEquals("unknown", LanguageDetector.detect(file))
    }
}