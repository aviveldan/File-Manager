package org.fossify.filemanager.helpers

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
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

    // --- Helper to create a PDF with text ---

    private fun createPdfWithText(file: File, text: String) {
        val document = PDDocument()
        try {
            val page = PDPage()
            document.addPage(page)
            PDPageContentStream(document, page).use { cs ->
                cs.beginText()
                cs.setFont(PDType1Font.HELVETICA, 12f)
                cs.newLineAtOffset(50f, 700f)
                cs.showText(text)
                cs.endText()
            }
            document.save(file)
        } finally {
            document.close()
        }
    }

    // --- Supported text extensions ---

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

    // --- PDF extraction ---

    @Test
    fun extractTextFromPdfFile() {
        val file = File(tempDir, "document.pdf")
        createPdfWithText(file, "Hello PDF World")
        val result = FileContentExtractor.extractTextForAi(file)
        assertNotNull(result)
        assertTrue("Should contain the PDF text", result!!.contains("Hello PDF World"))
    }

    @Test
    fun returnsNullForCorruptPdfFile() {
        val file = File(tempDir, "corrupt.pdf").apply { writeText("not a real pdf") }
        val result = FileContentExtractor.extractTextForAi(file)
        assertNull(result)
    }

    @Test
    fun truncatesPdfTextExceeding4000Chars() {
        val longText = "A".repeat(5000)
        val file = File(tempDir, "big.pdf")
        createPdfWithText(file, longText)
        val result = FileContentExtractor.extractTextForAi(file)

        assertNotNull(result)
        assertTrue("Should end with truncation marker", result!!.endsWith("\n...[Truncated]"))
        val textPart = result.removeSuffix("\n...[Truncated]")
        assertEquals(4000, textPart.length)
    }

    @Test
    fun doesNotTruncateShortPdfText() {
        val file = File(tempDir, "short.pdf")
        createPdfWithText(file, "Short text")
        val result = FileContentExtractor.extractTextForAi(file)

        assertNotNull(result)
        assertTrue("Should contain the text", result!!.contains("Short text"))
        assertTrue("Should NOT contain truncation marker", !result.contains("[Truncated]"))
    }

    @Test
    fun returnsNullForNonExistentPdfFile() {
        val file = File(tempDir, "missing.pdf")
        assertNull(FileContentExtractor.extractTextForAi(file))
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
    fun returnsNullForImageFile() {
        val file = File(tempDir, "photo.jpg").apply { writeText("binary data") }
        assertNull(FileContentExtractor.extractTextForAi(file))
    }

    @Test
    fun returnsNullForNoExtension() {
        val file = File(tempDir, "Makefile").apply { writeText("all: build") }
        assertNull(FileContentExtractor.extractTextForAi(file))
    }

    // --- Truncation (text files) ---

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

    @Test
    fun caseInsensitivePdfExtension() {
        val file = File(tempDir, "document.PDF")
        createPdfWithText(file, "Uppercase PDF")
        val result = FileContentExtractor.extractTextForAi(file)
        assertNotNull(result)
        assertTrue("Should contain the PDF text", result!!.contains("Uppercase PDF"))
    }
}
