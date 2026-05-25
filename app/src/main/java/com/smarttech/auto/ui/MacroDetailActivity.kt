package com.smarttech.auto.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.smarttech.auto.AutoClickService
import com.smarttech.auto.R
import com.smarttech.auto.executor.ScriptExecutor
import com.smarttech.auto.model.MacroScript
import com.smarttech.auto.storage.MacroRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MacroDetailActivity : AppCompatActivity() {

    private lateinit var repository: MacroRepository
    private lateinit var macro: MacroScript
    private lateinit var tvName: TextView
    private lateinit var tvTargetApp: TextView
    private lateinit var tvStepCount: TextView
    private lateinit var tvCurrentStep: TextView
    private lateinit var rvSteps: RecyclerView
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var btnRun: MaterialButton

    private var isRunning = false
    private var executorJob: Job? = null
    private var executor: ScriptExecutor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_macro_detail)

        repository = MacroRepository(this)
        val macroId = intent.getStringExtra("macro_id") ?: return
        macro = repository.getById(macroId) ?: return

        title = macro.name
        tvName = findViewById(R.id.tv_macro_name)
        tvTargetApp = findViewById(R.id.tv_target_app)
        tvStepCount = findViewById(R.id.tv_step_count)
        tvCurrentStep = findViewById(R.id.tv_current_step)
        rvSteps = findViewById(R.id.rv_steps)
        progressBar = findViewById(R.id.progress_bar)
        btnRun = findViewById(R.id.btn_run)

        tvName.text = macro.name
        tvTargetApp.text = if (macro.targetAppName.isNotEmpty()) "📱 ${macro.targetAppName}" else "📱 대상 앱 미지정"
        tvStepCount.text = "${macro.steps.size}개 동작"

        rvSteps.layoutManager = LinearLayoutManager(this)
        rvSteps.adapter = StepAdapter(macro.steps)

        btnRun.setOnClickListener {
            if (isRunning) stopMacro() else runMacro()
        }

        findViewById<MaterialButton>(R.id.btn_delete).setOnClickListener {
            repository.delete(macro.id)
            finish()
        }

        if (intent.getBooleanExtra("run", false)) {
            runMacro()
        }
    }

    private fun runMacro() {
        if (!isAccessibilityServiceEnabled(this, AutoClickService::class.java)) {
            Toast.makeText(this, "먼저 접근성 서비스를 활성화해주세요", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            return
        }

        val service = AutoClickService.serviceInstance
        if (service == null) {
            Toast.makeText(this, "오버레이 서비를 먼저 실행해주세요", Toast.LENGTH_LONG).show()
            return
        }

        isRunning = true
        btnRun.text = "⏹ 중지"
        progressBar.visibility = android.view.View.VISIBLE
        tvCurrentStep.visibility = android.view.View.VISIBLE

        executor = ScriptExecutor(service)
        val steps = macro.steps
        executorJob = CoroutineScope(Dispatchers.Main).launch {
            withContext(Dispatchers.IO) {
                executor?.execute(steps) { index, step ->
                    withContext(Dispatchers.Main) {
                        val actionEmoji = when (step.action) {
                            "click" -> "👆"
                            "click_if_exists" -> "🔍"
                            "click_until" -> "🔄"
                            "wait" -> "⏳"
                            "swipe" -> "👉"
                            "scroll" -> "📜"
                            "type" -> "⌨️"
                            "back" -> "🔙"
                            "home" -> "🏠"
                            "launch_app" -> "📱"
                            "loop" -> "🔁"
                            "wait_until" -> "⏰"
                            else -> "▶"
                        }
                        val desc = step.description ?: step.targetValue ?: step.action
                        tvCurrentStep.text = "${actionEmoji} ${index + 1}/${steps.size}: $desc"
                    }
                }
            }
            finishMacro()
        }
    }

    private fun stopMacro() {
        executor?.cancel()
        executorJob?.cancel()
        finishMacro()
        Toast.makeText(this, "⏹ 매크로 중지됨", Toast.LENGTH_SHORT).show()
    }

    private fun finishMacro() {
        isRunning = false
        btnRun.text = "▶ 실행"
        progressBar.visibility = android.view.View.GONE
        tvCurrentStep.visibility = android.view.View.GONE
    }

    private fun isAccessibilityServiceEnabled(context: Context, cls: Class<*>): Boolean {
        val expectedComponent = ComponentName(context, cls)
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        while (splitter.hasNext()) {
            val componentName = ComponentName.unflattenFromString(splitter.next())
            if (componentName == expectedComponent) return true
        }
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        executor?.cancel()
        executorJob?.cancel()
    }
}
