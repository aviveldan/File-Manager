package org.fossify.filemanager.helpers

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
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

        // It's a content URI — open a ParcelFileDescriptor and keep it alive for the engine.
        // Using /proc/self/fd/<N> lets the native C++ engine open the file without needing a
        // resolved filesystem path, which may be inaccessible via Java's File API on some devices.
        val uri = Uri.parse(uriString)
        val pfd = openPfdOrResolve(uri)
        val (modelPath, ownedPfd) = pfd

        try {
            // Skip Java-level file validation for /proc/self/fd/ paths: File.exists() returns
            // false for FD paths even though the file is valid and open — the native engine
            // opens it directly from the kernel path without going through the FUSE layer.
            if (!modelPath.startsWith("/proc/self/fd/")) {
                validateModelFile(modelPath)
            }
            runEngine(modelPath, prompt)
        } finally {
            ownedPfd?.close()
        }
    }

    private fun openPfdOrResolve(uri: Uri): Pair<String, ParcelFileDescriptor?> {
        // Try DocumentsContract path (works for primary external storage URIs)
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

        // Try to get a real filesystem path via /proc/self/fd/ symlink but keep PFD open.
        // The FD path /proc/self/fd/<N> is directly openable by native code even when
        // Java's File.exists() would fail on the mapped FUSE path.
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: error("Cannot open model file from URI: ${uri}")

        // Try to get a nicer filesystem path for diagnostics (non-critical)
        val fdPath = "/proc/self/fd/${pfd.fd}"
        val nicePathCandidate = try {
            val raw = File(fdPath).canonicalPath
            val normalized = raw.replace(DATA_MEDIA_PATH_REGEX, "/storage/emulated/$1/")
            // Prefer the human-readable FUSE path when possible, but skip File.exists()
            // check — it can fail even for valid files on some Android versions/devices.
            if (normalized.startsWith("/storage")) normalized else fdPath
        } catch (_: Exception) {
            fdPath
        }

        return Pair(nicePathCandidate, pfd)
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

