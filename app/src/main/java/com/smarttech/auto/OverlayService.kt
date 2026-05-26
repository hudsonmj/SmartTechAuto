package com.smarttech.auto

import android.accessibilityservice.GestureDescription
import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.util.Log
import com.smarttech.auto.executor.LogBuffer
import com.smarttech.auto.executor.ScriptExecutor
import com.smarttech.auto.model.ActionStep
import com.smarttech.auto.model.MacroScript
import com.smarttech.auto.model.RecordedAction
import com.smarttech.auto.storage.MacroRepository
import com.smarttech.auto.ui.RecordingReviewActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OverlayService : Service() {

    companion object {
        var instance: OverlayService? = null
        private const val CHANNEL_ID = "SmartTechAutoOverlay"
        private const val NOTIFICATION_ID = 1001

        fun toggleRecording() {
            instance?.let { svc ->
                if (AutoClickService.isRecording) {
                    svc.stopRecording()
                } else {
                    svc.startRecording()
                }
            }
        }
    }

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var captureOverlay: View
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var tvStatus: TextView
    private lateinit var layoutRecording: View
    private lateinit var tvRecIndicator: TextView
    private lateinit var layoutCreate: View
    private lateinit var tvCoord: TextView
    private lateinit var tvNumber: TextView
    private lateinit var tvStepCount: TextView
    private lateinit var btnCapture: Button
    private lateinit var btnActionClick: Button
    private lateinit var btnActionHold: Button
    private lateinit var btnNumMinus: Button
    private lateinit var btnNumPlus: Button
    private lateinit var btnModeCount: Button
    private lateinit var btnModeSec: Button
    private lateinit var btnAddStep: Button
    private lateinit var btnSaveScript: Button
    private lateinit var cbAutoClose: CheckBox

    private var isCaptureOverlayShowing = false
    private var recBlinkCount = 0
    private val recBlinkRunnable = object : Runnable {
        override fun run() {
            recBlinkCount++
            tvRecIndicator.text = if (recBlinkCount % 2 == 0) "🔴 ● REC ●" else "🔴   REC   "
            mainHandler.postDelayed(this, 800)
        }
    }

    private var capturedX: Int? = null
    private var capturedY: Int? = null
    private var selectedAction = "click"
    private var numberValue = 1
    private var isCountMode = true
    private val createdSteps = mutableListOf<ActionStep>()
    private var isCreateMode = false
    private var previewOverlay: View? = null
    private lateinit var macroRepository: MacroRepository

    private val mainHandler = Handler(Looper.getMainLooper())
    private val hideStatusRunnable = Runnable { tvStatus.visibility = View.GONE }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        try {
            createNotificationChannel()
            try {
                startForeground(NOTIFICATION_ID, buildNotification())
            } catch (_: Exception) {}
            initOverlay()
        } catch (e: Exception) {
            Log.e("OverlayService", "onCreate failed", e)
        }
    }

    private fun initOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        overlayView = inflater.inflate(R.layout.overlay_view, null)
        captureOverlay = View(this).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }

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
        macroRepository = MacroRepository(this)

        tvStatus = overlayView.findViewById(R.id.tv_status)
        layoutRecording = overlayView.findViewById(R.id.layout_recording)
        tvRecIndicator = overlayView.findViewById(R.id.tv_rec_indicator)
        layoutCreate = overlayView.findViewById(R.id.layout_create)
        tvCoord = overlayView.findViewById(R.id.tv_coord)
        tvNumber = overlayView.findViewById(R.id.tv_number)
        tvStepCount = overlayView.findViewById(R.id.tv_step_count)
        btnCapture = overlayView.findViewById(R.id.btn_capture)
        btnActionClick = overlayView.findViewById(R.id.btn_action_click)
        btnActionHold = overlayView.findViewById(R.id.btn_action_hold)
        btnNumMinus = overlayView.findViewById(R.id.btn_num_minus)
        btnNumPlus = overlayView.findViewById(R.id.btn_num_plus)
        btnModeCount = overlayView.findViewById(R.id.btn_mode_count)
        btnModeSec = overlayView.findViewById(R.id.btn_mode_sec)
        btnAddStep = overlayView.findViewById(R.id.btn_add_step)
        btnSaveScript = overlayView.findViewById(R.id.btn_save_script)
        cbAutoClose = overlayView.findViewById(R.id.cb_auto_close)

        val prefs = getSharedPreferences("smarttech_auto", Context.MODE_PRIVATE)
        cbAutoClose.isChecked = prefs.getBoolean("auto_close_popups", true)
        cbAutoClose.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("auto_close_popups", isChecked).apply()
            AutoClickService.autoClosePopups = isChecked
        }
        AutoClickService.autoClosePopups = cbAutoClose.isChecked

        setupUI()
        setupCreateUI()
        updateActionButtons()
        updateModeButtons()
        updateNumberDisplay()
        setupTouchListener()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "오버레이 서비스",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "플로팅 컨트롤러 표시를 위한 서비스"
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
            .setContentText("플로팅 컨트롤러 실행 중")
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
            mainHandler.postDelayed(hideStatusRunnable, 3000)
        }
    }

    private fun setupUI() {
        val btnPlayPause = overlayView.findViewById<Button>(R.id.btn_play_pause)
        val btnRecord = overlayView.findViewById<Button>(R.id.btn_record)
        val btnCreate = overlayView.findViewById<Button>(R.id.btn_create)
        val btnMacros = overlayView.findViewById<Button>(R.id.btn_macros)
        val btnLog = overlayView.findViewById<Button>(R.id.btn_log)
        val btnClose = overlayView.findViewById<Button>(R.id.btn_close)
        val btnStopRecording = overlayView.findViewById<Button>(R.id.btn_stop_recording)

        updatePlayPauseButton(btnPlayPause)

        btnPlayPause.setOnClickListener {
            if (AutoClickService.isRecording) return@setOnClickListener
            AutoClickService.isRunning = !AutoClickService.isRunning
            updatePlayPauseButton(btnPlayPause)
            if (AutoClickService.isRunning) {
                showStatus("▶ 자동 클릭 시작")
                AutoClickService.serviceInstance?.triggerAutoClickOnCurrentScreen()
            } else {
                showStatus("⏸ 중지됨")
            }
        }

        btnRecord.setOnClickListener {
            if (AutoClickService.isRunning) return@setOnClickListener
            if (isCreateMode) return@setOnClickListener
            toggleRecording()
        }

        btnCreate.setOnClickListener {
            if (AutoClickService.isRecording) return@setOnClickListener
            if (AutoClickService.isRunning) return@setOnClickListener
            isCreateMode = !isCreateMode
            layoutCreate.visibility = if (isCreateMode) View.VISIBLE else View.GONE
            if (isCreateMode) {
                layoutRecording.visibility = View.GONE
                if (AutoClickService.isRecording) stopRecording()
                showStatus("🛠 제작 모드")
            }
        }

        btnStopRecording.setOnClickListener {
            stopRecording()
        }

        btnMacros.setOnClickListener {
            showMacroPopup()
        }

        btnLog.setOnClickListener {
            val logText = LogBuffer.getText()
            if (logText.isEmpty()) {
                showStatus("📋 로그가 없습니다")
            } else {
                val ctx = this

                // ── Settings button ──
                fun showSettingsDialog() {
                    val emailPrefill = MailSender.getEmail(ctx)
                    val etEmail = android.widget.EditText(ctx).apply {
                        setText(emailPrefill)
                        hint = "Gmail 주소"
                        inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                    }
                    val etPass = android.widget.EditText(ctx).apply {
                        hint = "앱 비밀번호"
                        inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                    }
                    val settingsLayout = LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(48, 16, 48, 16)
                        addView(etEmail)
                        etPass.layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { setMargins(0, 12, 0, 0) }
                        addView(etPass)
                    }
                    AlertDialog.Builder(ctx)
                        .setTitle("⚙ Gmail 설정")
                        .setMessage("Gmail 앱 비밀번호가 필요합니다\n(Gmail 설정 → 보안 → 앱 비밀번호)")
                        .setView(settingsLayout)
                        .setPositiveButton("저장") { _, _ ->
                            val e = etEmail.text.toString().trim()
                            val p = etPass.text.toString().trim()
                            if (e.isNotBlank() && p.isNotBlank()) {
                                MailSender.saveCredentials(ctx, e, p)
                                showStatus("✅ Gmail 계정 저장 완료")
                            } else {
                                showStatus("⚠ 이메일과 비밀번호를 입력해주세요")
                            }
                        }
                        .setNegativeButton("취소", null)
                        .create()
                        .also { d ->
                            d.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
                            d.show()
                        }
                }

                // ── Log text ──
                val textView = TextView(ctx).apply {
                    text = logText
                    textSize = 10f
                    typeface = android.graphics.Typeface.MONOSPACE
                    setTextColor(Color.WHITE)
                    setBackgroundColor(Color.parseColor("#AA000000"))
                    setPadding(24, 24, 24, 24)
                    setTextIsSelectable(true)
                    setMaxHeight(600)
                }

                // ── Button row ──
                val clearBtn = Button(ctx).apply {
                    text = "🗑 지우기"
                    setTextColor(Color.WHITE)
                    setBackgroundColor(Color.parseColor("#F44336"))
                    setOnClickListener {
                        LogBuffer.clear()
                        textView.text = "(로그 지워짐)"
                    }
                }
                val emailBtn = Button(ctx)
                emailBtn.text = if (MailSender.isConfigured(ctx)) "✉ 메일보내기" else "✉ 메일보내기 (설정필요)"
                emailBtn.setTextColor(Color.WHITE)
                emailBtn.setBackgroundColor(Color.parseColor("#2196F3"))
                emailBtn.setOnClickListener {
                    if (!MailSender.isConfigured(ctx)) {
                        showSettingsDialog()
                        return@setOnClickListener
                    }
                    emailBtn.isEnabled = false
                    emailBtn.text = "⏳ 보내는 중..."
                    val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date())
                    MailSender.sendLogAsync(
                        context = ctx,
                        to = "hudsonmj@nate.com",
                        subject = "로그 ($dateStr)",
                        body = LogBuffer.getText()
                    ) { success, msg ->
                        emailBtn.isEnabled = true
                        emailBtn.text = "✉ 메일보내기"
                        showStatus(if (success) "✅ $msg" else "⚠ $msg")
                    }
                }
                val settingsBtn = Button(ctx).apply {
                    text = "⚙"
                    setTextColor(Color.WHITE)
                    setBackgroundColor(Color.parseColor("#757575"))
                    setOnClickListener { showSettingsDialog() }
                }
                val btnRow = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(clearBtn)
                    emailBtn.layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(8, 0, 0, 0) }
                    addView(emailBtn)
                    settingsBtn.layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(8, 0, 0, 0) }
                    addView(settingsBtn)
                }

                val layout = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(textView)
                    addView(btnRow)
                }
                val dialog = AlertDialog.Builder(ctx)
                    .setTitle("📋 실행 로그")
                    .setView(layout)
                    .setPositiveButton("닫기", null)
                    .create()
                dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
                dialog.show()
            }
        }

        btnClose.setOnClickListener {
            hideCaptureOverlay()
            stopSelf()
        }
    }

    private fun setupCreateUI() {
        btnCapture.setOnClickListener {
            showCaptureOverlay()
            showStatus("📍 화면을 터치하세요")
        }

        btnActionClick.setOnClickListener {
            selectedAction = "click"
            updateActionButtons()
        }

        btnActionHold.setOnClickListener {
            selectedAction = "hold"
            updateActionButtons()
        }

        btnNumMinus.setOnClickListener {
            if (numberValue > 1) {
                numberValue--
                updateNumberDisplay()
            }
        }

        btnNumPlus.setOnClickListener {
            numberValue++
            updateNumberDisplay()
        }

        btnModeCount.setOnClickListener {
            isCountMode = true
            updateModeButtons()
        }

        btnModeSec.setOnClickListener {
            isCountMode = false
            updateModeButtons()
        }

        btnAddStep.setOnClickListener {
            addStep()
        }

        btnSaveScript.setOnClickListener {
            saveScript()
        }
    }

    private fun updateActionButtons() {
        setButtonSelected(btnActionClick, selectedAction == "click", "#4CAF50")
        setButtonSelected(btnActionHold, selectedAction == "hold", "#4CAF50")
    }

    private fun updateModeButtons() {
        setButtonSelected(btnModeCount, isCountMode, "#FF9800")
        setButtonSelected(btnModeSec, !isCountMode, "#FF9800")
    }

    private fun setButtonSelected(btn: Button, selected: Boolean, activeColor: String) {
        val color = if (selected) android.graphics.Color.parseColor(activeColor)
                    else android.graphics.Color.parseColor("#555555")
        val drawable = GradientDrawable().apply {
            setColor(color)
            cornerRadius = 6f
        }
        btn.background = drawable
        val clean = btn.text.toString().replace("● ", "").replace("○ ", "")
        btn.text = (if (selected) "● " else "○ ") + clean
    }

    private fun updateNumberDisplay() {
        tvNumber.text = numberValue.toString()
    }

    private fun addStep() {
        val x = capturedX ?: run {
            showStatus("⚠ 좌표를 먼저 지정하세요")
            return
        }
        val y = capturedY ?: return

        val step = if (selectedAction == "click") {
            ActionStep(
                action = "click",
                targetType = "coordinate",
                x = x,
                y = y,
                count = if (isCountMode) numberValue else null,
                ms = if (!isCountMode) numberValue * 1000L else null,
                description = "클릭 (${x},${y})" + if (isCountMode) " ${numberValue}회" else " ${numberValue}초"
            )
        } else {
            ActionStep(
                action = "hold",
                targetType = "coordinate",
                x = x,
                y = y,
                count = if (isCountMode) numberValue else null,
                ms = if (!isCountMode) numberValue * 1000L else null,
                description = "길게누르기 (${x},${y})" + if (isCountMode) " ${numberValue}회" else " ${numberValue}초"
            )
        }

        createdSteps.add(step)
        tvStepCount.text = "${createdSteps.size}개"
        showStatus("✅ 스텝 추가됨 (${createdSteps.size}개)")

        capturedX = null
        capturedY = null
        tvCoord.text = "📍(_,_)"
    }

    private fun saveScript() {
        if (createdSteps.isEmpty()) {
            showStatus("⚠ 추가된 스텝이 없습니다")
            return
        }

        mainHandler.post {
            val editText = EditText(this).apply {
                hint = "매크로 이름"
            }

            val dialog = AlertDialog.Builder(this)
                .setTitle("💾 매크로 저장")
                .setMessage("${createdSteps.size}개 스텝을 저장합니다")
                .setView(editText)
                .setPositiveButton("저장") { _, _ ->
                    val name = editText.text.toString().trim()
                    if (name.isNotEmpty()) {
                        val script = MacroScript(
                            name = name,
                            steps = createdSteps.toList()
                        )
                        macroRepository.save(script)
                        createdSteps.clear()
                        tvStepCount.text = "0개"
                        capturedX = null
                        capturedY = null
                        tvCoord.text = "📍(_,_)"
                        showStatus("✅ '${name}' 저장됨")
                    } else {
                        showStatus("⚠ 이름을 입력하세요")
                    }
                }
                .setNegativeButton("취소", null)
                .create()

            dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            dialog.show()
        }
    }

    private fun showMacroPopup() {
        val macros = macroRepository.getAll()
        if (macros.isEmpty()) {
            showStatus("📋 저장된 매크로가 없습니다")
            return
        }

        mainHandler.post {
            val names = macros.map { it.name }.toTypedArray()
            var selectedMacro = macros[0]

            showPreviewOverlay(selectedMacro.steps)

            val dialog = AlertDialog.Builder(this)
                .setTitle("📋 매크로 선택")
                .setSingleChoiceItems(names, 0) { _, which ->
                    selectedMacro = macros[which]
                    showPreviewOverlay(selectedMacro.steps)
                }
                .setPositiveButton("▶ 시작") { _, _ ->
                    hidePreviewOverlay()
                    runSelectedMacro(selectedMacro)
                }
                .setNegativeButton("취소") { _, _ ->
                    hidePreviewOverlay()
                }
                .setOnDismissListener {
                    hidePreviewOverlay()
                }
                .create()

            dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            dialog.show()
        }
    }

    private fun runSelectedMacro(macro: com.smarttech.auto.model.MacroScript) {
        val service = AutoClickService.serviceInstance
        if (service == null) {
            showStatus("⚠ 오버레이 서비스가 없습니다")
            return
        }

        showStatus("▶ '${macro.name}' 실행 (${macro.steps.size}단계)")
        val executor = ScriptExecutor(service)
        CoroutineScope(Dispatchers.Main).launch {
            withContext(Dispatchers.IO) {
                executor.execute(macro.steps) { index, step ->
                    val desc = step.description ?: step.targetValue ?: step.action
                    showStatus("▶ ${index + 1}/${macro.steps.size}: $desc")
                }
            }
        }
    }

    private fun showPreviewOverlay(steps: List<ActionStep>) {
        hidePreviewOverlay()
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_PHONE

        val view = object : View(this) {
            private val circleFill = Paint().apply {
                color = Color.parseColor("#33FF5722"); style = Paint.Style.FILL; isAntiAlias = true
            }
            private val circleStroke = Paint().apply {
                color = Color.parseColor("#FF5722"); style = Paint.Style.STROKE
                strokeWidth = 4f; isAntiAlias = true
            }
            private val labelBg = Paint().apply {
                color = Color.parseColor("#CC000000"); style = Paint.Style.FILL; isAntiAlias = true
            }
            private val textPaint = Paint().apply {
                color = Color.WHITE; textSize = 36f; isAntiAlias = true; isFakeBoldText = true
            }
            private val coordPaint = Paint().apply {
                color = Color.parseColor("#FFFFFF"); textSize = 28f; isAntiAlias = true
            }
            private val actionPaint = Paint().apply {
                color = Color.parseColor("#FFCC00"); textSize = 28f; isAntiAlias = true; isFakeBoldText = true
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                for ((i, step) in steps.withIndex()) {
                    val x = step.x ?: continue
                    val y = step.y ?: continue
                    val radius = 55f

                    canvas.drawCircle(x.toFloat(), y.toFloat(), radius, circleFill)
                    canvas.drawCircle(x.toFloat(), y.toFloat(), radius, circleStroke)

                    val label = "${i + 1}"
                    val tw = textPaint.measureText(label)
                    val lx = x.toFloat() + radius - 4f
                    val ly = y.toFloat() - radius + 4f
                    canvas.drawRoundRect(lx, ly, lx + tw + 24f, ly + 44f, 8f, 8f, labelBg)
                    canvas.drawText(label, lx + 12f, ly + 32f, textPaint)

                    val coordStr = "(${step.x}, ${step.y})"
                    canvas.drawText(coordStr, x.toFloat() - radius, y.toFloat() + radius + 40f, coordPaint)

                    val actionStr = if (step.action == "hold") "길게" else "클릭"
                    canvas.drawText(actionStr, x.toFloat() - radius, y.toFloat() + radius + 70f, actionPaint)
                }
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSPARENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        windowManager.addView(view, params)
        previewOverlay = view
    }

    private fun hidePreviewOverlay() {
        previewOverlay?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
            previewOverlay = null
        }
    }

    private fun startRecording() {
        AutoClickService.isRecording = true
        AutoClickService.recordedActions.clear()
        layoutRecording.visibility = View.VISIBLE
        recBlinkCount = 0
        mainHandler.post(recBlinkRunnable)
        showCaptureOverlay()
        showStatus("🔴 녹화 시작 - 화면을 터치하세요")
    }

    private fun stopRecording() {
        AutoClickService.isRecording = false
        layoutRecording.visibility = View.GONE
        mainHandler.removeCallbacks(recBlinkRunnable)
        tvRecIndicator.visibility = View.VISIBLE
        hideCaptureOverlay()

        showStatus("⏹ 녹화 완료")
        mainHandler.postDelayed({
            val actions = AutoClickService.recordedActions.toList()
            if (actions.isNotEmpty()) {
                val intent = Intent(this, RecordingReviewActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } else {
                showStatus("캡처된 동작이 없습니다")
            }
        }, 500)
    }

    private fun showCaptureOverlay() {
        if (isCaptureOverlayShowing) return
        isCaptureOverlayShowing = true

        val layoutFlag: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val capParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSPARENT
        )
        capParams.gravity = Gravity.TOP or Gravity.START

        captureOverlay.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                onCaptureTouch(event.rawX.toInt(), event.rawY.toInt())
                true
            } else false
        }

        windowManager.addView(captureOverlay, capParams)
    }

    private fun hideCaptureOverlay() {
        if (!isCaptureOverlayShowing) return
        isCaptureOverlayShowing = false
        try { windowManager.removeView(captureOverlay) } catch (_: Exception) {}
    }

    private fun onCaptureTouch(x: Int, y: Int) {
        hideCaptureOverlay()

        if (isCreateMode) {
            capturedX = x
            capturedY = y
            tvCoord.text = "📍(${x},${y})"
            showStatus("✅ 좌표 (${x},${y}) 지정됨")
            return
        }

        val action = RecordedAction(
            type = RecordedAction.Type.TOUCH,
            x = x,
            y = y
        )
        AutoClickService.recordedActions.add(action)

        val service = AutoClickService.serviceInstance
        if (service != null) {
            val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0L, 50L))
                .build()
            service.dispatchGesture(gesture, null, null)
        }

        showDescriptionDialog(action)
    }

    private fun showDescriptionDialog(action: RecordedAction) {
        mainHandler.post {
            val hint = "이 동작 설명 (예: 좌표 ${action.x}, ${action.y})"
            val editText = EditText(this).apply {
                this.hint = hint
            }

            val dialog = AlertDialog.Builder(this)
                .setTitle("👆 ${AutoClickService.recordedActions.size}번째 동작")
                .setMessage("좌표 (${action.x}, ${action.y})")
                .setView(editText)
                .setPositiveButton("확인") { _, _ ->
                    val desc = editText.text.toString().trim()
                    val lastIdx = AutoClickService.recordedActions.size - 1
                    if (lastIdx >= 0 && desc.isNotEmpty()) {
                        val updated = AutoClickService.recordedActions[lastIdx].copy(note = desc)
                        AutoClickService.recordedActions[lastIdx] = updated
                    }
                    showCaptureOverlay()
                    showStatus("✅ 저장됨")
                }
                .setNegativeButton("건너뛰기") { _, _ ->
                    showCaptureOverlay()
                }
                .setNeutralButton("⏹ 중지") { _, _ ->
                    stopRecording()
                }
                .setOnCancelListener {
                    showCaptureOverlay()
                }
                .create()

            dialog.setCanceledOnTouchOutside(true)
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            dialog.show()
        }
    }

    private fun updatePlayPauseButton(btn: Button) {
        val color = if (AutoClickService.isRunning) "#FF9800" else "#4CAF50"
        btn.text = if (AutoClickService.isRunning) "⏸ 정지" else "▶ 시작"
        val drawable = GradientDrawable().apply {
            setColor(android.graphics.Color.parseColor(color))
            cornerRadius = 6f
        }
        btn.background = drawable
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
        mainHandler.removeCallbacks(recBlinkRunnable)
        hideCaptureOverlay()
        if (::overlayView.isInitialized) {
            windowManager.removeView(overlayView)
        }
        AutoClickService.isRunning = false
        AutoClickService.isRecording = false
        createdSteps.clear()
    }
}
