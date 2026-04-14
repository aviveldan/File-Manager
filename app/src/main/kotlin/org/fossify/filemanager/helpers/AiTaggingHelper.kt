package org.fossify.filemanager.helpers

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.lifecycleScope
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.commons.extensions.toast
import org.fossify.filemanager.R
import org.fossify.filemanager.activities.SimpleActivity
import org.fossify.filemanager.database.FileTag
import org.fossify.filemanager.extensions.fileTagDao
import org.fossify.filemanager.interfaces.AiInferenceEngine
import java.io.File

/**
 * Orchestrates 1-click AI tagging: extracts file content, runs SLM inference on a background
 * thread, parses the result, and persists tags to Room.
 */
class AiTaggingHelper(private val activity: SimpleActivity) {

    /**
     * Launches the full AI-tagging pipeline for the given file path.
     * Shows toast feedback and writes results to the DB.
     *
     * @param filePath   absolute path of the file to tag
     * @param onComplete optional callback (on Main) after tags are saved, e.g. to refresh UI
     */
    @Suppress("TooGenericExceptionCaught")
    fun tagFile(filePath: String, onComplete: (() -> Unit)? = null) {
        val prefs = activity.getSharedPreferences(activity.packageName, Context.MODE_PRIVATE)
        val modelPath = prefs.getString(PREF_LOCAL_LLM_PATH, null)
        if (modelPath.isNullOrEmpty()) {
            activity.toast(R.string.model_path_not_set)
            return
        }

        val file = File(filePath)
        activity.toast(activity.getString(R.string.ai_analyzing, file.name))

        activity.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val prompt = buildPrompt(file)
                val engine = createInferenceEngine(modelPath)
                val rawResponse = engine.generateResponse(prompt)
                val tags = parseTagResponse(rawResponse)

                activity.fileTagDao.insertOrUpdateTags(FileTag(filePath, tags))

                withContext(Dispatchers.Main) {
                    activity.toast(R.string.ai_tags_added)
                    onComplete?.invoke()
                }
            } catch (e: Exception) {
                val message = e.message?.lines()?.firstOrNull { it.isNotBlank() }
                    ?: "Unknown error"
                withContext(Dispatchers.Main) {
                    activity.toast(activity.getString(R.string.ai_tagging_failed, message))
                }
            }
        }
    }

    private fun createInferenceEngine(modelPath: String): AiInferenceEngine {
        val filename = resolveDisplayName(modelPath)
        return if (filename.lowercase().endsWith(".litertlm")) {
            LiteRtLmInferenceEngine(activity)
        } else {
            LiteRtInferenceEngine(activity)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun resolveDisplayName(modelPath: String): String {
        if (!modelPath.startsWith("content://")) return modelPath
        return try {
            activity.contentResolver.query(
                Uri.parse(modelPath),
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null, null, null
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        } catch (_: Exception) {
            null
        } ?: modelPath
    }

    companion object {
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        internal const val TAGS_LIMIT = 3

        /**
         * Builds a content-aware prompt for the AI model. Includes file content for supported
         * text formats, or falls back to filename-only analysis for binary files.
         */
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        internal fun buildPrompt(file: File): String {
            val content = FileContentExtractor.extractTextForAi(file)
            return if (content != null) {
                "You are an offline file organizer. Analyze the following filename and file content. " +
                    "Generate exactly 3 descriptive category tags. " +
                    "Respond with ONLY the 3 tags separated by commas. Do not explain.\n" +
                    "Filename: '${file.name}'\n" +
                    "Content:\n$content"
            } else {
                "You are an offline file organizer. Analyze the following filename. " +
                    "Generate exactly 3 descriptive category tags. " +
                    "Respond with ONLY the 3 tags separated by commas. Do not explain.\n" +
                    "Filename: '${file.name}'"
            }
        }

        /**
         * Cleans the raw model output into a normalized comma-separated tag string.
         * Strips whitespace, empty segments, and limits to [TAGS_LIMIT] tags.
         */
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        internal fun parseTagResponse(raw: String): String {
            return raw.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .take(TAGS_LIMIT)
                .joinToString(", ")
        }
    }
}
