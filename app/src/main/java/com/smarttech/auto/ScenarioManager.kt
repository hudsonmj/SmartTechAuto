package com.smarttech.auto

import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.*

data class AppScenario(
    val packageName: String,
    val waitTimeSeconds: Long
)

class ScenarioManager(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    fun executeScenarios(scenarios: List<AppScenario>) {
        scope.launch {
            for (scenario in scenarios) {
                Log.d("ScenarioManager", "Launching: ${scenario.packageName}")
                launchApp(scenario.packageName)
                
                // 지정된 시간 동안 대기 (앱이 로딩되고 광고가 뜰 때까지)
                delay(scenario.waitTimeSeconds * 1000)
                
                // 이때 AutoClickService가 켜져 있다면 자동으로 클릭을 수행함
            }
            Log.d("ScenarioManager", "All scenarios completed")
        }
    }

    private fun launchApp(packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } else {
            Log.e("ScenarioManager", "App not found: $packageName")
        }
    }
}
