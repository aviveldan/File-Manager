package org.fossify.filemanager.interfaces

interface AiInferenceEngine {
    suspend fun generateResponse(prompt: String): String
}
