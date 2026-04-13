package org.fossify.filemanager.helpers

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.filemanager.interfaces.AiInferenceEngine
import java.io.File

class LiteRtInferenceEngine(private val context: Context) : AiInferenceEngine {

    companion object {
        private const val MAX_TOKENS = 512
        private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
        private const val PRIMARY_VOLUME = "primary"
        private val DATA_MEDIA_PATH_REGEX = Regex("^/data/media/(\\d+)/")
    }

    override suspend fun generateResponse(prompt: String): String = withContext(Dispatchers.IO) {
        val modelPath = resolveModelPath()
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(MAX_TOKENS)
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
            ?: error("Model path not configured. Please set it in Settings.")

        // If it's already an absolute file path (e.g. from tests or resolved by Settings), use it directly
        if (uriString.startsWith("/")) {
            return uriString
        }

        // It's a content URI — resolve to actual file path to avoid copying large model files
        val uri = Uri.parse(uriString)

        val resolvedPath = resolveContentUriToPath(uri)
        if (resolvedPath != null) {
            return resolvedPath
        }

        // Fallback: copy to cache (not ideal for large files)
        val cacheFile = File(context.cacheDir, "llm_model.task")
        if (!cacheFile.exists() || cacheFile.length() == 0L) {
            context.contentResolver.openInputStream(uri)?.use { input ->
                cacheFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: error("Cannot read model file from URI: $uriString")
        }

        return cacheFile.absolutePath
    }

    private fun resolveContentUriToPath(uri: Uri): String? {
        // Handle external storage document URIs
        if (DocumentsContract.isDocumentUri(context, uri) && uri.authority == EXTERNAL_STORAGE_AUTHORITY) {
            val docId = DocumentsContract.getDocumentId(uri)
            val parts = docId.split(":")
            if (parts.size == 2 && PRIMARY_VOLUME.equals(parts[0], ignoreCase = true)) {
                val path = "${Environment.getExternalStorageDirectory()}/${parts[1]}"
                if (File(path).exists()) {
                    return path
                }
            }
        }

        // Fallback: resolve via file descriptor symlink
        return resolveViaFileDescriptor(uri)
    }

    private fun resolveViaFileDescriptor(uri: Uri): String? {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
        return try {
            val fdPath = "/proc/self/fd/${pfd.fd}"
            val rawPath = File(fdPath).canonicalPath
            // Kernel symlink resolves to /data/media/<userId>/... but FUSE mount is
            // /storage/emulated/<userId>/... — normalize. Skip File.exists() check since
            // the FD is valid and the path is real even if Java's API can't verify it.
            val normalized = rawPath.replace(DATA_MEDIA_PATH_REGEX, "/storage/emulated/$1/")
            if (!normalized.startsWith("/proc") && !normalized.startsWith("/data/")) normalized else null
        } finally {
            pfd.close()
        }
    }
}
