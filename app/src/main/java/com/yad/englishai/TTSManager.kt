package com.yad.englishai

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * TTSManager - Mengelola Text-to-Speech
 */
class TTSManager(context: Context) : TextToSpeech.OnInitListener {
    
    private var tts: TextToSpeech? = null
    private var isReady = false
    private var onReadyCallback: (() -> Unit)? = null
    
    init {
        tts = TextToSpeech(context, this)
    }
    
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            isReady = result != TextToSpeech.LANG_MISSING_DATA && 
                     result != TextToSpeech.LANG_NOT_SUPPORTED
            onReadyCallback?.invoke()
        }
    }
    
    fun speak(text: String) {
        if (isReady && text.isNotEmpty()) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_$text")
        }
    }
    
    fun setOnReady(callback: () -> Unit) {
        onReadyCallback = callback
        if (isReady) callback()
    }
    
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}