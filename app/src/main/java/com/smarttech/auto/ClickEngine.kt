package com.smarttech.auto

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay
import kotlin.random.Random

class ClickEngine(private val service: AccessibilityService) {

    private val TAG = "ClickEngine"

    suspend fun searchAndClick(
        node: AccessibilityNodeInfo,
        targets: List<LearnedTarget>,
        adKeywords: List<String>,
        delayMin: Long,
        delayMax: Long
    ) {
        if (targets.isNotEmpty()) {
            showStatus("\u25B6 \uC2E4\uD589 \uC911... ${targets.size}\uAC1C \uB300\uC0C1")
            processTargets(node, targets, delayMin, delayMax)
            showStatus("\u2705 \uC804\uCCB4 \uC644\uB8CC")
            return
        }

        for (keyword in adKeywords) {
            val adNode = TargetMatcher.findNodeByText(node, keyword) ?: continue
            performSmartClick(adNode, delayMin, delayMax)
            adNode.recycle()
            showStatus("\u2705 \uAD11\uACE0 \uD074\uB9AD: $keyword")
            return
        }
        val checkinNode = TargetMatcher.findNodeByText(node, "\uCD9C\uC11D\uCCB4\uD06C") ?: run {
            showStatus("\u23F8 \uD074\uB9AD\uD560 \uB300\uC0C1\uC774 \uC5C6\uC2B5\uB2C8\uB2E4")
            return
        }
        performSmartClick(checkinNode, delayMin, delayMax)
        checkinNode.recycle()
        showStatus("\u2705 \uCD9C\uC11D\uCCB4\uD06C \uD074\uB9AD")
    }

    private suspend fun processTargets(
        node: AccessibilityNodeInfo,
        targets: List<LearnedTarget>,
        delayMin: Long,
        delayMax: Long
    ) {
        for ((index, target) in targets.withIndex()) {
            val strategy = when {
                target.isIdBased() -> "ID:${target.viewId}"
                target.isPathBased() -> "\uACBD\uB85C"
                target.isCoordsBased() -> "\uC88C\uD45C"
                target.isClassBased() -> "\uD0C0\uC785"
                target.isNextToBased() -> "\uC66C\uD14D\uC2A4\uD2B8"
                else -> "\uD14D\uC2A4\uD2B8:${target.text?.take(20)}"
            }
            showStatus("\uD83D\uDD0D ${index + 1}/$targets.size $strategy")
            delay(800)

            val foundNode = TargetMatcher.findTargetNode(node, target)
            if (foundNode != null) {
                showStatus("\uD074\uB9AD ${index + 1}/$targets.size")
                performSmartClick(foundNode, delayMin, delayMax, target.holdMs)
                foundNode.recycle()
                showStatus("\u2705 ${index + 1}/$targets.size \uC644\uB8CC")
                delay(2500)
                continue
            }

            showStatus("\u274C ${index + 1}/$targets.size \uCC3E\uC9C0\uBABB\uD568, \uC2A4\uD06C\uB864\uC911...")
            var lastSnapshot = TargetMatcher.captureScreenText(node)
            var clicked = false
            while (true) {
                scrollDown(node)
                delay(2500)

                val retryRoot = service.rootInActiveWindow ?: break
                try {
                    val retryNode = TargetMatcher.findTargetNode(retryRoot, target)
                    if (retryNode != null) {
                        performSmartClick(retryNode, delayMin, delayMax, target.holdMs)
                        retryNode.recycle()
                        clicked = true
                        showStatus("\u2705 ${index + 1}/$targets.size \uC644\uB8CC")
                        break
                    }
                    val newSnapshot = TargetMatcher.captureScreenText(retryRoot)
                    if (newSnapshot == lastSnapshot) break
                    lastSnapshot = newSnapshot
                } finally {
                    retryRoot.recycle()
                }
            }
            if (clicked) delay(2500) else showStatus("\u274C ${index + 1}/$targets.size \uC2A4\uD06C\uB864\uAE4C\uC9C0 \uC2E4\uD328")
        }
    }

    fun scrollDown(node: AccessibilityNodeInfo) {
        if (node.isScrollable) {
            node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            return
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (child.isScrollable) {
                child.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                child.recycle()
                return
            }
            child.recycle()
        }
        val rect = Rect()
        node.getBoundsInScreen(rect)
        val w = rect.width()
        val h = rect.height()
        if (w > 0 && h > 0) {
            performSwipeGesture(w.toFloat(), h.toFloat())
        }
    }

    suspend fun performSmartClick(node: AccessibilityNodeInfo, delayMin: Long, delayMax: Long, holdMs: Long? = null) {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        val delayTime = Random.nextLong(delayMin, delayMax)
        delay(delayTime)

        val x = rect.centerX().toFloat()
        val y = rect.centerY().toFloat()

        if (holdMs != null) {
            Log.d(TAG, "Long press at ($x,$y) for ${holdMs}ms after ${delayTime}ms")
            if (tryPerformLongClick(node)) {
                Log.d(TAG, "ACTION_LONG_CLICK succeeded on node")
                return
            }
            val clickableParent = findLongClickableParent(node)
            if (clickableParent != null) {
                val performed = clickableParent.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
                clickableParent.recycle()
                if (performed) {
                    Log.d(TAG, "ACTION_LONG_CLICK succeeded on parent")
                    return
                }
            }
            Log.d(TAG, "ACTION_LONG_CLICK failed, falling back to gesture")
        } else {
            Log.d(TAG, "Click at ($x,$y) after ${delayTime}ms")
        }

        val duration = holdMs ?: 50
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        service.dispatchGesture(gesture, null, null)
    }

    private fun tryPerformLongClick(node: AccessibilityNodeInfo): Boolean {
        return node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
    }

    private fun findLongClickableParent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current = node
        val visited = mutableListOf<AccessibilityNodeInfo>()
        while (true) {
            val parent = current.parent ?: break
            visited.add(current)
            if (parent.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)) {
                visited.forEach { it.recycle() }
                return parent
            }
            current = parent
        }
        visited.forEach { it.recycle() }
        return null
    }

    private fun performSwipeGesture(screenWidth: Float, screenHeight: Float) {
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
        service.dispatchGesture(gesture, null, null)
        Log.d(TAG, "Swipe $startY -> $endY")
    }

    private fun showStatus(text: String) {
        OverlayService.instance?.showStatus(text)
    }
}
