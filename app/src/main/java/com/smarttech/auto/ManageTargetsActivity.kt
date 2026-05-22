package com.smarttech.auto

import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ManageTargetsActivity : AppCompatActivity() {

    private var currentPkg: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_targets)

        val layoutList = findViewById<LinearLayout>(R.id.layout_list)
        val btnClose = findViewById<Button>(R.id.btn_close)

        btnClose.setOnClickListener { finish() }

        currentPkg = AutoClickService.lastTargetPackage ?: AutoClickService.currentPackage
        loadTargetsForCurrentPackage()
        refreshList(layoutList)
    }

    private fun loadTargetsForCurrentPackage() {
        val pkg = currentPkg ?: return
        val prefs = getSharedPreferences("smarttech_auto", Context.MODE_PRIVATE)
        val targets = AutoClickService.loadTargetsForPackage(prefs, pkg)
        AutoClickService.targetsChanged(
            targets.map { t ->
                when {
                    t.isIdBased() -> "ID:${t.viewId}"
                    else -> "TEXT:${t.text}"
                }
            }
        )
    }

    private fun refreshList(layoutList: LinearLayout) {
        layoutList.removeAllViews()
        val targets = AutoClickService.getLearnedTargets()
        if (targets.isEmpty()) {
            val tv = TextView(this)
            tv.text = if (currentPkg != null) "\uC800\uC7A5\uB41C \uD56D\uBAA9\uC774 \uC5C6\uC2B5\uB2C8\uB2E4" else "\uD604\uC7AC \uC571\uC744 \uAC10\uC9C0\uD560 \uC218 \uC5C6\uC2B5\uB2C8\uB2E4"
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
            btnDel.text = "\uC0AD\uC81C"
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
        val pkg = currentPkg ?: return
        val prefs = getSharedPreferences("smarttech_auto", Context.MODE_PRIVATE)
        val targets = AutoClickService.loadTargetsForPackage(prefs, pkg).toMutableList()
        if (index in targets.indices) {
            targets.removeAt(index)
            AutoClickService.saveTargetsForPackage(prefs, pkg, targets)
            AutoClickService.targetsChanged(
                targets.map { t ->
                    when {
                        t.isIdBased() -> "ID:${t.viewId}"
                        else -> "TEXT:${t.text}"
                    }
                }
            )
        }
    }
}
