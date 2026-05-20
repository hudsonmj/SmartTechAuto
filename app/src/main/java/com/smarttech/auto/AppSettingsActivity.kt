package com.smarttech.auto

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class AppSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_settings)

        val layoutAppList = findViewById<LinearLayout>(R.id.layout_app_list)
        val btnAddCurrent = findViewById<Button>(R.id.btn_add_current)
        val btnClose = findViewById<Button>(R.id.btn_close)

        btnClose.setOnClickListener { finish() }

        btnAddCurrent.setOnClickListener {
            val pkg = AutoClickService.currentPackage
            if (pkg != null && pkg.isNotBlank()) {
                val prefs = getSharedPreferences("smarttech_auto", Context.MODE_PRIVATE)
                AutoClickService.loadAppConfigs(prefs)
                val existing = AutoClickService.getConfiguredPackages()
                if (pkg in existing) {
                    Toast.makeText(this, "이미 등록된 앱입니다: $pkg", Toast.LENGTH_SHORT).show()
                } else {
                    AutoClickService.setAppMode(pkg, "manual")
                    AutoClickService.saveAppConfigs(prefs)
                    Toast.makeText(this, "앱 추가됨: $pkg", Toast.LENGTH_SHORT).show()
                    refreshList(layoutAppList)
                }
            } else {
                Toast.makeText(this, "현재 실행 중인 앱을 감지할 수 없습니다", Toast.LENGTH_SHORT).show()
            }
        }

        refreshList(layoutAppList)
    }

    override fun onResume() {
        super.onResume()
        val layoutAppList = findViewById<LinearLayout>(R.id.layout_app_list)
        refreshList(layoutAppList)
    }

    private fun refreshList(layoutAppList: LinearLayout) {
        layoutAppList.removeAllViews()
        val prefs = getSharedPreferences("smarttech_auto", Context.MODE_PRIVATE)
        AutoClickService.loadAppConfigs(prefs)
        val packages = AutoClickService.getConfiguredPackages()

        if (packages.isEmpty()) {
            val tv = TextView(this)
            tv.text = "등록된 앱이 없습니다"
            tv.gravity = Gravity.CENTER
            tv.setPadding(0, 32, 0, 32)
            tv.setTextColor(0xFF888888.toInt())
            layoutAppList.addView(tv)
            return
        }

        for ((index, pkg) in packages.withIndex()) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.setPadding(8, 8, 8, 8)

            val appName = try {
                val pm = packageManager
                val ai = pm.getApplicationInfo(pkg, 0)
                pm.getApplicationLabel(ai).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                pkg
            }

            val nameText = TextView(this)
            nameText.text = "${index + 1}. $appName"
            nameText.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            nameText.setTextColor(0xFF333333.toInt())
            nameText.textSize = 12f
            nameText.gravity = Gravity.CENTER_VERTICAL

            val modeSwitch = Switch(this)
            val currentMode = AutoClickService.getAppMode(pkg)
            modeSwitch.isChecked = currentMode == "auto"
            modeSwitch.textOn = "자동"
            modeSwitch.textOff = "수동"
            modeSwitch.setTextSize(10f)

            modeSwitch.setOnCheckedChangeListener { _, isChecked ->
                val newMode = if (isChecked) "auto" else "manual"
                AutoClickService.setAppMode(pkg, newMode)
                AutoClickService.saveAppConfigs(prefs)
            }

            val btnDelete = Button(this)
            btnDelete.text = "X"
            btnDelete.textSize = 11f
            btnDelete.setBackgroundColor(0xFFF44336.toInt())
            btnDelete.setTextColor(0xFFFFFFFF.toInt())
            btnDelete.setPadding(8, 0, 8, 0)
            btnDelete.minWidth = 0
            btnDelete.minHeight = 0
            val dp36 = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 36f, resources.displayMetrics
            ).toInt()
            btnDelete.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp36
            )

            btnDelete.setOnClickListener {
                AutoClickService.removeApp(pkg)
                AutoClickService.saveAppConfigs(prefs)
                val targetKey = "targets_$pkg"
                prefs.edit().remove(targetKey).apply()
                refreshList(layoutAppList)
            }

            row.addView(nameText)
            row.addView(modeSwitch)
            row.addView(btnDelete)
            layoutAppList.addView(row)

            val divider = TextView(this)
            divider.setBackgroundColor(0xFFDDDDDD.toInt())
            divider.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            )
            layoutAppList.addView(divider)
        }
    }
}
