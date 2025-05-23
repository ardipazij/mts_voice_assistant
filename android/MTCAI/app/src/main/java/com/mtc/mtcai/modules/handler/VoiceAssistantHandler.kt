package com.mtc.mtcai.modules.handler

import com.mtc.mtcai.data.model.VoiceAssistantResponse
import com.mtc.mtcai.data.repository.VoiceAssistantRepository
import javax.inject.Inject

class VoiceAssistantHandler @Inject constructor(
    private val repository: VoiceAssistantRepository
) {
    suspend fun handle(text: String): VoiceAssistantResponse {
        return repository.getVoiceResponse(text)
    }
}
