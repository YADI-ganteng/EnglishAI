package com.yad.englishai

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class FloatingTranslatorService : Service() {
    
    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var translationArea: LinearLayout? = null
    private var inputEditText: EditText? = null
    private var resultTextView: TextView? = null
    private var isDragging = false
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    companion object {
        const val CHANNEL_ID = "translator_channel"
        const val NOTIFICATION_ID = 1
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        setupFloatingWindow()
        return START_STICKY
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Translator", NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("EnglishAI Translator")
        .setContentText("Floating translator aktif")
        .setSmallIcon(android.R.drawable.ic_menu_edit)
        .setOngoing(true)
        .build()
    
    private fun setupFloatingWindow() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        floatingView = inflater.inflate(R.layout.floating_translator, null)
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 50
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
        
        inputEditText?.isFocusable = true
        inputEditText?.isFocusableInTouchMode = true
        
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
            Toast.makeText(this, "TTS coming soon", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun setupDragAndDrop(params: WindowManager.LayoutParams) {
        val dragHandle = floatingView?.findViewById<View>(R.id.dragHandle)
        
        dragHandle?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isDragging = true
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isDragging) {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        try {
                            windowManager.updateViewLayout(floatingView, params)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    isDragging = false
                    true
                }
                else -> true
            }
        }
    }
    
    private fun performTranslation() {
        val text = inputEditText?.text?.toString()?.trim() ?: return
        
        if (text.isEmpty()) {
            Toast.makeText(this, "Masukkan teks dulu", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "Tidak ada koneksi internet", Toast.LENGTH_SHORT).show()
            return
        }
        
        translationArea?.visibility = View.VISIBLE
        resultTextView?.text = "Menerjemahkan..."
        
        scope.launch {
            val result = translateText(text)
            resultTextView?.text = result
        }
    }
    
    private suspend fun translateText(text: String): String = withContext(Dispatchers.IO) {
        try {
            val encodedText = URLEncoder.encode(text, "UTF-8")
            val url = URL("https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=id&dt=t&q=$encodedText")
            
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            
            val responseCode = connection.responseCode
            
            if (responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                parseResponse(response)
            } else {
                "Error: HTTP $responseCode"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "Gagal: ${e.message}"
        }
    }
    
    private fun parseResponse(response: String): String {
        return try {
            val jsonArray = JSONObject(response).getJSONArray("sentences")
            val sb = StringBuilder()
            for (i in 0 until jsonArray.length()) {
                sb.append(jsonArray.getJSONObject(i).getString("trans"))
            }
            sb.toString().trim()
        } catch (e: Exception) {
            "Gagal parse response"
        }
    }
    
    private fun copyResult() {
        val result = resultTextView?.text?.toString() ?: return
        if (result.isEmpty()) return
        
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("translation", result)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Tersalin!", Toast.LENGTH_SHORT).show()
    }
    
    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                   capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = cm.activeNetworkInfo ?: return false
            @Suppress("DEPRECATION")
            return networkInfo.isConnected
        }
    }
    
    override fun onDestroy() {
        scope.cancel()
        if (floatingView != null) {
            try { windowManager.removeView(floatingView) } catch (e: Exception) {}
        }
        super.onDestroy()
    }
}