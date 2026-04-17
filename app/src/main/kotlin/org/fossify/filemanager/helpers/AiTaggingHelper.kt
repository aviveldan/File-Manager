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
                val engine = createInferenceEngine(modelPath)
                val rawResponse = runInference(engine, file)
                val tags = parseTagResponse(rawResponse)

                activity.fileTagDao.insertOrUpdateTags(FileTag(filePath, tags))

                withContext(Dispatchers.Main) {
                    activity.toast(R.string.ai_tags_added)
                    onComplete?.invoke()
                }
            } catch (e: Exception) {
                val message = formatTaggingError(e.message)
                withContext(Dispatchers.Main) {
                    activity.toast(activity.getString(R.string.ai_tagging_failed, message))
                }
            }
        }
    }

    /**
     * Selects the correct inference path based on whether the file is a supported image.
     * Falls back to text-only tagging if the engine does not support vision modality.
     */
    @Suppress("SwallowedException")
    private suspend fun runInference(engine: AiInferenceEngine, file: File): String {
        val bitmap = ImageContentExtractor.extractBitmapForAi(file)
        if (bitmap != null) {
            try {
                return engine.generateResponseForImage(buildImagePrompt(), bitmap)
            } catch (_: UnsupportedOperationException) {
                // Engine doesn't support vision — fall back to text-only tagging below
            } finally {
                bitmap.recycle()
            }
        }
        return engine.generateResponse(buildPrompt(file))
    }

    private fun createInferenceEngine(modelPath: String): AiInferenceEngine {
        return if (isLiteRtLmModel(modelPath)) {
            LiteRtLmInferenceEngine(activity)
        } else {
            LiteRtInferenceEngine(activity)
        }
    }

    /**
     * Determines if the model at [modelPath] is a LiteRT-LM model based on its filename.
     * Checks direct path first, then resolves display name for content URIs, and falls
     * back to URI path segment inspection for providers where display name query fails.
     */
    private fun isLiteRtLmModel(modelPath: String): Boolean {
        if (modelPath.lowercase().endsWith(".litertlm")) return true
        if (!modelPath.startsWith("content://")) return false

        val displayName = resolveDisplayName(modelPath)
        if (displayName.lowercase().endsWith(".litertlm")) return true

        val lastSegment = Uri.parse(modelPath).lastPathSegment
        return lastSegment != null && lastSegment.lowercase().endsWith(".litertlm")
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
         * Builds the multimodal prompt for image files. The image bitmap is passed separately
         * via the vision modality, so the prompt only contains the instruction text.
         */
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        internal fun buildImagePrompt(): String {
            return "You are an offline file organizer. Analyze this image. " +
                "Generate exactly 3 descriptive category tags. " +
                "Respond with ONLY the 3 tags separated by commas. Do not explain."
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

        /**
         * Extracts a user-friendly error message from engine exceptions.
         * Native initialization failures often contain unhelpful protobuf-style codes
         * (e.g. "%UNKNOWN%") or stack traces that don't fit in a toast.
         */
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        internal fun formatTaggingError(rawMessage: String?): String {
            if (rawMessage == null) return "Unknown error"
            if (rawMessage.contains("%UNKNOWN%") ||
                rawMessage.contains("Source Location Trace") ||
                rawMessage.contains("third_party/")
            ) {
                return "Model initialization failed. The model may be incompatible — try a different model file."
            }
            return rawMessage.lines().firstOrNull { it.isNotBlank() } ?: rawMessage
        }
    }
}
