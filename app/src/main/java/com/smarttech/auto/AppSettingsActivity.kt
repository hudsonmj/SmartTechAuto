package com.smarttech.auto

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
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
        val etDelayMin = findViewById<EditText>(R.id.et_delay_min)
        val etDelayMax = findViewById<EditText>(R.id.et_delay_max)
        val btnSaveDelay = findViewById<Button>(R.id.btn_save_delay)

        val prefs = getSharedPreferences("smarttech_auto", Context.MODE_PRIVATE)
        etDelayMin.setText(prefs.getLong("click_delay_min", 500L).toString())
        etDelayMax.setText(prefs.getLong("click_delay_max", 1500L).toString())

        btnSaveDelay.setOnClickListener {
            val minText = etDelayMin.text.toString()
            val maxText = etDelayMax.text.toString()
            val min = minText.toLongOrNull()
            val max = maxText.toLongOrNull()
            if (min == null || max == null || min < 100 || max > 10000 || min >= max) {
                Toast.makeText(this, "\uC62C\uBC14\uB978 \uBC94\uC704\uB97C \uC785\uB825\uD558\uC138\uC694 (100~10000, \uCD5C\uC18C < \uCD5C\uB300)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.edit().putLong("click_delay_min", min).putLong("click_delay_max", max).apply()
            Toast.makeText(this, "\uD074\uB9AD \uC9C0\uC5F0 \uC800\uC7A5\uB428 (\uCD5C\uC18C:${min}ms, \uCD5C\uB300:${max}ms)", Toast.LENGTH_SHORT).show()
        }

        btnClose.setOnClickListener { finish() }

        btnAddCurrent.setOnClickListener {
            val pkg = AutoClickService.currentPackage
            if (pkg != null && pkg.isNotBlank()) {
                val prefs2 = getSharedPreferences("smarttech_auto", Context.MODE_PRIVATE)
                AutoClickService.loadAppConfigs(prefs2)
                val existing = AutoClickService.getConfiguredPackages()
                if (pkg in existing) {
                    Toast.makeText(this, "\uC774\uBBF8 \uB4F1\uB85D\uB41C \uC571\uC785\uB2C8\uB2E4: $pkg", Toast.LENGTH_SHORT).show()
                } else {
                    AutoClickService.setAppMode(pkg, "manual")
                    AutoClickService.saveAppConfigs(prefs2)
                    Toast.makeText(this, "\uC571 \uCD94\uAC00\uB428: $pkg", Toast.LENGTH_SHORT).show()
                    refreshList(layoutAppList)
                }
            } else {
                Toast.makeText(this, "\uD604\uC7AC \uC2E4\uD589 \uC911\uC778 \uC571\uC744 \uAC10\uC9C0\uD560 \uC218 \uC5C6\uC2B5\uB2C8\uB2E4", Toast.LENGTH_SHORT).show()
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
            tv.text = "\uB4F1\uB85D\uB41C \uC571\uC774 \uC5C6\uC2B5\uB2C8\uB2E4"
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
            modeSwitch.textOn = "\uC790\uB3D9"
            modeSwitch.textOff = "\uC218\uB3D9"
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
