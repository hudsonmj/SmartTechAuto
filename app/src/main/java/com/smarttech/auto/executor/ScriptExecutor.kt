package com.smarttech.auto.executor

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.smarttech.auto.AutoClickService
import com.smarttech.auto.OverlayService
import com.smarttech.auto.TargetMatcher
import com.smarttech.auto.model.ActionStep
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.math.abs

class ScriptExecutor(private val service: AccessibilityService) {

    private val TAG = "ScriptExecutor"
    private var isCancelled = false
    private var currentStepIndex = 0
    private val mainHandler = Handler(Looper.getMainLooper())

    fun cancel() {
        isCancelled = true
    }

    suspend fun execute(steps: List<ActionStep>, onStepChanged: suspend (Int, ActionStep) -> Unit = { _, _ -> }) {
        isCancelled = false
        currentStepIndex = 0

        for ((index, step) in steps.withIndex()) {
            if (isCancelled) {
                showStatus("⏹ 중지됨")
                return
            }
            currentStepIndex = index
            onStepChanged(index, step)
            executeStep(step)
        }
        if (AutoClickService.autoClosePopups) {
            showStatus("🚫 팝업닫기 대기 (3초)")
            delay(3000)
            tryClosePopups()
        }
        showStatus("✅ 매크로 완료")
    }

    private suspend fun executeStep(step: ActionStep) {
        Log.d(TAG, "Executing: ${step.action} | ${step.description ?: step.targetValue ?: ""}")
        showStatus("▶ ${step.description ?: step.action}")

        when (step.action) {
            "click" -> executeClick(step)
            "hold" -> executeHold(step)
            "click_if_exists" -> executeClickIfExists(step)
            "click_until" -> executeClickUntil(step)
            "find_and_click" -> executeFindAndClick(step)
            "wait" -> executeWait(step)
            "type" -> executeType(step)
            "swipe" -> executeSwipe(step)
            "scroll" -> executeScroll(step)
            "back" -> executeBack()
            "home" -> executeHome()
            "launch_app" -> executeLaunchApp(step)
            "wait_until" -> executeWaitUntil(step)
            "loop" -> executeLoop(step)
            else -> Log.w(TAG, "Unknown action: ${step.action}")
        }
    }

    private suspend fun executeClick(step: ActionStep) {
        val root = service.rootInActiveWindow ?: return
        try {
            when (step.targetType) {
                "text" -> {
                    val text = step.targetValue ?: return
                    val node = TargetMatcher.findNodeByText(root, text) 
                        ?: findNodeByTextContains(root, text)
                    if (node != null) {
                        performClick(node)
                        node.recycle()
                    } else {
                        showStatus("⚠ '$text' 찾을 수 없음")
                    }
                }
                "id" -> {
                    val id = step.targetValue ?: return
                    val node = findViewById(root, id)
                    if (node != null) {
                        performClick(node)
                        node.recycle()
                    }
                }
                "coordinate" -> {
                    val x = step.x ?: 500
                    val y = step.y ?: 500
                    val count = step.count ?: 1
                    for (i in 0 until count) {
                        if (isCancelled) return
                        if (count > 1) showStatus("👆 클릭 ${i + 1}/$count")
                        performClick(x.toFloat(), y.toFloat())
                        if (i < count - 1) delay(300)
                    }
                }
                "description" -> {
                    val desc = step.description ?: step.targetValue ?: return
                    val node = findNodeByDescription(root, desc)
                    if (node != null) {
                        performClick(node)
                        node.recycle()
                    } else {
                        showStatus("⚠ '$desc' 찾을 수 없음")
                    }
                }
            }
        } finally {
            root.recycle()
        }
    }

    private suspend fun executeHold(step: ActionStep) {
        val holdMs = step.ms ?: 1000L
        val count = step.count ?: 1
        val root = service.rootInActiveWindow ?: return
        try {
            when (step.targetType) {
                "coordinate" -> {
                    val x = step.x ?: 500
                    val y = step.y ?: 500
                    for (i in 0 until count) {
                        if (isCancelled) return
                        showStatus("✊ 길게누르기 ${i + 1}/$count (${holdMs}ms)")
                        performHold(x.toFloat(), y.toFloat(), holdMs)
                        delay(holdMs)
                        if (i < count - 1) delay(500)
                    }
                }
                else -> {
                    val text = step.targetValue ?: step.description ?: return
                    val node = findNodeByDescription(root, text)
                    if (node != null) {
                        val rect = Rect()
                        node.getBoundsInScreen(rect)
                        for (i in 0 until count) {
                            if (isCancelled) return
                            showStatus("✊ 길게누르기 ${i + 1}/$count")
                            performHold(rect.centerX().toFloat(), rect.centerY().toFloat(), holdMs)
                            delay(holdMs)
                            if (i < count - 1) delay(500)
                        }
                        node.recycle()
                    }
                }
            }
        } finally {
            root.recycle()
        }
    }

    private fun performHold(x: Float, y: Float, ms: Long) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, ms))
            .build()
        service.dispatchGesture(gesture, null, null)
    }

    private suspend fun executeClickIfExists(step: ActionStep) {
        delay(500)
        val root = service.rootInActiveWindow ?: return
        try {
            val text = step.targetValue ?: return
            val node = TargetMatcher.findNodeByText(root, text)
                ?: findNodeByTextContains(root, text)
            if (node != null) {
                showStatus("🔍 '${text}' 발견, 클릭")
                performClick(node)
                node.recycle()
                delay(1000)
            } else {
                showStatus("⏭ '${text}' 없음, 건너뜀")
            }
        } finally {
            root.recycle()
        }
    }

    private suspend fun executeClickUntil(step: ActionStep) {
        val targetText = step.targetValue ?: return
        val maxAttempts = step.maxAttempts ?: 20
        val interval = step.intervalMs ?: 1000L
        val conditionText = step.conditionValue

        for (i in 0 until maxAttempts) {
            if (isCancelled) return
            showStatus("🔄 ${i + 1}/$maxAttempts '$targetText' 클릭 중...")

            val root = service.rootInActiveWindow ?: return
            try {
                val node = TargetMatcher.findNodeByText(root, targetText)
                    ?: findNodeByTextContains(root, targetText)
                if (node != null) {
                    performClick(node)
                    node.recycle()
                }

                delay(interval)

                if (conditionText != null) {
                    val conditionMet = findNodeByTextContains(root, conditionText) == null
                    if (conditionMet) {
                        showStatus("✅ 조건 충족 ('${conditionText}' 사라짐)")
                        return
                    }
                }
            } finally {
                root.recycle()
            }
        }
        showStatus("⏹ 최대 시도 도달")
    }

    private suspend fun executeFindAndClick(step: ActionStep) {
        val desc = step.description ?: step.targetValue ?: return
        val maxAttempts = step.maxAttempts ?: 5
        val interval = step.intervalMs ?: 2000L

        for (i in 0 until maxAttempts) {
            if (isCancelled) return
            showStatus("🔍 ${i + 1}/$maxAttempts 찾는 중: $desc")

            val root = service.rootInActiveWindow ?: return
            try {
                val node = findNodeByDescription(root, desc)
                if (node != null) {
                    showStatus("✅ 발견, 클릭")
                    performClick(node)
                    node.recycle()
                    return
                }
            } finally {
                root.recycle()
            }
            delay(interval)
        }
        showStatus("⚠ $desc 찾기 실패")
    }

    private suspend fun executeWait(step: ActionStep) {
        val ms = step.ms ?: 1000L
        showStatus("⏳ ${ms}ms 대기")
        delay(ms)
    }

    private suspend fun executeType(step: ActionStep) {
        val text = step.text ?: return
        val root = service.rootInActiveWindow ?: return
        try {
            val focused = findFocusedNode(root)
            if (focused != null) {
                val args = android.os.Bundle().apply { putCharSequence("text", text) }
                focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                focused.recycle()
            }
        } finally {
            root.recycle()
        }
    }

    private suspend fun executeSwipe(step: ActionStep) {
        val fromX = (step.fromX ?: 500).toFloat()
        val fromY = (step.fromY ?: 1500).toFloat()
        val toX = (step.toX ?: 500).toFloat()
        val toY = (step.toY ?: 300).toFloat()
        val path = Path().apply { moveTo(fromX, fromY); lineTo(toX, toY) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 400L))
            .build()
        service.dispatchGesture(gesture, null, null)
        delay(1000)
    }

    private suspend fun executeScroll(step: ActionStep) {
        val root = service.rootInActiveWindow ?: return
        try {
            val direction = step.direction ?: "down"
            val action = if (direction == "up") AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD

            if (root.isScrollable) {
                root.performAction(action)
            } else {
                scrollInChildren(root, action)
            }
        } finally {
            root.recycle()
        }
        delay(1000)
    }

    private fun scrollInChildren(node: AccessibilityNodeInfo, action: Int) {
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (child.isScrollable) {
                child.performAction(action)
                child.recycle()
                return
            }
            child.recycle()
        }
    }

    private suspend fun executeBack() {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        delay(500)
    }

    private suspend fun executeHome() {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        delay(500)
    }

    private suspend fun executeLaunchApp(step: ActionStep) {
        val pkg = step.packageName ?: return
        val intent = service.packageManager.getLaunchIntentForPackage(pkg)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            service.startActivity(intent)
            delay(2000)
        }
    }

    private suspend fun executeWaitUntil(step: ActionStep) {
        val value = step.conditionValue ?: return
        val timeout = step.ms ?: 10000L
        val start = System.currentTimeMillis()

        while (System.currentTimeMillis() - start < timeout) {
            if (isCancelled) return
            val root = service.rootInActiveWindow ?: return
            try {
                val found = findNodeByTextContains(root, value)
                if (found != null) {
                    found.recycle()
                    showStatus("✅ '$value' 나타남")
                    return
                }
            } finally {
                root.recycle()
            }
            delay(500)
        }
        showStatus("⏰ '$value' 대기 타임아웃")
    }

    private suspend fun executeLoop(step: ActionStep) {
        val count = step.count ?: 1
        val subSteps = step.steps ?: return
        for (i in 0 until count) {
            if (isCancelled) return
            showStatus("🔄 반복 ${i + 1}/$count")
            for (sub in subSteps) {
                if (isCancelled) return
                executeStep(sub)
            }
        }
    }

    private fun performClick(node: AccessibilityNodeInfo) {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        performClick(rect.centerX().toFloat(), rect.centerY().toFloat())
    }

    private fun performClick(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 50L))
            .build()
        service.dispatchGesture(gesture, null, null)
    }

    private fun findViewById(node: AccessibilityNodeInfo, id: String): AccessibilityNodeInfo? {
        if (node.viewIdResourceName == id) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findViewById(child, id)
            if (result != null) { child.recycle(); return result }
            child.recycle()
        }
        return null
    }

    private fun findNodeByTextContains(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val nodeText = node.text?.toString()
        val contentDesc = node.contentDescription?.toString()
        if (nodeText?.contains(text, ignoreCase = true) == true ||
            contentDesc?.contains(text, ignoreCase = true) == true
        ) return node

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findNodeByTextContains(child, text)
            if (result != null) { child.recycle(); return result }
            child.recycle()
        }
        return null
    }

    private fun findNodeByDescription(node: AccessibilityNodeInfo, description: String): AccessibilityNodeInfo? {
        val nodeText = node.text?.toString()
        val contentDesc = node.contentDescription?.toString()
        val className = node.className?.toString()

        val desc = description.lowercase()
        if (nodeText?.lowercase()?.contains(desc) == true ||
            contentDesc?.lowercase()?.contains(desc) == true ||
            className?.lowercase()?.contains(desc) == true
        ) return node

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findNodeByDescription(child, description)
            if (result != null) { child.recycle(); return result }
            child.recycle()
        }
        return null
    }

    private fun findFocusedNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isFocused) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findFocusedNode(child)
            if (result != null) { child.recycle(); return result }
            child.recycle()
        }
        return null
    }

    private suspend fun tryClosePopups() {
        val root = service.rootInActiveWindow ?: return
        try {
            val keywords = listOf("X", "닫기", ">>", "Close", "Skip", "건너뛰기",
                "그만보기", "광고 닫기", "보상받기", "확인", "완료", "취소")

            for (keyword in keywords) {
                if (isCancelled) return
                val node = findNodeByTextContains(root, keyword)
                if (node != null) {
                    val clickable = findClickableParent(node)
                    if (clickable != null) {
                        showStatus("🚫 팝업닫기: $keyword")
                        performClick(clickable)
                        clickable.recycle()
                        node.recycle()
                        delay(800)
                        return
                    }
                    node.recycle()
                }
            }
        } finally {
            root.recycle()
        }
    }

    private fun findClickableParent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current = node
        if (current.isClickable) return current
        while (true) {
            val parent = current.parent ?: return null
            if (parent.isClickable) return parent
            current = parent
        }
    }

    private fun showStatus(text: String) {
        mainHandler.post {
            OverlayService.instance?.showStatus(text)
        }
    }
}
