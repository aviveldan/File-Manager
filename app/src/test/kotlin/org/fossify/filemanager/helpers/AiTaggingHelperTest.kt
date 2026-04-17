package org.fossify.filemanager.helpers

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Tests the pure-logic companion methods of [AiTaggingHelper]: prompt building and tag parsing.
 * These are JVM-only tests that don't require an Android runtime.
 */
class AiTaggingHelperTest {

    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "ath_test_${System.nanoTime()}")
        tempDir.mkdirs()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    // --- parseTagResponse ---

    @Test
    fun parseTagResponseTrimsWhitespace() {
        val result = AiTaggingHelper.parseTagResponse("  tag1 ,  tag2 ,  tag3  ")
        assertEquals("tag1, tag2, tag3", result)
    }

    @Test
    fun parseTagResponseLimitsToThreeTags() {
        val result = AiTaggingHelper.parseTagResponse("a, b, c, d, e")
        assertEquals("a, b, c", result)
    }

    @Test
    fun parseTagResponseFiltersEmptySegments() {
        val result = AiTaggingHelper.parseTagResponse("tag1,,, tag2,  , tag3")
        assertEquals("tag1, tag2, tag3", result)
    }

    @Test
    fun parseTagResponseHandlesSingleTag() {
        val result = AiTaggingHelper.parseTagResponse("only-one")
        assertEquals("only-one", result)
    }

    @Test
    fun parseTagResponseHandlesEmptyInput() {
        val result = AiTaggingHelper.parseTagResponse("")
        assertEquals("", result)
    }

    @Test
    fun parseTagResponseHandlesCommasOnly() {
        val result = AiTaggingHelper.parseTagResponse(",,,")
        assertEquals("", result)
    }

    // --- buildPrompt ---

    @Test
    fun buildPromptIncludesContentForSupportedFile() {
        val file = File(tempDir, "notes.txt").apply { writeText("Important meeting notes") }
        val prompt = AiTaggingHelper.buildPrompt(file)

        assertTrue("Should contain filename", prompt.contains("Filename: 'notes.txt'"))
        assertTrue("Should contain content section", prompt.contains("Content:"))
        assertTrue("Should contain file content", prompt.contains("Important meeting notes"))
        assertTrue("Should contain instruction", prompt.contains("3 descriptive category tags"))
    }

    @Test
    fun buildPromptFallsBackToFilenameOnlyForBinaryFile() {
        val file = File(tempDir, "video.mp4").apply { writeText("binary") }
        val prompt = AiTaggingHelper.buildPrompt(file)

        assertTrue("Should contain filename", prompt.contains("Filename: 'video.mp4'"))
        assertFalse("Should NOT contain content section", prompt.contains("Content:"))
        assertTrue("Should contain instruction", prompt.contains("3 descriptive category tags"))
    }

    @Test
    fun buildPromptIncludesTruncationMarkerForLargeFile() {
        val content = "x".repeat(5000)
        val file = File(tempDir, "large.csv").apply { writeText(content) }
        val prompt = AiTaggingHelper.buildPrompt(file)

        assertTrue("Should contain truncation marker", prompt.contains("[Truncated]"))
        assertTrue("Should contain content section", prompt.contains("Content:"))
    }

    // --- buildImagePrompt ---

    @Test
    fun buildImagePromptContainsImageInstruction() {
        val prompt = AiTaggingHelper.buildImagePrompt()

        assertTrue("Should mention image analysis", prompt.contains("Analyze this image"))
        assertTrue("Should request 3 tags", prompt.contains("3 descriptive category tags"))
        assertTrue("Should request comma-separated", prompt.contains("separated by commas"))
        assertFalse("Should NOT contain filename placeholder", prompt.contains("Filename:"))
    }

    // --- formatTaggingError ---

    @Test
    fun formatTaggingErrorReturnsUserFriendlyMessageForUnknown() {
        val result = AiTaggingHelper.formatTaggingError("Initialization %UNKNOWN% at native layer")
        assertTrue("Should suggest incompatible model", result.contains("incompatible"))
    }

    @Test
    fun formatTaggingErrorReturnsUserFriendlyMessageForStackTrace() {
        val result = AiTaggingHelper.formatTaggingError("Error\nSource Location Trace:\nthird_party/foo.cc:42")
        assertTrue("Should suggest incompatible model", result.contains("incompatible"))
    }

    @Test
    fun formatTaggingErrorPreservesFirstLineForNormalErrors() {
        val result = AiTaggingHelper.formatTaggingError("Model file not found at: /path/model.task")
        assertEquals("Model file not found at: /path/model.task", result)
    }

    @Test
    fun formatTaggingErrorHandlesNullMessage() {
        val result = AiTaggingHelper.formatTaggingError(null)
        assertEquals("Unknown error", result)
    }
}
