package com.smarttech.auto.ui

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.smarttech.auto.R
import com.smarttech.auto.ai.GeminiClient
import com.smarttech.auto.model.ActionStep
import com.smarttech.auto.model.MacroScript
import com.smarttech.auto.storage.MacroRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MacroCreateActivity : AppCompatActivity() {

    private lateinit var repository: MacroRepository
    private lateinit var etApiKey: TextInputEditText
    private lateinit var etRequest: TextInputEditText
    private lateinit var btnGenerate: MaterialButton
    private lateinit var btnSave: MaterialButton
    private lateinit var progressBar: ProgressBar
    private lateinit var tvError: TextView
    private lateinit var tvResultLabel: TextView
    private lateinit var rvSteps: RecyclerView
    private lateinit var etMacroName: TextInputEditText
    private lateinit var layoutSave: View

    private var generatedSteps: List<ActionStep> = emptyList()
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_macro_create)
        title = "🤖 AI 매크로 생성"

        repository = MacroRepository(this)

        etApiKey = findViewById(R.id.et_api_key)
        etRequest = findViewById(R.id.et_request)
        btnGenerate = findViewById(R.id.btn_generate)
        btnSave = findViewById(R.id.btn_save)
        progressBar = findViewById(R.id.progress_bar)
        tvError = findViewById(R.id.tv_error)
        tvResultLabel = findViewById(R.id.tv_result_label)
        rvSteps = findViewById(R.id.rv_steps)
        etMacroName = findViewById(R.id.et_macro_name)
        layoutSave = findViewById(R.id.layout_save)

        rvSteps.layoutManager = LinearLayoutManager(this)

        val savedKey = repository.getApiKey()
        if (savedKey.isNotEmpty()) {
            etApiKey.setText(savedKey)
        }

        btnGenerate.setOnClickListener {
            generateScript()
        }

        btnSave.setOnClickListener {
            saveMacro()
        }
    }

    private fun generateScript() {
        val apiKey = etApiKey.text?.toString()?.trim() ?: return
        val request = etRequest.text?.toString()?.trim() ?: return

        if (apiKey.isEmpty()) {
            tvError.text = "Gemini API 키를 입력해주세요"
            tvError.visibility = View.VISIBLE
            return
        }
        if (request.isEmpty()) {
            tvError.text = "매크로 동작을 설명해주세요"
            tvError.visibility = View.VISIBLE
            return
        }

        repository.saveApiKey(apiKey)
        tvError.visibility = View.GONE
        progressBar.visibility = View.VISIBLE
        btnGenerate.isEnabled = false
        layoutSave.visibility = View.GONE
        tvResultLabel.visibility = View.GONE

        scope.launch {
            val client = GeminiClient(apiKey)
            val result = client.generateScript(request)

            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                btnGenerate.isEnabled = true

                result.fold(
                    onSuccess = { steps ->
                        generatedSteps = steps
                        showSteps(steps)
                    },
                    onFailure = { e ->
                        tvError.text = "오류: ${e.message}"
                        tvError.visibility = View.VISIBLE
                    }
                )
            }
        }
    }

    private fun showSteps(steps: List<ActionStep>) {
        tvResultLabel.visibility = View.VISIBLE
        val adapter = StepAdapter(steps)
        rvSteps.adapter = adapter

        etMacroName.setText("")
        layoutSave.visibility = View.VISIBLE
    }

    private fun saveMacro() {
        val name = etMacroName.text?.toString()?.trim()
        if (name.isNullOrEmpty()) {
            etMacroName.error = "매크로 이름을 입력해주세요"
            return
        }

        val script = MacroScript(
            name = name,
            steps = generatedSteps
        )
        repository.save(script)
        finish()
    }
}
