package com.mtc.mtcai.core.di

import android.content.Context
import com.mtc.mtcai.core.device.FlashlightController
import com.mtc.mtcai.data.api.ApiDataInstance
import com.mtc.mtcai.data.api.VoiceAssistantApiService
import com.mtc.mtcai.data.repository.VoiceAssistantRepository
import com.mtc.mtcai.modules.handler.VoiceAssistantHandler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideApiService(): VoiceAssistantApiService {
        return ApiDataInstance.apiService
    }

    @Provides
    fun provideMainRepository(apiService: VoiceAssistantApiService): VoiceAssistantRepository {
        return VoiceAssistantRepository(apiService)
    }

    @Provides
    @Singleton
    fun provideFlashlightController(
        @ApplicationContext context: Context
    ): FlashlightController {
        return FlashlightController(context)
    }

    @Provides
    @Singleton
    fun provideVoiceAssistantHandler(
        repository: VoiceAssistantRepository
    ): VoiceAssistantHandler {
        return VoiceAssistantHandler(repository)
    }

}