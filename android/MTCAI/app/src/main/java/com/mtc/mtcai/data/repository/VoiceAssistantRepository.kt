package com.mtc.mtcai.data.repository

import com.mtc.mtcai.data.api.VoiceAssistantApiService
import com.mtc.mtcai.data.model.VoiceAssistantResponse
import com.mtc.mtcai.data.model.VoiceCommandRequest

class VoiceAssistantRepository(private val apiService: VoiceAssistantApiService) {

    suspend fun getVoiceResponse(text: String): VoiceAssistantResponse {
        val request = VoiceCommandRequest(message = text)
        return apiService.processVoiceCommand(request)
    }

}
