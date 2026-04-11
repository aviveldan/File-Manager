package org.fossify.filemanager.helpers

import android.content.Context
import android.net.Uri
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.filemanager.interfaces.AiInferenceEngine
import java.io.File

class LiteRtInferenceEngine(private val context: Context) : AiInferenceEngine {

    override suspend fun generateResponse(prompt: String): String = withContext(Dispatchers.IO) {
        val modelPath = resolveModelPath()
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(512)
            .build()

        val llmInference = LlmInference.createFromOptions(context, options)
        try {
            llmInference.generateResponse(prompt)
        } finally {
            llmInference.close()
        }
    }

    private fun resolveModelPath(): String {
        val prefs = context.getSharedPreferences(context.packageName, Context.MODE_PRIVATE)
        val uriString = prefs.getString(PREF_LOCAL_LLM_PATH, null)
            ?: throw IllegalStateException("Model path not configured. Please set it in Settings.")

        // If it's already an absolute file path (e.g. from tests), use it directly
        if (uriString.startsWith("/")) {
            return uriString
        }

        // It's a content URI — copy to cache only if needed
        val uri = Uri.parse(uriString)
        val cacheFile = File(context.cacheDir, "llm_model.task")
        if (!cacheFile.exists() || cacheFile.length() == 0L) {
            context.contentResolver.openInputStream(uri)?.use { input ->
                cacheFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalStateException("Cannot read model file from URI: $uriString")
        }

        return cacheFile.absolutePath
    }
}
