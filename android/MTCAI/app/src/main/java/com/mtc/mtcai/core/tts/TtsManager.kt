package com.mtc.mtcai.core.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

object TtsManager : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var initialized = false

    fun init(context: Context) {
        val currentLocale = context.resources.configuration.locales[0]

        Log.d("log current lang", currentLocale.toString())
        if (tts == null) {
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = currentLocale
                    initialized = true
                }
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("ru_RU")
            initialized = true
        }
    }

    fun speak(text: String) {
        if (initialized) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
        initialized = false
    }
}
