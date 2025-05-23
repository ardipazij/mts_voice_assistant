package com.mtc.mtcai.data.model

data class VoiceAssistantResponse(
    val command: String,
    val response: String,
    val params: Map<String, String>
)

