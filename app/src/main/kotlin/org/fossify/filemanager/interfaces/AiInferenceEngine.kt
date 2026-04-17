package org.fossify.filemanager.interfaces

import android.graphics.Bitmap

interface AiInferenceEngine {
    suspend fun generateResponse(prompt: String): String

    /**
     * Generates a response using both a text prompt and an image (vision modality).
     * Not all engines support multimodal input — the default throws [UnsupportedOperationException].
     */
    suspend fun generateResponseForImage(prompt: String, bitmap: Bitmap): String {
        throw UnsupportedOperationException("This engine does not support multimodal (image) input.")
    }
}
