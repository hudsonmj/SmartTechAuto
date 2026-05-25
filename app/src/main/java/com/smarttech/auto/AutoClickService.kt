package com.smarttech.auto

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Rect
import android.os.PowerManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.smarttech.auto.model.RecordedAction
import kotlinx.coroutines.*
import org.json.JSONObject

data class LearnedTarget(
    val viewId: String?,
    val text: String?
) {
    fun displayText(): String = when {
        viewId != null -> viewId
        text?.startsWith("PATH:") == true -> "\uACBD\uB85C:${text.removePrefix("PATH:")}"
        text?.startsWith("COORDS:") == true -> "\uC88C\uD45C:${text.removePrefix("COORDS:")}"
        text?.startsWith("CLASS:") == true -> "\uD0C0\uC785:${text.removePrefix("CLASS:")}"
        text?.startsWith("NEXTTO:") == true -> "\uC66C\uD14D\uC2A4\uD2B8:${text.removePrefix("NEXTTO:")}"
        else -> (text ?: "?")
    }
    fun isIdBased(): Boolean = viewId != null
    fun isPathBased(): Boolean = text?.startsWith("PATH:") == true
    fun isCoordsBased(): Boolean = text?.startsWith("COORDS:") == true
    fun isClassBased(): Boolean = text?.startsWith("CLASS:") == true
    fun isNextToBased(): Boolean = text?.startsWith("NEXTTO:") == true
}

class AutoClickService : AccessibilityService() {

    companion object {
        private const val TAG = "AutoClickService"
        var isRunning = false
        var isRecording = false
        var isLearning = false
        var autoClosePopups = true
        val recordedActions = mutableListOf<RecordedAction>()
        var currentPackage: String? = null
        var lastTargetPackage: String? = null
        private var appConfigs = mutableMapOf<String, String>()
        var sharedTargets: List<LearnedTarget> = emptyList()
        var serviceInstance: AutoClickService? = null

        fun addRecordedNote(text: String) {
            recordedActions.add(RecordedAction(type = RecordedAction.Type.NOTE, note = text))
            Log.d(TAG, "Recorded note: $text")
        }

        fun getLearnedTargets(): List<LearnedTarget> = sharedTargets

        fun targetsChanged(rawLines: List<String>) {
            sharedTargets = rawLines.mapNotNull { line ->
                when {
                    line.startsWith("ID:") -> LearnedTarget(line.removePrefix("ID:"), null)
                    line.startsWith("TEXT:") -> LearnedTarget(null, line.removePrefix("TEXT:"))
                    else -> null
                }
            }
        }

        fun loadAppConfigs(prefs: android.content.SharedPreferences) {
            appConfigs.clear()
            val raw = prefs.getString("app_configs", "") ?: ""
            if (raw.isNotEmpty()) {
                try {
                    val json = JSONObject(raw)
                    for (key in json.keys()) {
                        appConfigs[key] = json.getString(key)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load app configs", e)
                }
            }
        }

        fun saveAppConfigs(prefs: android.content.SharedPreferences) {
            val json = JSONObject(appConfigs as Map<*, *>)
            prefs.edit().putString("app_configs", json.toString()).apply()
        }

        fun getAppMode(pkg: String): String? = appConfigs[pkg]
        fun setAppMode(pkg: String, mode: String) { appConfigs[pkg] = mode }
        fun removeApp(pkg: String) { appConfigs.remove(pkg) }
        fun getConfiguredPackages(): List<String> = appConfigs.keys.toList()

        private fun getTargetsKey(pkg: String): String = "targets_$pkg"

        fun loadTargetsForPackage(prefs: android.content.SharedPreferences, pkg: String): List<LearnedTarget> {
            val raw = prefs.getString(getTargetsKey(pkg), "") ?: ""
            val list = mutableListOf<LearnedTarget>()
            if (raw.isNotEmpty()) {
                for (line in raw.split("\n")) {
                    val target = when {
                        line.startsWith("ID:") -> LearnedTarget(line.removePrefix("ID:"), null)
                        line.startsWith("TEXT:") -> LearnedTarget(null, line.removePrefix("TEXT:"))
                        else -> null
                    }
                    if (target != null) list.add(target)
                }
            }
            return list
        }

        fun saveTargetsForPackage(prefs: android.content.SharedPreferences, pkg: String, targets: List<LearnedTarget>) {
            val lines = targets.joinToString("\n") { t ->
                when {
                    t.isIdBased() -> "ID:${t.viewId}"
                    else -> "TEXT:${t.text}"
                }
            }
            prefs.edit().putString(getTargetsKey(pkg), lines).apply()
        }
    }

    private val adKeywords = listOf(
        "\uAD11\uACE0 \uB2EB\uAE30", "\uADF8\uB9CC\uBCF4\uAE30", "X", "Close", "Skip",
        "\uBCF4\uC0C1\uBC1B\uAE30", "\uBCF4\uC0C1 \uBC1B\uAE30", "\uD655\uC778", "\uC644\uB8CC"
    )

    private val learnedTargets = mutableListOf<LearnedTarget>()
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var isProcessing = false
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var clickEngine: ClickEngine

    override fun onCreate() {
        super.onCreate()
        serviceInstance = this
        clickEngine = ClickEngine(this)
        val prefs = getSharedPreferences("smarttech_auto", Context.MODE_PRIVATE)
        loadAppConfigs(prefs)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val eventPkg = event.packageName?.toString()

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            currentPackage = eventPkg
        }

        if (isLearning && (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
                event.eventType == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED)) {
            handleLearningEvent(event, eventPkg)
            return
        }

        if (!isRunning) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val pkg = currentPackage
            if (pkg == null || appConfigs[pkg] != "auto") return
            if (isProcessing) return

            isProcessing = true
            acquireWakeLock()
            val rootNode = rootInActiveWindow ?: run { isProcessing = false; return }

            serviceScope.launch {
                try {
                    val prefs = getSharedPreferences("smarttech_auto", Context.MODE_PRIVATE)
                    learnedTargets.clear()
                    learnedTargets.addAll(loadTargetsForPackage(prefs, pkg))

                    val delayMin = prefs.getLong("click_delay_min", 500L)
                    val delayMax = prefs.getLong("click_delay_max", 1500L)

                    clickEngine.searchAndClick(rootNode, learnedTargets.toList(), adKeywords, delayMin, delayMax)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in auto-click", e)
                } finally {
                    isProcessing = false
                    rootNode.recycle()
                }
            }
        }
    }

    private fun handleLearningEvent(event: AccessibilityEvent, eventPkg: String?) {
        if (eventPkg == packageName) {
            Log.d(TAG, "Skipped: event from own package $eventPkg")
            return
        }

        val source = event.source
        Log.d(TAG, "Learning event: type=${event.eventType}, pkg=$eventPkg, source=${source != null}")

        val viewId = source?.viewIdResourceName
        if (viewId != null && viewId.startsWith(packageName)) {
            source?.recycle()
            Log.d(TAG, "Skipped: viewId starts with own package $viewId")
            return
        }

        val text = if (source != null) {
            source.text?.toString() ?: source.contentDescription?.toString()
        } else {
            event.text?.firstOrNull()?.toString()
        }

        Log.d(TAG, "viewId=$viewId, text=$text")
        val pkg = eventPkg ?: currentPackage ?: return

        if (viewId == null && text == null) {
            handleAnonymousNode(source, pkg)
            return
        }

        val target = LearnedTarget(viewId, if (viewId != null) null else text)
        addTargetForPackage(pkg, target)
        Log.d(TAG, "Learned target: ${target.displayText()} for $pkg")
        OverlayService.instance?.showStatus("\u2705 \uC800\uC7A5\uB428: ${target.displayText()}")
        source?.recycle()
    }

    private fun handleAnonymousNode(source: AccessibilityNodeInfo?, pkg: String) {
        if (source == null) {
            OverlayService.instance?.showStatus("\u274C \uC800\uC7A5 \uC2E4\uD328 (\uC811\uADFC\uC131 \uC815\uBCF4 \uC5C6\uC74C)")
            Log.w(TAG, "Cannot learn: source is null for $pkg")
            return
        }

        val parent = source.parent
        if (parent != null) {
            for (i in 0 until parent.childCount) {
                val child = parent.getChild(i)
                if (child != null && child != source) {
                    val st = child.text?.toString() ?: child.contentDescription?.toString()
                    if (st != null && st.isNotBlank()) {
                        val target = LearnedTarget(null, "NEXTTO:$st")
                        addTargetForPackage(pkg, target)
                        Log.d(TAG, "Learned by sibling: $st for $pkg")
                        OverlayService.instance?.showStatus("\u2705 \uC66C\uD14D\uC2A4\uD2B8 \uC800\uC7A5 ($pkg)")
                        child.recycle()
                        parent.recycle()
                        source.recycle()
                        return
                    }
                    child.recycle()
                }
            }
            parent.recycle()
        }

        val className = source.className?.toString()
        if (className != null && className.isNotBlank()) {
            val target = LearnedTarget(null, "CLASS:$className")
            addTargetForPackage(pkg, target)
            Log.d(TAG, "Learned by class: $className for $pkg")
            OverlayService.instance?.showStatus("\u2705 \uD0C0\uC785 \uC800\uC7A5 ($pkg)")
            source.recycle()
            return
        }

        val path = buildNodeIndexPath(source)
        if (path != null) {
            val target = LearnedTarget(null, "PATH:$path")
            addTargetForPackage(pkg, target)
            Log.d(TAG, "Learned by path: $path for $pkg")
            OverlayService.instance?.showStatus("\u2705 \uACBD\uB85C \uC800\uC7A5 ($pkg)")
        } else {
            val rect = android.graphics.Rect()
            source.getBoundsInScreen(rect)
            val coordText = "COORDS:${rect.centerX()},${rect.centerY()}"
            val target = LearnedTarget(null, coordText)
            addTargetForPackage(pkg, target)
            Log.d(TAG, "Learned by coord: ($coordText) for $pkg")
            OverlayService.instance?.showStatus("\u2705 \uC88C\uD45C \uC800\uC7A5 ($pkg)")
        }
        source.recycle()
    }

    private fun addTargetForPackage(pkg: String, target: LearnedTarget) {
        val prefs = getSharedPreferences("smarttech_auto", Context.MODE_PRIVATE)
        learnedTargets.clear()
        learnedTargets.addAll(loadTargetsForPackage(prefs, pkg))
        learnedTargets.add(target)
        saveForPackage(pkg)
    }

    private fun buildNodeIndexPath(source: AccessibilityNodeInfo): String? {
        val indices = mutableListOf<Int>()
        var current = source
        val currentRect = Rect().also { current.getBoundsInScreen(it) }

        while (true) {
            val parent = current.parent ?: break
            var foundIndex = -1

            for (i in 0 until parent.childCount) {
                val child = parent.getChild(i) ?: continue
                val childRect = Rect()
                child.getBoundsInScreen(childRect)
                if (childRect == currentRect) {
                    foundIndex = i
                    child.recycle()
                    break
                }
                child.recycle()
            }

            if (foundIndex < 0) return null
            indices.add(foundIndex)

            val parentRect = Rect()
            parent.getBoundsInScreen(parentRect)
            currentRect.set(parentRect)
            current = parent
        }

        indices.reverse()
        return indices.joinToString(",")
    }

    private fun saveForPackage(pkg: String) {
        lastTargetPackage = pkg
        val prefs = getSharedPreferences("smarttech_auto", Context.MODE_PRIVATE)
        saveTargetsForPackage(prefs, pkg, learnedTargets)
        if (appConfigs[pkg] == null) {
            appConfigs[pkg] = "manual"
            saveAppConfigs(prefs)
        }
        sharedTargets = learnedTargets.toList()
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "SmartTechAuto:AutoClickWakeLock"
            )
            wakeLock?.acquire(10 * 60 * 1000L)
            Log.d(TAG, "WakeLock acquired")
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
        Log.d(TAG, "WakeLock released")
    }

    fun triggerAutoClickOnCurrentScreen() {
        if (!isRunning || isProcessing) return
        val pkg = currentPackage
        if (pkg == null) {
            OverlayService.instance?.showStatus("\u26A0\uFE0F \uD604\uC7AC \uC571\uC744 \uAC10\uC9C0\uD560 \uC218 \uC5C6\uC2B5\uB2C8\uB2E4")
            return
        }

        isProcessing = true
        acquireWakeLock()
        val rootNode = rootInActiveWindow ?: run { isProcessing = false; return }

        serviceScope.launch {
            try {
                val prefs = getSharedPreferences("smarttech_auto", Context.MODE_PRIVATE)
                loadAppConfigs(prefs)
                learnedTargets.clear()
                learnedTargets.addAll(loadTargetsForPackage(prefs, pkg))
                val delayMin = prefs.getLong("click_delay_min", 500L)
                val delayMax = prefs.getLong("click_delay_max", 1500L)
                clickEngine.searchAndClick(rootNode, learnedTargets.toList(), adKeywords, delayMin, delayMax)
            } catch (e: Exception) {
                Log.e(TAG, "Error in auto-click", e)
            } finally {
                isProcessing = false
                rootNode.recycle()
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceInstance = null
        releaseWakeLock()
        serviceScope.cancel()
    }
}
