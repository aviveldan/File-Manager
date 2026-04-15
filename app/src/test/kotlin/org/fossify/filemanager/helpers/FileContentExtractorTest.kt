package org.fossify.filemanager.helpers

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class FileContentExtractorTest {

    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "fce_test_${System.nanoTime()}")
        tempDir.mkdirs()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    // --- Supported extensions ---

    @Test
    fun extractTextFromTxtFile() {
        val file = File(tempDir, "readme.txt").apply { writeText("Hello, world!") }
        val result = FileContentExtractor.extractTextForAi(file)
        assertEquals("Hello, world!", result)
    }

    @Test
    fun extractTextFromMdFile() {
        val file = File(tempDir, "notes.md").apply { writeText("# Title\nSome content") }
        val result = FileContentExtractor.extractTextForAi(file)
        assertEquals("# Title\nSome content", result)
    }

    @Test
    fun extractTextFromCsvFile() {
        val file = File(tempDir, "data.csv").apply { writeText("a,b,c\n1,2,3") }
        val result = FileContentExtractor.extractTextForAi(file)
        assertEquals("a,b,c\n1,2,3", result)
    }

    @Test
    fun extractTextFromJsonFile() {
        val content = """{"key": "value"}"""
        val file = File(tempDir, "config.json").apply { writeText(content) }
        val result = FileContentExtractor.extractTextForAi(file)
        assertEquals(content, result)
    }

    @Test
    fun extractTextFromXmlFile() {
        val content = "<root><item>test</item></root>"
        val file = File(tempDir, "data.xml").apply { writeText(content) }
        val result = FileContentExtractor.extractTextForAi(file)
        assertEquals(content, result)
    }

    // --- Unsupported extensions ---

    @Test
    fun returnsNullForApkFile() {
        val file = File(tempDir, "app.apk").apply { writeText("binary data") }
        assertNull(FileContentExtractor.extractTextForAi(file))
    }

    @Test
    fun returnsNullForMp4File() {
        val file = File(tempDir, "video.mp4").apply { writeText("binary data") }
        assertNull(FileContentExtractor.extractTextForAi(file))
    }

    @Test
    fun returnsNullForPdfFile() {
        val file = File(tempDir, "document.pdf").apply { writeText("binary data") }
        assertNull(FileContentExtractor.extractTextForAi(file))
    }

    @Test
    fun returnsNullForImageFile() {
        val file = File(tempDir, "photo.jpg").apply { writeText("binary data") }
        assertNull(FileContentExtractor.extractTextForAi(file))
    }

    @Test
    fun returnsNullForNoExtension() {
        val file = File(tempDir, "Makefile").apply { writeText("all: build") }
        assertNull(FileContentExtractor.extractTextForAi(file))
    }

    // --- Truncation ---

    @Test
    fun truncatesFileExceeding4000Chars() {
        val longContent = "x".repeat(5000)
        val file = File(tempDir, "big.txt").apply { writeText(longContent) }
        val result = FileContentExtractor.extractTextForAi(file)

        assertNotNull(result)
        assertTrue("Should end with truncation marker", result!!.endsWith("\n...[Truncated]"))
        // The text part (before marker) should be exactly 4000 chars
        val textPart = result.removeSuffix("\n...[Truncated]")
        assertEquals(4000, textPart.length)
    }

    @Test
    fun doesNotTruncateFileExactly4000Chars() {
        val content = "a".repeat(4000)
        val file = File(tempDir, "exact.txt").apply { writeText(content) }
        val result = FileContentExtractor.extractTextForAi(file)

        assertEquals(content, result)
        assertTrue("Should NOT contain truncation marker", !result!!.contains("[Truncated]"))
    }

    @Test
    fun doesNotTruncateFileUnder4000Chars() {
        val content = "short text"
        val file = File(tempDir, "small.txt").apply { writeText(content) }
        val result = FileContentExtractor.extractTextForAi(file)

        assertEquals(content, result)
    }

    // --- Edge cases ---

    @Test
    fun emptyFileReturnsEmptyString() {
        val file = File(tempDir, "empty.txt").apply { writeText("") }
        val result = FileContentExtractor.extractTextForAi(file)
        assertEquals("", result)
    }

    @Test
    fun nonExistentFileReturnsNull() {
        val file = File(tempDir, "missing.txt")
        assertNull(FileContentExtractor.extractTextForAi(file))
    }

    @Test
    fun caseInsensitiveExtensionMatching() {
        val file = File(tempDir, "UPPER.TXT").apply { writeText("uppercase extension") }
        val result = FileContentExtractor.extractTextForAi(file)
        assertEquals("uppercase extension", result)
    }

    @Test
    fun mixedCaseExtension() {
        val file = File(tempDir, "mixed.Json").apply { writeText("""{"a":1}""") }
        val result = FileContentExtractor.extractTextForAi(file)
        assertEquals("""{"a":1}""", result)
    }
}
