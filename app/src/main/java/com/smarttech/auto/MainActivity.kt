package com.smarttech.auto

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnAccessibility = findViewById<Button>(R.id.btn_accessibility_settings)
        val btnOverlay = findViewById<Button>(R.id.btn_overlay_settings)
        val btnStartOverlay = findViewById<Button>(R.id.btn_start_overlay)
        val btnRunScenario = findViewById<Button>(R.id.btn_run_scenario)

        val scenarioManager = ScenarioManager(this)

        btnAccessibility.setOnClickListener {
            if (!isAccessibilityServiceEnabled(this, AutoClickService::class.java)) {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivity(intent)
            } else {
                Toast.makeText(this, "접근성 권한이 이미 허용되어 있습니다.", Toast.LENGTH_SHORT).show()
            }
        }

        btnOverlay.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else {
                Toast.makeText(this, "오버레이 권한이 이미 허용되어 있습니다.", Toast.LENGTH_SHORT).show()
            }
        }

        btnStartOverlay.setOnClickListener {
            if (isAccessibilityServiceEnabled(this, AutoClickService::class.java) && Settings.canDrawOverlays(this)) {
                val intent = Intent(this, OverlayService::class.java)
                startService(intent)
            } else {
                Toast.makeText(this, "접근성 및 오버레이 권한을 먼저 허용해주세요.", Toast.LENGTH_LONG).show()
            }
        }

        btnRunScenario.setOnClickListener {
            val scenarios = listOf(
                AppScenario("com.android.settings", 5), // 설정 앱 실행 후 5초 대기
                AppScenario("com.google.android.youtube", 10) // 유튜브 실행 후 10초 대기
            )
            scenarioManager.executeScenarios(scenarios)
            Toast.makeText(this, "시나리오를 시작합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    // 접근성 권한 체크 헬퍼
    private fun isAccessibilityServiceEnabled(context: Context, accessibilityService: Class<*>): Boolean {
        val expectedComponentName = ComponentName(context, accessibilityService)
        val enabledServicesSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)

        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledService = ComponentName.unflattenFromString(componentNameString)
            if (enabledService != null && enabledService == expectedComponentName) {
                return true
            }
        }
        return false
    }
}