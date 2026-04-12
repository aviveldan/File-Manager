package org.fossify.filemanager.helpers

import android.app.AlertDialog
import android.content.Context
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.fossify.commons.extensions.toast
import org.fossify.filemanager.R
import org.fossify.filemanager.activities.SimpleActivity
import org.fossify.filemanager.interfaces.AiInferenceEngine
import java.io.File

class AiPlaygroundHelper(private val activity: SimpleActivity) {

    companion object {
        private const val BYTES_PER_MB = 1_048_576L
    }

    @Suppress("TooGenericExceptionCaught")
    fun open() {
        val prefs = activity.getSharedPreferences(activity.packageName, Context.MODE_PRIVATE)
        val modelPath = prefs.getString(PREF_LOCAL_LLM_PATH, null)
        if (modelPath.isNullOrEmpty()) {
            activity.toast(R.string.model_path_not_set)
            return
        }

        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_ai_playground, null)
        val promptInput = dialogView.findViewById<EditText>(R.id.ai_prompt_input)
        val outputText = dialogView.findViewById<TextView>(R.id.ai_output_text)
        val generateButton = dialogView.findViewById<Button>(R.id.ai_generate_button)

        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.ai_playground)
            .setView(dialogView)
            .setNegativeButton(R.string.cancel, null)
            .create()

        generateButton.setOnClickListener {
            val prompt = promptInput.text.toString()
            if (prompt.isBlank()) return@setOnClickListener

            outputText.text = activity.getString(R.string.generating)
            generateButton.isEnabled = false

            val engine = createInferenceEngine(modelPath)
            activity.lifecycleScope.launch {
                try {
                    val result = engine.generateResponse(prompt)
                    outputText.text = result
                } catch (e: Exception) {
                    val errorMsg = formatErrorMessage(e, modelPath)
                    outputText.text = activity.getString(R.string.ai_error, errorMsg)
                } finally {
                    generateButton.isEnabled = true
                }
            }
        }

        dialog.show()
    }

    private fun createInferenceEngine(modelPath: String): AiInferenceEngine {
        val pathLower = modelPath.lowercase()
        return if (pathLower.endsWith(".litertlm")) {
            LiteRtLmInferenceEngine(activity)
        } else {
            LiteRtInferenceEngine(activity)
        }
    }

    private fun formatErrorMessage(e: Exception, modelPath: String): String {
        val message = e.message ?: return "Unknown error"
        // Extract a user-friendly summary but keep technical details for debugging
        val summary = if (message.contains("Source Location Trace") || message.contains("third_party/")) {
            "Failed to load model. Please verify the model file is valid and compatible."
        } else {
            message.lines().firstOrNull { it.isNotBlank() } ?: message
        }
        val fileInfo = try {
            val file = File(modelPath)
            val sizeMb = "%.1f".format(file.length().toDouble() / BYTES_PER_MB)
            "Path: $modelPath\nExists: ${file.exists()}, Readable: ${file.canRead()}, " +
                "Size: $sizeMb MB"
        } catch (_: SecurityException) {
            "Path: $modelPath (unable to check file)"
        }
        // Include file diagnostics and full technical details for debugging
        return "$summary\n\n$fileInfo\n\nDetails: $message"
    }
}
