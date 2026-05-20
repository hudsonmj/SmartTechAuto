package com.smarttech.auto

import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit

class ManageTargetsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_targets)

        val layoutList = findViewById<LinearLayout>(R.id.layout_list)
        val btnClose = findViewById<Button>(R.id.btn_close)

        btnClose.setOnClickListener { finish() }

        refreshList(layoutList)
    }

    private fun refreshList(layoutList: LinearLayout) {
        layoutList.removeAllViews()
        val targets = AutoClickService.getLearnedTargets()
        if (targets.isEmpty()) {
            val tv = TextView(this)
            tv.text = "저장된 항목이 없습니다"
            tv.gravity = Gravity.CENTER
            tv.setPadding(0, 32, 0, 32)
            tv.setTextColor(0xFF888888.toInt())
            layoutList.addView(tv)
            return
        }
        for ((index, target) in targets.withIndex()) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.setPadding(8, 8, 8, 8)

            val label = TextView(this)
            label.text = "${index + 1}. ${target.displayText()}"
            label.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            label.setTextColor(0xFF333333.toInt())
            label.textSize = 13f
            label.gravity = Gravity.CENTER_VERTICAL

            val btnDel = Button(this)
            btnDel.text = "삭제"
            btnDel.textSize = 11f
            btnDel.setBackgroundColor(0xFFF44336.toInt())
            btnDel.setTextColor(0xFFFFFFFF.toInt())
            btnDel.setPadding(8, 0, 8, 0)
            btnDel.minWidth = 0
            btnDel.minHeight = 0
            val dp36 = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 36f, resources.displayMetrics
            ).toInt()
            btnDel.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp36
            )

            val pos = index
            btnDel.setOnClickListener {
                deleteTarget(pos)
                refreshList(layoutList)
            }

            row.addView(label)
            row.addView(btnDel)
            layoutList.addView(row)

            val divider = TextView(this)
            divider.setBackgroundColor(0xFFDDDDDD.toInt())
            divider.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            )
            layoutList.addView(divider)
        }
    }

    private fun deleteTarget(index: Int) {
        val prefs = getSharedPreferences("smarttech_auto", Context.MODE_PRIVATE)
        val raw = prefs.getString("learned_targets", "") ?: ""
        val lines = raw.split("\n").filter { it.isNotEmpty() }.toMutableList()
        if (index in lines.indices) {
            lines.removeAt(index)
            prefs.edit { putString("learned_targets", lines.joinToString("\n")) }
            AutoClickService.targetsChanged(lines)
        }
    }
}
