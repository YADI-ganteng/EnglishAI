package com.yad.englishai

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.Locale

class MainActivity : AppCompatActivity() {
    
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var translator: com.google.mlkit.nl.translate.Translator
    private lateinit var btnStartFloating: Button
    private lateinit var btnStopFloating: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        val inputText = findViewById<EditText>(R.id.inputText)
        val translatedText = findViewById<TextView>(R.id.translatedText)
        val btnTranslate = findViewById<Button>(R.id.btnTranslate)
        val btnSpeak = findViewById<Button>(R.id.btnSpeak)
        val btnCopy = findViewById<Button>(R.id.btnCopy)
        btnStartFloating = findViewById(R.id.btnStartFloating)
        btnStopFloating = findViewById(R.id.btnStopFloating)
        
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.INDONESIAN)
            .setTargetLanguage(TranslateLanguage.ENGLISH)
            .build()
        translator = Translation.getClient(options)
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
        
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) textToSpeech.language = Locale.US
        }
        
        btnTranslate.setOnClickListener {
            val text = inputText.text.toString()
            if (text.isNotEmpty()) {
                translator.translate(text)
                    .addOnSuccessListener { result -> translatedText.text = result }
                    .addOnFailureListener { Toast.makeText(this, "Gagal", Toast.LENGTH_SHORT).show() }
            }
        }
        
        btnSpeak.setOnClickListener {
            val text = translatedText.text.toString()
            if (text.isNotEmpty() && text != "Hasil terjemahan...") {
                textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "speak")
            }
        }
        
        btnCopy.setOnClickListener {
            val text = translatedText.text.toString()
            if (text.isNotEmpty() && text != "Hasil terjemahan...") {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("English", text))
                Toast.makeText(this, "✅ Tersalin!", Toast.LENGTH_SHORT).show()
                translatedText.text = "Hasil terjemahan..."
                inputText.text.clear()
            }
        }
        
        btnStartFloating.setOnClickListener { startFloatingTranslator() }
        btnStopFloating.setOnClickListener {
            stopService(Intent(this, FloatingTranslatorService::class.java))
            updateFloatingButtons()
        }
        
        updateFloatingButtons()
    }
    
    private fun startFloatingTranslator() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                startActivityForResult(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")), 100)
                return
            }
        }
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), 200)
    }
    
    private fun updateFloatingButtons() {
        val running = FloatingTranslatorService.isRunning
        btnStartFloating.isEnabled = !running
        btnStopFloating.isEnabled = running
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 200 && resultCode == RESULT_OK && data != null) {
            val intent = Intent(this, FloatingTranslatorService::class.java)
            intent.putExtra("resultCode", resultCode)
            intent.putExtra("resultData", data)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
            else startService(intent)
            updateFloatingButtons()
        }
    }
    
    override fun onResume() { super.onResume(); updateFloatingButtons() }
    
    override fun onDestroy() {
        super.onDestroy()
        textToSpeech.stop(); textToSpeech.shutdown(); translator.close()
    }
}
