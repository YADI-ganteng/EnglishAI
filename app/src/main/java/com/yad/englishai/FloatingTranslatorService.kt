package com.yad.englishai

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class FloatingTranslatorService : Service() {
    
    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var translationArea: LinearLayout? = null
    private var inputEditText: EditText? = null
    private var resultTextView: TextView? = null
    private var serviceIntent: Intent? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    
    companion object {
        const val CHANNEL_ID = "translator_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "STOP_SERVICE"
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        serviceIntent = intent
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        setupFloatingWindow()
        return START_STICKY
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Floating Translator",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Floating translator service"
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("EnglishAI")
        .setContentText("Floating translator aktif")
        .setSmallIcon(android.R.drawable.ic_menu_edit)
        .setContentIntent(createPendingIntent())
        .setOngoing(true)
        .build()
    
    private fun createPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
    
    private fun setupFloatingWindow() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        floatingView = inflater.inflate(R.layout.floating_translator, null)
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 100
        
        try {
            windowManager.addView(floatingView, params)
            setupViews()
            setupDragAndDrop(params)
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }
    
    private fun setupViews() {
        inputEditText = floatingView?.findViewById(R.id.inputEditText)
        resultTextView = floatingView?.findViewById(R.id.resultTextView)
        translationArea = floatingView?.findViewById(R.id.translationArea)
        
        floatingView?.findViewById<Button>(R.id.btnTranslate)?.setOnClickListener {
            performTranslation()
        }
        
        floatingView?.findViewById<Button>(R.id.btnCopy)?.setOnClickListener {
            copyResult()
        }
        
        floatingView?.findViewById<Button>(R.id.btnClose)?.setOnClickListener {
            translationArea?.visibility = View.GONE
        }
        
        floatingView?.findViewById<Button>(R.id.btnSpeak)?.setOnClickListener {
            speakResult()
        }
    }
    
    private fun setupDragAndDrop(params: WindowManager.LayoutParams) {
        val dragHandle = floatingView?.findViewById<View>(R.id.dragHandle)
        
        dragHandle?.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_MOVE -> {
                    params.x = event.rawX.toInt() - (floatingView?.width ?: 0) / 2
                    params.y = event.rawY.toInt() - (floatingView?.height ?: 0) / 2
                    try {
                        windowManager.updateViewLayout(floatingView, params)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    true
                }
                else -> false
            }
        }
    }
    
    private fun performTranslation() {
        val text = inputEditText?.text?.toString()?.trim() ?: return
        
        if (text.isEmpty()) {
            Toast.makeText(this, R.string.no_text, Toast.LENGTH_SHORT).show()
            return
        }
        
        translationArea?.visibility = View.VISIBLE
        resultTextView?.text = getString(R.string.translating)
        
        scope.launch {
            val result = translateText(text)
            resultTextView?.text = result
        }
    }
    
    private suspend fun translateText(text: String): String = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=id&dt=t&q=${java.net.URLEncoder.encode(text, "UTF-8")}")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 10000
            
            val response = connection.inputStream.bufferedReader().readText()
            connection.disconnect()
            
            parseTranslationResponse(response)
        } catch (e: Exception) {
            e.printStackTrace()
            getString(R.string.translation_failed)
        }
    }
    
    private fun parseTranslationResponse(response: String): String {
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
    
    private fun copyResult() {
        val result = resultTextView?.text?.toString() ?: return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("translation", result)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
    }
    
    private fun speakResult() {
        val result = resultTextView?.text?.toString() ?: return
        // TODO: Implement text-to-speech
        Toast.makeText(this, "TTS coming soon", Toast.LENGTH_SHORT).show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        if (floatingView != null) {
            try {
                windowManager.removeView(floatingView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}