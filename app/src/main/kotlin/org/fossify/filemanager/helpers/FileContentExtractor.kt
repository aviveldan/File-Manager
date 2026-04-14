package org.fossify.filemanager.helpers

import java.io.File

/**
 * Extracts text content from supported file formats for AI analysis.
 * Reads only the first [MAX_CHARS] characters to prevent OOM in the SLM.
 */
object FileContentExtractor {

    private const val MAX_CHARS = 4_000
    private const val TRUNCATION_MARKER = "\n...[Truncated]"

    private val SUPPORTED_EXTENSIONS = setOf("txt", "md", "csv", "json", "xml")

    /**
     * Reads the beginning of a text file for AI consumption.
     *
     * @return the first [MAX_CHARS] characters (with a truncation marker if the file is longer),
     *         or `null` if the file extension is not in the supported set.
     */
    fun extractTextForAi(file: File): String? {
        val extension = file.extension.lowercase()
        if (extension !in SUPPORTED_EXTENSIONS) return null
        if (!file.exists() || !file.canRead()) return null

        // Read MAX_CHARS + 1 to detect whether truncation occurred, avoiding
        // a byte-length vs char-count mismatch for multi-byte UTF-8 files.
        val readLimit = MAX_CHARS + 1
        val buffer = CharArray(readLimit)
        val charsRead = file.reader(Charsets.UTF_8).use { reader ->
            reader.read(buffer, 0, readLimit)
        }

        if (charsRead <= 0) return ""

        return if (charsRead > MAX_CHARS) {
            String(buffer, 0, MAX_CHARS) + TRUNCATION_MARKER
        } else {
            String(buffer, 0, charsRead)
        }
    }
}
