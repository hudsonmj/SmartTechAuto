package com.smarttech.auto

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*
import kotlin.random.Random
import org.json.JSONObject

data class LearnedTarget(
    val viewId: String?,
    val text: String?
) {
    fun displayText(): String = when {
        viewId != null -> viewId
        text?.startsWith("COORDS:") == true -> "좌표:${text.removePrefix("COORDS:")}"
        text?.startsWith("CLASS:") == true -> "타입:${text.removePrefix("CLASS:")}"
        text?.startsWith("NEXTTO:") == true -> "옆텍스트:${text.removePrefix("NEXTTO:")}"
        else -> (text ?: "?")
    }
    fun isIdBased(): Boolean = viewId != null
    fun isCoordsBased(): Boolean = text?.startsWith("COORDS:") == true
    fun isClassBased(): Boolean = text?.startsWith("CLASS:") == true
    fun isNextToBased(): Boolean = text?.startsWith("NEXTTO:") == true
}

class AutoClickService : AccessibilityService() {

    companion object {
        private const val TAG = "AutoClickService"
        var isRunning = false
        var isLearning = false
        var currentPackage: String? = null
        private var appConfigs = mutableMapOf<String, String>() // pkg -> mode
        private var sharedTargets: List<LearnedTarget> = emptyList()

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
                    t.isCoordsBased() || t.isClassBased() || t.isNextToBased() -> "TEXT:${t.text}"
                    else -> "TEXT:${t.text}"
                }
            }
            prefs.edit().putString(getTargetsKey(pkg), lines).apply()
        }
    }

    private val adKeywords = listOf(
        "광고 닫기", "그만보기", "X", "Close", "Skip", "보상받기", "보상 받기", "확인", "완료"
    )

    private val learnedTargets = mutableListOf<LearnedTarget>()
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var isProcessing = false

    override fun onCreate() {
        super.onCreate()
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
                val parent = source?.parent
                if (parent != null) {
                    for (i in 0 until parent.childCount) {
                        val child = parent.getChild(i)
                        if (child != null && child != source) {
                            val st = child.text?.toString() ?: child.contentDescription?.toString()
                            if (st != null && st.isNotBlank()) {
                                val target = LearnedTarget(null, "NEXTTO:$st")
                                learnedTargets.clear()
                                learnedTargets.add(target)
                                saveForPackage(pkg)
                                Log.d(TAG, "Learned by sibling: $st for $pkg")
                                OverlayService.instance?.showStatus("✅ 옆텍스트 저장 ($pkg)")
                                child.recycle()
                                parent.recycle()
                                source?.recycle()
                                return
                            }
                            child.recycle()
                        }
                    }
                    parent.recycle()
                }

                val className = source?.className?.toString()
                if (className != null && className.isNotBlank()) {
                    val target = LearnedTarget(null, "CLASS:$className")
                    learnedTargets.clear()
                    learnedTargets.add(target)
                    saveForPackage(pkg)
                    Log.d(TAG, "Learned by class: $className for $pkg")
                    OverlayService.instance?.showStatus("✅ 타입 저장 ($pkg)")
                    source?.recycle()
                    return
                }
                val rect = Rect()
                source?.getBoundsInScreen(rect)
                val coordText = "COORDS:${rect.centerX()},${rect.centerY()}"
                val target = LearnedTarget(null, coordText)
                learnedTargets.clear()
                learnedTargets.add(target)
                saveForPackage(pkg)
                Log.d(TAG, "Learned by coord: ($coordText) for $pkg")
                OverlayService.instance?.showStatus("✅ 좌표 저장 ($pkg)")
                source?.recycle()
                return
            }

            val target = LearnedTarget(viewId, if (viewId != null) null else text)
            learnedTargets.clear()
            learnedTargets.add(target)
            saveForPackage(pkg)
            Log.d(TAG, "Learned target: ${target.displayText()} for $pkg")
            OverlayService.instance?.showStatus("✅ 저장됨: ${target.displayText()}")
            source?.recycle()
            return
        }

        if (!isRunning) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            val pkg = currentPackage
            if (pkg == null || appConfigs[pkg] != "auto") return

            if (isProcessing) return

            isProcessing = true
            val rootNode = rootInActiveWindow ?: run {
                isProcessing = false
                return
            }

            serviceScope.launch {
                try {
                    val prefs = getSharedPreferences("smarttech_auto", Context.MODE_PRIVATE)
                    learnedTargets.clear()
                    learnedTargets.addAll(loadTargetsForPackage(prefs, pkg))
                    searchAndClick(rootNode)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in searchAndClick", e)
                } finally {
                    isProcessing = false
                    rootNode.recycle()
                }
            }
        }
    }

    private fun saveForPackage(pkg: String) {
        val prefs = getSharedPreferences("smarttech_auto", Context.MODE_PRIVATE)
        saveTargetsForPackage(prefs, pkg, learnedTargets)
        if (appConfigs[pkg] == null) {
            appConfigs[pkg] = "manual"
            saveAppConfigs(prefs)
        }
        sharedTargets = learnedTargets.toList()
    }

    private suspend fun searchAndClick(node: AccessibilityNodeInfo) {
        val targets = learnedTargets.toList()
        if (targets.isNotEmpty()) {
            for ((index, target) in targets.withIndex()) {
                OverlayService.instance?.showStatus("▶ ${index + 1}/${targets.size}")
                delay(500)

                if (clickSingleTarget(node, target)) {
                    OverlayService.instance?.showStatus("✅ ${index + 1}/${targets.size} 완료")
                    delay(2500)
                    continue
                }

                var lastSnapshot = captureScreenText(node)
                var clicked = false
                while (true) {
                    OverlayService.instance?.showStatus("🔄 ${index + 1}/${targets.size} 스와이프")
                    scrollDown(node)
                    delay(2500)

                    val retryRoot = rootInActiveWindow
                    if (retryRoot == null) {
                        Log.d(TAG, "rootInActiveWindow null for target ${index + 1}")
                        break
                    }
                    try {
                        if (clickSingleTarget(retryRoot, target)) {
                            clicked = true
                            OverlayService.instance?.showStatus("✅ ${index + 1}/${targets.size} 완료")
                            break
                        }

                        val newSnapshot = captureScreenText(retryRoot)
                        if (newSnapshot == lastSnapshot) {
                            Log.d(TAG, "Bottom reached for target ${index + 1}")
                            break
                        }
                        lastSnapshot = newSnapshot
                    } finally {
                        retryRoot.recycle()
                    }
                }

                if (clicked) delay(2500)
            }
            return
        }

        for (keyword in adKeywords) {
            if (findAndClickNode(node, keyword)) return
        }
        if (findAndClickNode(node, "출석체크")) return
    }

    private fun scrollDown(node: AccessibilityNodeInfo) {
        if (node.isScrollable) {
            node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            Log.d(TAG, "Scrolled via ACTION_SCROLL_FORWARD")
            return
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                if (child.isScrollable) {
                    child.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                    child.recycle()
                    Log.d(TAG, "Scrolled child via ACTION_SCROLL_FORWARD")
                    return
                }
                child.recycle()
            }
        }
        val rect = Rect()
        node.getBoundsInScreen(rect)
        val screenWidth = rect.width()
        val screenHeight = rect.height()
        if (screenWidth > 0 && screenHeight > 0) {
            performSwipeGesture(screenWidth, screenHeight)
        }
    }

    private fun performSwipeGesture(screenWidth: Int, screenHeight: Int) {
        val startX = screenWidth / 2f
        val startY = screenHeight * 0.8f
        val endY = screenHeight * 0.2f

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(startX, endY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 400L))
            .build()

        dispatchGesture(gesture, null, null)
        Log.d(TAG, "Swipe gesture $startY -> $endY")
    }

    private suspend fun clickSingleTarget(node: AccessibilityNodeInfo, target: LearnedTarget): Boolean {
        val vid = target.viewId
        if (vid != null) {
            val nodes = node.findAccessibilityNodeInfosByViewId(vid)
            for (targetNode in nodes) {
                val clickable = findClickableAncestor(targetNode)
                if (clickable != null) {
                    performSmartClick(clickable)
                    nodes.forEach { it.recycle() }
                    return true
                }
            }
            nodes.forEach { it.recycle() }
            } else if (target.text != null) {
                if (target.isNextToBased()) {
                    val searchText = target.text.removePrefix("NEXTTO:")
                    if (clickByNextTo(node, searchText)) return true
                } else if (target.isCoordsBased()) {
                    val parts = target.text.removePrefix("COORDS:").split(",")
                    if (parts.size == 2) {
                        val cx = parts[0].toIntOrNull()
                        val cy = parts[1].toIntOrNull()
                        if (cx != null && cy != null) {
                            return clickByCoord(node, cx, cy)
                        }
                    }
                } else if (target.isClassBased()) {
                    val className = target.text.removePrefix("CLASS:")
                    return clickByClass(node, className)
                } else {
                    return findAndClickNode(node, target.text)
                }
            }
        return false
    }

    private suspend fun clickByCoord(node: AccessibilityNodeInfo, x: Int, y: Int): Boolean {
        val clickable = findClickableNodeAt(node, x, y)
        if (clickable != null) {
            performSmartClick(clickable)
            clickable.recycle()
            return true
        }
        return false
    }

    private fun findClickableNodeAt(node: AccessibilityNodeInfo, x: Int, y: Int): AccessibilityNodeInfo? {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.contains(x, y)) {
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    val found = findClickableNodeAt(child, x, y)
                    if (found != null) {
                        child.recycle()
                        return found
                    }
                    child.recycle()
                }
            }
            if (node.isClickable) return node
        }
        return null
    }

    private suspend fun clickByClass(node: AccessibilityNodeInfo, className: String): Boolean {
        val clickable = findClickableNodeByClass(node, className)
        if (clickable != null) {
            performSmartClick(clickable)
            clickable.recycle()
            return true
        }
        return false
    }

    private fun findClickableNodeByClass(node: AccessibilityNodeInfo, className: String): AccessibilityNodeInfo? {
        if (node.className?.toString() == className && node.isClickable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val found = findClickableNodeByClass(child, className)
                if (found != null) {
                    child.recycle()
                    return found
                }
                child.recycle()
            }
        }
        return null
    }

    private suspend fun clickByNextTo(node: AccessibilityNodeInfo, siblingText: String): Boolean {
        val textNodes = node.findAccessibilityNodeInfosByText(siblingText)
        for (textNode in textNodes) {
            val parent = textNode.parent
            if (parent != null) {
                for (i in 0 until parent.childCount) {
                    val sibling = parent.getChild(i)
                    if (sibling != null && sibling != textNode) {
                        val clickable = findClickableAncestor(sibling)
                        if (clickable != null) {
                            performSmartClick(clickable)
                            if (sibling != clickable) clickable.recycle()
                            sibling.recycle()
                            textNode.recycle()
                            parent.recycle()
                            return true
                        }
                        sibling.recycle()
                    }
                }
                parent.recycle()
            }
            textNode.recycle()
        }
        return false
    }

    private fun findClickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isClickable) return node
        var p = node.parent
        while (p != null) {
            if (p.isClickable) return p
            p = p.parent
        }
        return null
    }

    private fun captureScreenText(node: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        captureTextRecursive(node, sb)
        return sb.toString()
    }

    private fun captureTextRecursive(node: AccessibilityNodeInfo, sb: StringBuilder) {
        if (node.text != null) {
            sb.append(node.text.toString())
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                captureTextRecursive(child, sb)
                child.recycle()
            }
        }
    }

    private suspend fun findAndClickNode(node: AccessibilityNodeInfo, keyword: String): Boolean {
        val nodesByText = node.findAccessibilityNodeInfosByText(keyword)
        for (targetNode in nodesByText) {
            if (targetNode.isClickable) {
                performSmartClick(targetNode)
                return true
            } else {
                var parent = targetNode.parent
                while (parent != null) {
                    if (parent.isClickable) {
                        performSmartClick(parent)
                        return true
                    }
                    parent = parent.parent
                }
            }
        }
        return false
    }

    private fun performSwipeDown(screenWidth: Int, screenHeight: Int) {
        val startX = screenWidth / 2f
        val startY = screenHeight * 0.8f
        val endY = screenHeight * 0.2f

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(startX, endY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 400L))
            .build()

        dispatchGesture(gesture, null, null)
        Log.d(TAG, "Swiped down from $startY to $endY")
    }

    private suspend fun performSmartClick(node: AccessibilityNodeInfo) {
        val rect = Rect()
        node.getBoundsInScreen(rect)

        val delayTime = Random.nextLong(500, 1500)
        delay(delayTime)

        val x = rect.centerX().toFloat()
        val y = rect.centerY().toFloat()

        Log.d(TAG, "Clicking at: ($x, $y) after ${delayTime}ms delay")

        val path = Path().apply {
            moveTo(x, y)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()

        dispatchGesture(gesture, null, null)
    }

    private fun saveLearnedTargets() {
        val lines = learnedTargets.map { target ->
            when {
                target.isIdBased() -> "ID:${target.viewId}"
                target.isCoordsBased() || target.isClassBased() || target.isNextToBased() -> "TEXT:${target.text}"
                else -> "TEXT:${target.text}"
            }
        }
        val prefs = getSharedPreferences("smarttech_auto", Context.MODE_PRIVATE)
        prefs.edit().putString("learned_targets", lines.joinToString("\n")).apply()
        sharedTargets = learnedTargets.toList()
    }

    private fun loadLearnedTargets() {
        val prefs = getSharedPreferences("smarttech_auto", Context.MODE_PRIVATE)
        val raw = prefs.getString("learned_targets", "") ?: ""
        learnedTargets.clear()
        if (raw.isNotEmpty()) {
            for (line in raw.split("\n")) {
                val target = when {
                    line.startsWith("ID:") -> LearnedTarget(line.removePrefix("ID:"), null)
                    line.startsWith("TEXT:") -> LearnedTarget(null, line.removePrefix("TEXT:"))
                    else -> null
                }
                if (target != null) learnedTargets.add(target)
            }
            Log.d(TAG, "Loaded ${learnedTargets.size} learned targets")
        }
        sharedTargets = learnedTargets.toList()
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
