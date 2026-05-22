package com.smarttech.auto

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView

class OverlayService : Service() {

    companion object {
        var instance: OverlayService? = null
        private const val CHANNEL_ID = "SmartTechAutoOverlay"
        private const val NOTIFICATION_ID = 1001
    }

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var tvStatus: TextView
    private val mainHandler = Handler(Looper.getMainLooper())
    private val hideStatusRunnable = Runnable { tvStatus.visibility = View.GONE }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        overlayView = inflater.inflate(R.layout.overlay_view, null)

        val layoutFlag: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 100
        params.y = 100

        windowManager.addView(overlayView, params)

        instance = this
        tvStatus = overlayView.findViewById(R.id.tv_status)

        setupUI()
        setupTouchListener()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "\uC624\uBC84\uB808\uC774 \uC11C\uBE44\uC2A4",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "\uD50C\uB85C\uD305 \uCEE8\uD2B8\uB864\uB7EC \uD45C\uC2DC\uB97C \uC704\uD55C \uC11C\uBE44\uC2A4"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("SmartTech Auto")
            .setContentText("\uD50C\uB85C\uD305 \uCEE8\uD2B8\uB864\uB7EC \uC2E4\uD589 \uC911")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(Notification.PRIORITY_LOW)
            .build()
    }

    fun showStatus(text: String) {
        mainHandler.post {
            tvStatus.text = text
            tvStatus.visibility = View.VISIBLE
            mainHandler.removeCallbacks(hideStatusRunnable)
            mainHandler.postDelayed(hideStatusRunnable, 2500)
        }
    }

    private fun setupUI() {
        val btnPlayPause = overlayView.findViewById<Button>(R.id.btn_play_pause)
        val btnLearn = overlayView.findViewById<Button>(R.id.btn_learn)
        val btnViewIds = overlayView.findViewById<Button>(R.id.btn_view_ids)
        val btnSettings = overlayView.findViewById<Button>(R.id.btn_settings)
        val btnClose = overlayView.findViewById<Button>(R.id.btn_close)

        updatePlayPauseButton(btnPlayPause)
        updateLearnButton(btnLearn)

        btnPlayPause.setOnClickListener {
            if (AutoClickService.isLearning) return@setOnClickListener
            AutoClickService.isRunning = !AutoClickService.isRunning
            updatePlayPauseButton(btnPlayPause)
            if (AutoClickService.isRunning) {
                showStatus("\u25B6 \uC790\uB3D9 \uD074\uB9AD \uC2DC\uC791")
                AutoClickService.serviceInstance?.triggerAutoClickOnCurrentScreen()
            } else {
                showStatus("\u23F8 \uC911\uC9C0\uB428")
            }
        }

        btnLearn.setOnClickListener {
            if (AutoClickService.isRunning) return@setOnClickListener
            AutoClickService.isLearning = !AutoClickService.isLearning
            updateLearnButton(btnLearn)
            if (AutoClickService.isLearning) showStatus("\uD83C\uDF93 \uD559\uC2B5\uC911 - \uBC84\uD2BC\uC744 \uD0ED\uD558\uC138\uC694") else showStatus("\u23F9 \uD559\uC2B5 \uC885\uB8CC")
        }

        btnViewIds.setOnClickListener {
            val intent = Intent(this, ManageTargetsActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }

        btnSettings.setOnClickListener {
            val intent = Intent(this, AppSettingsActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }

        btnClose.setOnClickListener {
            stopSelf()
        }
    }

    private fun updatePlayPauseButton(btn: Button) {
        if (AutoClickService.isRunning) {
            btn.text = "\u23F8 \uC815\uC9C0"
            btn.setBackgroundColor(android.graphics.Color.parseColor("#FF9800"))
        } else {
            btn.text = "\u25B6 \uC2DC\uC791"
            btn.setBackgroundColor(android.graphics.Color.parseColor("#4CAF50"))
        }
    }

    private fun updateLearnButton(btn: Button) {
        if (AutoClickService.isLearning) {
            btn.text = "\u23F9 \uD559\uC2B5\uC911"
            btn.setBackgroundColor(android.graphics.Color.parseColor("#E91E63"))
        } else {
            btn.text = "\uD83C\uDF93 \uD559\uC2B5"
            btn.setBackgroundColor(android.graphics.Color.parseColor("#9C27B0"))
        }
    }

    fun setOverlayTouchable(touchable: Boolean) {
        params.flags = if (touchable) {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        windowManager.updateViewLayout(overlayView, params)
    }

    private fun setupTouchListener() {
        overlayView.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val diffX = (event.rawX - initialTouchX).toInt()
                        val diffY = (event.rawY - initialTouchY).toInt()
                        if (Math.abs(diffX) < 10 && Math.abs(diffY) < 10) {
                            v.performClick()
                        }
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(overlayView, params)
                        return true
                    }
                }
                return false
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        if (::overlayView.isInitialized) {
            windowManager.removeView(overlayView)
        }
        AutoClickService.isRunning = false
        AutoClickService.isLearning = false
    }
}
