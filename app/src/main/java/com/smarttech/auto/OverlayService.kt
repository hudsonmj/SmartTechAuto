package com.smarttech.auto

import android.app.Service
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

    fun showStatus(text: String) {
        mainHandler.removeCallbacks(hideStatusRunnable)
        tvStatus.text = text
        tvStatus.visibility = View.VISIBLE
        mainHandler.postDelayed(hideStatusRunnable, 2500)
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
            Log.d("OverlayService", "Play button clicked, isLearning=${AutoClickService.isLearning}, isRunning=${AutoClickService.isRunning}")
            if (AutoClickService.isLearning) return@setOnClickListener
            AutoClickService.isRunning = !AutoClickService.isRunning
            setOverlayTouchable(!AutoClickService.isRunning)
            updatePlayPauseButton(btnPlayPause)
            Log.d("OverlayService", "Play button toggled, isRunning now=${AutoClickService.isRunning}")
            if (AutoClickService.isRunning) showStatus("▶ 자동 클릭 시작") else showStatus("⏸ 중지됨")
        }

        btnLearn.setOnClickListener {
            Log.d("OverlayService", "Learn button clicked, isRunning=${AutoClickService.isRunning}, isLearning=${AutoClickService.isLearning}")
            if (AutoClickService.isRunning) return@setOnClickListener
            AutoClickService.isLearning = !AutoClickService.isLearning
            updateLearnButton(btnLearn)
            Log.d("OverlayService", "Learn button toggled, isLearning now=${AutoClickService.isLearning}")
            if (AutoClickService.isLearning) showStatus("🎓 학습중 - 버튼을 탭하세요") else showStatus("⏹ 학습 종료")
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
            btn.text = "⏸ 정지"
            btn.setBackgroundColor(android.graphics.Color.parseColor("#FF9800"))
        } else {
            btn.text = "▶ 시작"
            btn.setBackgroundColor(android.graphics.Color.parseColor("#4CAF50"))
        }
    }

    private fun updateLearnButton(btn: Button) {
        if (AutoClickService.isLearning) {
            btn.text = "⏹ 학습중"
            btn.setBackgroundColor(android.graphics.Color.parseColor("#E91E63"))
        } else {
            btn.text = "🎓 학습"
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
