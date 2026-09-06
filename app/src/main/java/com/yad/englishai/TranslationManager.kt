package com.yad.englishai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * TranslationManager - Mengelola logika terjemahan
 * Dipisahkan dari service untuk mengurangi complexity
 */
class TranslationManager(private val context: Context) {
    
    companion object {
        private const val TRANSLATE_URL = "https://translate.googleapis.com/translate_a/single"
        private const val TIMEOUT_CONNECT = 5000
        private const val TIMEOUT_READ = 10000
        private const val MAX_TEXT_LENGTH = 5000
    }
    
    /**
     * Terjemahkan teks secara async
     */
    suspend fun translate(text: String): TranslationResult {
        return withContext(Dispatchers.IO) {
            try {
                val encodedText = URLEncoder.encode(text, "UTF-8")
                val url = URL("$TRANSLATE_URL?client=gtx&sl=auto&tl=id&dt=t&q=$encodedText")
                
                val connection = url.openConnection() as HttpURLConnection
                connection.apply {
                    requestMethod = "GET"
                    connectTimeout = TIMEOUT_CONNECT
                    readTimeout = TIMEOUT_READ
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                }
                
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                
                TranslationResult.Success(parseResponse(response))
            } catch (e: Exception) {
                TranslationResult.Error(context.getString(R.string.translation_failed))
            }
        }
    }
    
    /**
     * Parse response dari Google Translate API
     */
    private fun parseResponse(response: String): String {
        return try {
            val jsonArray = JSONObject(response).getJSONArray("sentences")
            val result = StringBuilder()
            
            for (i in 0 until jsonArray.length()) {
                result.append(jsonArray.getJSONObject(i).getString("trans"))
            }
            
            result.toString().trim()
        } catch (e: Exception) {
            context.getString(R.string.translation_failed)
        }
    }
    
    /**
     * Check validitas teks
     */
    fun isValidText(text: String): Boolean {
        return text.isNotBlank() && text.length <= MAX_TEXT_LENGTH
    }
    
    /**
     * Result sealed class
     */
    sealed class TranslationResult {
        data class Success(val text: String) : TranslationResult()
        data class Error(val message: String) : TranslationResult()
    }
}