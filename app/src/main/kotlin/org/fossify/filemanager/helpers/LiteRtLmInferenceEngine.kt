package org.fossify.filemanager.helpers

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.filemanager.interfaces.AiInferenceEngine
import java.io.File

class LiteRtLmInferenceEngine(private val context: Context) : AiInferenceEngine {

    companion object {
        private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
        private const val PRIMARY_VOLUME = "primary"
    }

    override suspend fun generateResponse(prompt: String): String = withContext(Dispatchers.IO) {
        val modelPath = resolveModelPath()
        validateModelFile(modelPath)

        val engineConfig = EngineConfig(
            modelPath = modelPath,
            backend = Backend.CPU(),
            cacheDir = context.cacheDir.path,
        )
        Engine(engineConfig).use { engine ->
            engine.initialize()
            engine.createConversation().use { conversation ->
                conversation.sendMessage(prompt).toString()
            }
        }
    }

    private fun validateModelFile(path: String) {
        val file = File(path)
        if (!file.exists()) {
            error("Model file not found at: $path")
        }
        if (!file.canRead()) {
            error("Model file is not readable (check storage permissions): $path")
        }
        if (file.length() == 0L) {
            error("Model file is empty (0 bytes): $path")
        }
    }

    private fun resolveModelPath(): String {
        val prefs = context.getSharedPreferences(context.packageName, Context.MODE_PRIVATE)
        val uriString = prefs.getString(PREF_LOCAL_LLM_PATH, null)
            ?: error("Model path not configured. Please set it in Settings.")

        // If it's already an absolute file path (e.g. resolved by Settings), use it directly
        if (uriString.startsWith("/")) {
            return uriString
        }

        // It's a content URI — resolve to actual file path
        val uri = Uri.parse(uriString)

        val resolvedPath = resolveContentUriToPath(uri)
        if (resolvedPath != null) {
            return resolvedPath
        }

        error("Cannot resolve model file path from URI: $uriString")
    }

    private fun resolveContentUriToPath(uri: Uri): String? {
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

        return resolveViaFileDescriptor(uri)
    }

    private fun resolveViaFileDescriptor(uri: Uri): String? {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
        return try {
            val fdPath = "/proc/self/fd/${pfd.fd}"
            val realPath = File(fdPath).canonicalPath
            if (!realPath.startsWith("/proc") && File(realPath).exists()) realPath else null
        } finally {
            pfd.close()
        }
    }
}
