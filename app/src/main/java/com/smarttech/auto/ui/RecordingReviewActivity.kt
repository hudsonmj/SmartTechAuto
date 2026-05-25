package com.smarttech.auto.ui

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.smarttech.auto.AutoClickService
import com.smarttech.auto.R
import com.smarttech.auto.ai.GeminiClient
import com.smarttech.auto.model.ActionStep
import com.smarttech.auto.model.MacroScript
import com.smarttech.auto.model.RecordedAction
import com.smarttech.auto.storage.MacroRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RecordingReviewActivity : AppCompatActivity() {

    private lateinit var repository: MacroRepository
    private lateinit var rvRecorded: RecyclerView
    private lateinit var etNote: TextInputEditText
    private lateinit var etApiKey: TextInputEditText
    private lateinit var btnAddNote: MaterialButton
    private lateinit var btnOptimize: MaterialButton
    private lateinit var btnSave: MaterialButton
    private lateinit var progressBar: ProgressBar
    private lateinit var tvError: TextView
    private lateinit var tvEmpty: TextView

    private val recordedActions = mutableListOf<RecordedAction>()
    private var adapter: RecordedActionAdapter? = null
    private var generatedSteps: List<ActionStep>? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recording_review)
        title = "🎬 녹화 검토"

        repository = MacroRepository(this)

        recordedActions.clear()
        recordedActions.addAll(AutoClickService.recordedActions)

        rvRecorded = findViewById(R.id.rv_recorded)
        etNote = findViewById(R.id.et_note)
        etApiKey = findViewById(R.id.et_api_key)
        btnAddNote = findViewById(R.id.btn_add_note)
        btnOptimize = findViewById(R.id.btn_optimize)
        btnSave = findViewById(R.id.btn_save)
        progressBar = findViewById(R.id.progress_bar)
        tvError = findViewById(R.id.tv_error)
        tvEmpty = findViewById(R.id.tv_empty)

        rvRecorded.layoutManager = LinearLayoutManager(this)
        adapter = RecordedActionAdapter(recordedActions)
        rvRecorded.adapter = adapter

        if (recordedActions.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
        }

        val savedKey = repository.getApiKey()
        if (savedKey.isNotEmpty()) {
            etApiKey.setText(savedKey)
        }

        btnAddNote.setOnClickListener {
            addNote()
        }

        btnOptimize.setOnClickListener {
            optimizeWithAI()
        }

        btnSave.setOnClickListener {
            saveScript()
        }
    }

    private fun addNote() {
        val text = etNote.text?.toString()?.trim()
        if (text.isNullOrEmpty()) return

        recordedActions.add(RecordedAction(type = RecordedAction.Type.NOTE, note = text))
        adapter?.notifyItemInserted(recordedActions.size - 1)
        rvRecorded.smoothScrollToPosition(recordedActions.size - 1)
        etNote.text?.clear()
        tvEmpty.visibility = View.GONE
    }

    private fun optimizeWithAI() {
        val apiKey = etApiKey.text?.toString()?.trim()
        if (apiKey.isNullOrEmpty()) {
            tvError.text = "Gemini API 키를 입력해주세요"
            tvError.visibility = View.VISIBLE
            return
        }
        if (recordedActions.isEmpty()) {
            tvError.text = "녹화된 동작이 없습니다"
            tvError.visibility = View.VISIBLE
            return
        }

        repository.saveApiKey(apiKey)
        tvError.visibility = View.GONE
        progressBar.visibility = View.VISIBLE
        btnOptimize.isEnabled = false

        val actions = recordedActions.toList()
        scope.launch {
            val client = GeminiClient(apiKey)
            val result = client.optimizeFromRecording(actions)

            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                btnOptimize.isEnabled = true

                result.fold(
                    onSuccess = { steps ->
                        generatedSteps = steps
                        btnSave.isEnabled = true
                        showOptimizedScript(steps)
                    },
                    onFailure = { e ->
                        tvError.text = "오류: ${e.message}"
                        tvError.visibility = View.VISIBLE
                    }
                )
            }
        }
    }

    private fun showOptimizedScript(steps: List<ActionStep>) {
        val message = buildString {
            appendLine("🤖 AI가 최적화한 ${steps.size}개 동작:\n")
            steps.forEachIndexed { i, step ->
                val emoji = when (step.action) {
                    "click" -> "👆"
                    "click_if_exists" -> "🔍"
                    "click_until" -> "🔄"
                    "find_and_click" -> "🔎"
                    "wait" -> "⏳"
                    "swipe" -> "👉"
                    "scroll" -> "📜"
                    "type" -> "⌨️"
                    "back" -> "🔙"
                    "home" -> "🏠"
                    "launch_app" -> "📱"
                    "loop" -> "🔁"
                    else -> "⚡"
                }
                val desc = step.description ?: step.targetValue ?: step.action
                appendLine("${i + 1}. $emoji $desc")
            }
        }

        AlertDialog.Builder(this)
            .setTitle("✅ AI 최적화 완료")
            .setMessage(message)
            .setPositiveButton("저장하기") { _, _ -> showSaveDialog() }
            .setNegativeButton("다시 생성", null)
            .show()
    }

    private fun showSaveDialog() {
        val editText = android.widget.EditText(this).apply {
            hint = "매크로 이름 (예: 유튜브 검색)"
        }

        AlertDialog.Builder(this)
            .setTitle("💾 매크로 저장")
            .setMessage("매크로 이름을 입력하세요")
            .setView(editText)
            .setPositiveButton("저장") { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isNotEmpty()) {
                    saveWithName(name)
                } else {
                    editText.error = "이름을 입력하세요"
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun saveWithName(name: String) {
        val steps = generatedSteps ?: return
        val script = MacroScript(name = name, steps = steps)
        repository.save(script)
        finish()
    }

    private fun saveScript() {
        if (generatedSteps == null) return
        showSaveDialog()
    }
}
