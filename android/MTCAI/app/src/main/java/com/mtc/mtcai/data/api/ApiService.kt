package com.mtc.mtcai.data.api

import com.mtc.mtcai.BuildConfig
import com.mtc.mtcai.data.model.VoiceAssistantResponse
import com.mtc.mtcai.data.model.VoiceCommandRequest
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

private const val BASE_URL = BuildConfig.BACKEND_URL

interface VoiceAssistantApiService {

    @POST("ai-response/")
    suspend fun processVoiceCommand(
        @Body message: VoiceCommandRequest
    ): VoiceAssistantResponse
}

object ApiDataInstance {

    private val authInterceptor = AuthInterceptor()

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val apiService: VoiceAssistantApiService by lazy { retrofit.create(VoiceAssistantApiService::class.java) }
}
