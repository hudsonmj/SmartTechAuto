package com.smarttech.auto

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Toast.makeText(this, "\uC54C\uB9BC \uAD8C\uD55C\uC774 \uD5C8\uC6A9\uB418\uC5C8\uC2B5\uB2C8\uB2E4.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnAccessibility = findViewById<Button>(R.id.btn_accessibility_settings)
        val btnOverlay = findViewById<Button>(R.id.btn_overlay_settings)
        val btnStartOverlay = findViewById<Button>(R.id.btn_start_overlay)
        val btnRunScenario = findViewById<Button>(R.id.btn_run_scenario)
        val btnBatteryOpt = findViewById<Button>(R.id.btn_battery_optimization)

        val scenarioManager = ScenarioManager(this)

        btnAccessibility.setOnClickListener {
            if (!isAccessibilityServiceEnabled(this, AutoClickService::class.java)) {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivity(intent)
            } else {
                Toast.makeText(this, "\uC811\uADFC\uC131 \uAD8C\uD55C\uC774 \uC774\uBBF8 \uD5C8\uC6A9\uB418\uC5B4 \uC788\uC2B5\uB2C8\uB2E4.", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, "\uC624\uBC84\uB808\uC774 \uAD8C\uD55C\uC774 \uC774\uBBF8 \uD5C8\uC6A9\uB418\uC5B4 \uC788\uC2B5\uB2C8\uB2E4.", Toast.LENGTH_SHORT).show()
            }
        }

        btnStartOverlay.setOnClickListener {
            if (isAccessibilityServiceEnabled(this, AutoClickService::class.java) && Settings.canDrawOverlays(this)) {
                requestNotificationPermission()
                val intent = Intent(this, OverlayService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
            } else {
                Toast.makeText(this, "\uC811\uADFC\uC131 \uBC0F \uC624\uBC84\uB808\uC774 \uAD8C\uD55C\uC744 \uBA3C\uC800 \uD5C8\uC6A9\uD574\uC8FC\uC138\uC694.", Toast.LENGTH_LONG).show()
            }
        }

        btnRunScenario.setOnClickListener {
            val scenarios = listOf(
                AppScenario("com.android.settings", 5),
                AppScenario("com.google.android.youtube", 10)
            )
            scenarioManager.executeScenarios(scenarios)
            Toast.makeText(this, "\uC2DC\uB098\uB9AC\uC624\uB97C \uC2DC\uC791\uD569\uB2C8\uB2E4.", Toast.LENGTH_SHORT).show()
        }

        btnBatteryOpt.setOnClickListener {
            requestBatteryOptimizationExemption()
        }
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "\uC774\uBBF8 \uBC30\uD130\uB9AC \uCD5C\uC801\uD654 \uC608\uC678\uC785\uB2C8\uB2E4.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                Toast.makeText(this, "\uC54C\uB9BC \uAD8C\uD55C\uC774 \uD544\uC694\uD569\uB2C8\uB2E4.", Toast.LENGTH_SHORT).show()
            }
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

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
