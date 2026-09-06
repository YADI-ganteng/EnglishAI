package com.yad.englishai

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * MainActivity - Halaman utama EnglishAI
 */
class MainActivity : AppCompatActivity() {
    
    private lateinit var inputEditText: EditText
    private lateinit var resultTextView: TextView
    private lateinit var btnTranslate: Button
    private lateinit var btnFloating: Button
    private lateinit var btnSpeak: Button
    
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    
    companion object {
        const val OVERLAY_PERMISSION_REQUEST = 1001
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initializeViews()
        setupListeners()
    }
    
    private fun initializeViews() {
        inputEditText = findViewById(R.id.inputEditText)
        resultTextView = findViewById(R.id.resultTextView)
        btnTranslate = findViewById(R.id.btnTranslate)
        btnFloating = findViewById(R.id.btnFloating)
        btnSpeak = findViewById(R.id.btnSpeak)
    }
    
    private fun setupListeners() {
        btnTranslate.setOnClickListener {
            handleTranslate()
        }
        
        btnFloating.setOnClickListener {
            handleFloatingTranslator()
        }
        
        btnSpeak.setOnClickListener {
            handleSpeak()
        }
    }
    
    private fun handleTranslate() {
        val text = inputEditText.text.toString().trim()
        
        if (text.isEmpty()) {
            Toast.makeText(this, R.string.no_text, Toast.LENGTH_SHORT).show()
            return
        }
        
        resultTextView.text = getString(R.string.translating)
        
        scope.launch {
            val result = translateText(text)
            resultTextView.text = result
        }
    }
    
    private fun handleFloatingTranslator() {
        if (checkOverlayPermission()) {
            startFloatingService()
        } else {
            requestOverlayPermission()
        }
    }
    
    private fun handleSpeak() {
        val result = resultTextView.text.toString()
        if (result.isNotEmpty() && result != getString(R.string.translation_result)) {
            Toast.makeText(this, R.string.tts_coming_soon, Toast.LENGTH_SHORT).show()
        }
    }
    
    private suspend fun translateText(text: String): String = withContext(Dispatchers.IO) {
        try {
            val encodedText = URLEncoder.encode(text, "UTF-8")
            val url = URL("https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=id&dt=t&q=$encodedText")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 10000
            
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            
            parseResponse(response)
        } catch (e: Exception) {
            e.printStackTrace()
            getString(R.string.translation_failed)
        }
    }
    
    private fun parseResponse(response: String): String {
        return try {
            val jsonArray = JSONObject(response).getJSONArray("sentences")
            val sb = StringBuilder()
            for (i in 0 until jsonArray.length()) {
                sb.append(jsonArray.getJSONObject(i).getString("trans"))
            }
            sb.toString()
        } catch (e: Exception) {
            getString(R.string.translation_failed)
        }
    }
    
    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }
    
    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST)
        }
    }
    
    private fun startFloatingService() {
        val intent = Intent(this, FloatingTranslatorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
    
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == OVERLAY_PERMISSION_REQUEST) {
            if (checkOverlayPermission()) {
                startFloatingService()
            }
        }
    }
    
    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}