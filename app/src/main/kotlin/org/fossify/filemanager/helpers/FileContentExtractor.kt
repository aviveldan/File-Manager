package org.fossify.filemanager.helpers

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File

/**
 * Extracts text content from supported file formats for AI analysis.
 * Reads only the first [MAX_CHARS] characters to prevent OOM in the SLM.
 */
object FileContentExtractor {

    private const val MAX_CHARS = 4_000
    private const val TRUNCATION_MARKER = "\n...[Truncated]"

    private val TEXT_EXTENSIONS = setOf("txt", "md", "csv", "json", "xml")

    /**
     * Reads the beginning of a file for AI consumption.
     *
     * @return the first [MAX_CHARS] characters (with a truncation marker if the file is longer),
     *         or `null` if the file format is not supported or cannot be read.
     */
    fun extractTextForAi(file: File): String? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extension) {
            "pdf" -> extractPdfText(file)
            in TEXT_EXTENSIONS -> extractPlainText(file)
            else -> null
        }
    }

    private fun extractPdfText(file: File): String? {
        var document: PDDocument? = null
        return try {
            document = PDDocument.load(file)
            val text = PDFTextStripper().getText(document)
            truncateIfNeeded(text)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            null
        } catch (e: ExceptionInInitializerError) {
            null
        } catch (e: NoClassDefFoundError) {
            null
        } finally {
            document?.close()
        }
    }

    private fun extractPlainText(file: File): String? {
        // Read MAX_CHARS + 1 to detect whether truncation occurred, avoiding
        // a byte-length vs char-count mismatch for multi-byte UTF-8 files.
        val readLimit = MAX_CHARS + 1
        val buffer = CharArray(readLimit)
        val charsRead = file.reader(Charsets.UTF_8).use { reader ->
            reader.read(buffer, 0, readLimit)
        }

        if (charsRead <= 0) return ""

        val text = String(buffer, 0, charsRead)
        return truncateIfNeeded(text)
    }

    private fun truncateIfNeeded(text: String): String {
        return if (text.length > MAX_CHARS) {
            text.substring(0, MAX_CHARS) + TRUNCATION_MARKER
        } else {
            text
        }
    }
}
