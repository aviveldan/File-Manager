package org.fossify.filemanager.helpers

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.system.Os
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
        private val DATA_MEDIA_PATH_REGEX = Regex("^/data/media/(\\d+)/")
    }

    override suspend fun generateResponse(prompt: String): String = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(context.packageName, Context.MODE_PRIVATE)
        val uriString = prefs.getString(PREF_LOCAL_LLM_PATH, null)
            ?: error("Model path not configured. Please set it in Settings.")

        // If it's already an absolute filesystem path (resolved at Settings save time), use directly.
        if (uriString.startsWith("/")) {
            validateModelFile(uriString)
            return@withContext runEngine(uriString, prompt)
        }

        // Content URI path: open a ParcelFileDescriptor and resolve to a usable file path.
        val uri = Uri.parse(uriString)
        val (modelPath, ownedPfd) = openAndResolve(uri)

        try {
            runEngine(modelPath, prompt)
        } finally {
            ownedPfd?.close()
        }
    }

    /**
     * Resolves a content URI to a file path that the native LiteRT-LM engine can open.
     * Returns the path and (if applicable) an open PFD that must be kept alive until the
     * engine finishes — the native code reads through the FD.
     */
    private fun openAndResolve(uri: Uri): Pair<String, ParcelFileDescriptor?> {
        // 1. Try DocumentsContract path (primary external storage provider gives us a plain path)
        if (DocumentsContract.isDocumentUri(context, uri) && uri.authority == EXTERNAL_STORAGE_AUTHORITY) {
            val docId = DocumentsContract.getDocumentId(uri)
            val parts = docId.split(":")
            if (parts.size == 2 && PRIMARY_VOLUME.equals(parts[0], ignoreCase = true)) {
                val path = "${Environment.getExternalStorageDirectory()}/${parts[1]}"
                if (File(path).exists()) {
                    return Pair(path, null)
                }
            }
        }

        // 2. Open a ParcelFileDescriptor — this works for all providers including Downloads msf: URIs.
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: error("Cannot open model file from URI: $uri")
        val fdPath = "/proc/self/fd/${pfd.fd}"

        // 3. Try to resolve the FD to a human-readable FUSE path via canonicalPath.
        val fusePath = try {
            val raw = File(fdPath).canonicalPath
            val normalized = raw.replace(DATA_MEDIA_PATH_REGEX, "/storage/emulated/$1/")
            if (normalized.startsWith("/storage")) normalized else null
        } catch (_: Exception) {
            null
        }

        // 4. If the FUSE path is valid and accessible, use it — it has the right extension.
        if (fusePath != null && File(fusePath).exists()) {
            return Pair(fusePath, pfd)
        }

        // 5. Fallback: create a symlink with .litertlm extension in cacheDir.
        //    /proc/self/fd/N has no extension, which causes LiteRT-LM format auto-detection to fail.
        //    A symlink in cacheDir keeps the correct extension while the FD stays open.
        //    Use pfd.fd in the name to avoid races between concurrent calls.
        val linkFile = File(context.cacheDir, "litert_model_link_${pfd.fd}.litertlm")
        return try {
            if (linkFile.exists() && !linkFile.delete()) {
                error("Cannot replace stale model symlink: ${linkFile.absolutePath}")
            }
            Os.symlink(fdPath, linkFile.absolutePath)
            Pair(linkFile.absolutePath, pfd)
        } catch (_: Exception) {
            // Last resort: pass the raw FD path; engine will attempt to open it directly.
            Pair(fdPath, pfd)
        }
    }

    private fun runEngine(modelPath: String, prompt: String): String {
        val engineConfig = EngineConfig(
            modelPath = modelPath,
            backend = Backend.CPU(),
            cacheDir = context.cacheDir.path,
        )
        return Engine(engineConfig).use { engine ->
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
}

