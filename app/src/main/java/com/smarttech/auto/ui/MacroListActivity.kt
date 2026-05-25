package com.smarttech.auto.ui

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.smarttech.auto.R
import com.smarttech.auto.model.MacroScript
import com.smarttech.auto.storage.MacroRepository

class MacroListActivity : AppCompatActivity() {

    private lateinit var repository: MacroRepository
    private lateinit var rvMacros: RecyclerView
    private lateinit var tvEmpty: TextView
    private var adapter: MacroListAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_macro_list)
        title = "📋 매크로 관리"

        repository = MacroRepository(this)
        rvMacros = findViewById(R.id.rv_macros)
        tvEmpty = findViewById(R.id.tv_empty)

        rvMacros.layoutManager = LinearLayoutManager(this)

        findViewById<MaterialButton>(R.id.btn_create_macro).setOnClickListener {
            startActivity(Intent(this, MacroCreateActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        loadMacros()
    }

    private fun loadMacros() {
        val macros = repository.getAll()
        if (macros.isEmpty()) {
            tvEmpty.visibility = android.view.View.VISIBLE
            rvMacros.visibility = android.view.View.GONE
        } else {
            tvEmpty.visibility = android.view.View.GONE
            rvMacros.visibility = android.view.View.VISIBLE
            adapter = MacroListAdapter(macros, object : MacroListAdapter.Callback {
                override fun onRun(macro: MacroScript) {
                    val intent = Intent(this@MacroListActivity, MacroDetailActivity::class.java)
                    intent.putExtra("macro_id", macro.id)
                    intent.putExtra("run", true)
                    startActivity(intent)
                }

                override fun onEdit(macro: MacroScript) {
                    val intent = Intent(this@MacroListActivity, MacroDetailActivity::class.java)
                    intent.putExtra("macro_id", macro.id)
                    startActivity(intent)
                }

                override fun onDelete(macro: MacroScript) {
                    repository.delete(macro.id)
                    loadMacros()
                }
            })
            rvMacros.adapter = adapter
        }
    }
}
