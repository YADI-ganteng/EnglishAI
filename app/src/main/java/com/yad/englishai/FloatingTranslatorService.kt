package com.yad.englishai

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import android.speech.tts.TextToSpeech
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
import java.util.Locale

/**
 * FloatingTranslatorService - Layanan penerjemah mengambang
 * 
 * Fitur:
 * - Floating window dengan drag & drop
 * - Terjemahan otomatis (auto-detect ke Indonesia)
 * - Copy hasil ke clipboard
 * - Text-to-Speech (coming soon)
 * - Notification dengan stop action
 */
class FloatingTranslatorService : Service(), TextToSpeech.OnInitListener {
    
    // === UI Components ===
    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var translationArea: LinearLayout? = null
    private var inputEditText: EditText? = null
    private var resultTextView: TextView? = null
    
    // === Service Components ===
    private var serviceIntent: Intent? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // === Text-to-Speech ===
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    
    companion object {
        const val CHANNEL_ID = "translator_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "STOP_SERVICE"
        const val TRANSLATE_URL = "https://translate.googleapis.com/translate_a/single"
        
        // Translation parameters
        const val TIMEOUT_CONNECT = 5000
        const val TIMEOUT_READ = 10000
        const val MAX_TEXT_LENGTH = 5000
    }
    
    // === Lifecycle Methods ===
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onCreate() {
        super.onCreate()
        initializeTextToSpeech()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        serviceIntent = intent
        
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        setupFloatingWindow()
        
        return START_STICKY
    }
    
    override fun onDestroy() {
        serviceScope.cancel()
        tts?.stop()
        tts?.shutdown()
        removeFloatingView()
        super.onDestroy()
    }
    
    // === Text-to-Speech ===
    private fun initializeTextToSpeech() {
        tts = TextToSpeech(this, this)
    }
    
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            isTtsReady = result != TextToSpeech.LANG_MISSING_DATA && 
                        result != TextToSpeech.LANG_NOT_SUPPORTED
        }
    }
    
    // === Notification ===
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_title),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_text)
            }
            
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle(getString(R.string.notification_title))
        .setContentText(getString(R.string.notification_text))
        .setSmallIcon(android.R.drawable.ic_menu_edit)
        .setContentIntent(createMainActivityPendingIntent())
        .addAction(0, getString(R.string.notification_stop), createStopServicePendingIntent())
        .setOngoing(true)
        .build()
    
    private fun createMainActivityPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
    
    private fun createStopServicePendingIntent(): PendingIntent {
        val intent = Intent(this, FloatingTranslatorService::class.java).apply {
            action = ACTION_STOP
        }
        return PendingIntent.getService(
            this, 1, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
    
    // === Floating Window Setup ===
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
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 100
        }
        
        try {
            windowManager.addView(floatingView, params)
            setupViewReferences()
            setupButtonListeners()
            setupDragHandle(params)
            showToast(R.string.service_started)
        } catch (e: SecurityException) {
            showToast(R.string.network_error)
            stopSelf()
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }
    
    private fun setupViewReferences() {
        inputEditText = floatingView?.findViewById(R.id.inputEditText)
        resultTextView = floatingView?.findViewById(R.id.resultTextView)
        translationArea = floatingView?.findViewById(R.id.translationArea)
    }
    
    private fun setupButtonListeners() {
        floatingView?.findViewById<Button>(R.id.btnTranslate)?.setOnClickListener {
            handleTranslateClick()
        }
        
        floatingView?.findViewById<Button>(R.id.btnCopy)?.setOnClickListener {
            handleCopyClick()
        }
        
        floatingView?.findViewById<Button>(R.id.btnClose)?.setOnClickListener {
            handleCloseClick()
        }
        
        floatingView?.findViewById<Button>(R.id.btnSpeak)?.setOnClickListener {
            handleSpeakClick()
        }
    }
    
    private fun setupDragHandle(params: WindowManager.LayoutParams) {
        val dragHandle = floatingView?.findViewById<View>(R.id.dragHandle)
        
        dragHandle?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_MOVE -> {
                    updateFloatingViewPosition(params, event)
                    true
                }
                else -> false
            }
        }
    }
    
    private fun updateFloatingViewPosition(params: WindowManager.LayoutParams, event: MotionEvent) {
        val viewWidth = floatingView?.width ?: 0
        val viewHeight = floatingView?.height ?: 0
        
        params.x = event.rawX.toInt() - viewWidth / 2
        params.y = event.rawY.toInt() - viewHeight / 2
        
        try {
            windowManager.updateViewLayout(floatingView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    // === Button Handlers ===
    private fun handleTranslateClick() {
        val text = inputEditText?.text?.toString()?.trim() ?: ""
        
        when {
            text.isEmpty() -> showToast(R.string.no_text)
            text.length > MAX_TEXT_LENGTH -> showToast(R.string.translation_failed)
            !isNetworkAvailable() -> showToast(R.string.network_error)
            else -> performTranslation(text)
        }
    }
    
    private fun handleCopyClick() {
        val result = resultTextView?.text?.toString() ?: return
        
        if (result.isEmpty() || result == getString(R.string.translation_result)) {
            showToast(R.string.no_text)
            return
        }
        
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("translation", result)
        clipboard.setPrimaryClip(clip)
        showToast(R.string.copied)
    }
    
    private fun handleCloseClick() {
        translationArea?.visibility = View.GONE
    }
    
    private fun handleSpeakClick() {
        val result = resultTextView?.text?.toString() ?: return
        
        if (isTtsReady && result.isNotEmpty()) {
            tts?.speak(result, TextToSpeech.QUEUE_FLUSH, null, "translation_tts")
        } else {
            showToast(R.string.tts_coming_soon)
        }
    }
    
    // === Translation Logic ===
    private fun performTranslation(text: String) {
        translationArea?.visibility = View.VISIBLE
        resultTextView?.setText(R.string.translating)
        
        serviceScope.launch {
            val result = translateTextAsync(text)
            resultTextView?.text = result
        }
    }
    
    private suspend fun translateTextAsync(text: String): String = withContext(Dispatchers.IO) {
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
            
            parseTranslationResponse(response)
        } catch (e: Exception) {
            e.printStackTrace()
            getString(R.string.translation_failed)
        }
    }
    
    private fun parseTranslationResponse(response: String): String {
        return try {
            val jsonArray = JSONObject(response).getJSONArray("sentences")
            val result = StringBuilder()
            
            for (i in 0 until jsonArray.length()) {
                result.append(jsonArray.getJSONObject(i).getString("trans"))
            }
            
            result.toString().trim()
        } catch (e: Exception) {
            getString(R.string.translation_failed)
        }
    }
    
    // === Network Check ===
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            
            return when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                else -> false
            }
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo ?: return false
            @Suppress("DEPRECATION")
            return networkInfo.isConnected
        }
    }
    
    // === Utility ===
    private fun showToast(messageResId: Int) {
        Toast.makeText(this, messageResId, Toast.LENGTH_SHORT).show()
    }
    
    private fun removeFloatingView() {
        if (floatingView != null) {
            try {
                windowManager.removeView(floatingView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            floatingView = null
        }
    }
}