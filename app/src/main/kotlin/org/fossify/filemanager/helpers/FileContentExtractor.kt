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

        val buffer = CharArray(MAX_CHARS)
        val charsRead = file.reader(Charsets.UTF_8).use { reader ->
            reader.read(buffer, 0, MAX_CHARS)
        }

        if (charsRead <= 0) return ""

        val text = String(buffer, 0, charsRead)
        return if (file.length() > charsRead) {
            text + TRUNCATION_MARKER
        } else {
            text
        }
    }
}
