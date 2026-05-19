package com.smarttech.auto

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*
import kotlin.random.Random

class AutoClickService : AccessibilityService() {

    companion object {
        private const val TAG = "AutoClickService"
        var isRunning = false
    }

    private val clickKeywords = listOf(
        "광고 닫기", "그만보기", "X", "Close", "Skip", "보상받기", "보상 받기", "확인", "완료"
    )

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var isProcessing = false

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isRunning || event == null) return

        // 윈도우 상태나 콘텐츠 변경 시에만 노드 검색
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            if (isProcessing) return
            
            val rootNode = rootInActiveWindow ?: return
            
            serviceScope.launch {
                isProcessing = true
                try {
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

    private suspend fun searchAndClick(node: AccessibilityNodeInfo) {
        // 1. 텍스트 기반 검색
        for (keyword in clickKeywords) {
            val nodesByText = node.findAccessibilityNodeInfosByText(keyword)
            for (targetNode in nodesByText) {
                if (targetNode.isClickable) {
                    performSmartClick(targetNode)
                    return // 한 번에 하나만 클릭
                } else {
                    // 부모 노드가 클릭 가능한지 확인
                    var parent = targetNode.parent
                    while (parent != null) {
                        if (parent.isClickable) {
                            performSmartClick(parent)
                            return
                        }
                        parent = parent.parent
                    }
                }
            }
        }
        
        // 2. 만약 특정 ID 기반 추적을 추가한다면 여기서 처리 (예: "com.example.app:id/close_btn")
        // val nodesById = node.findAccessibilityNodeInfosByViewId("com.example.app:id/close_btn")
    }

    private suspend fun performSmartClick(node: AccessibilityNodeInfo) {
        val rect = Rect()
        node.getBoundsInScreen(rect)

        // 휴먼 라이크(안티-디텍션) 행동: 랜덤 딜레이 (0.5초 ~ 1.5초)
        val delayTime = Random.nextLong(500, 1500)
        delay(delayTime)

        // 중심점에서 랜덤하게 오프셋 추가 (±10 픽셀)
        val x = rect.centerX() + Random.nextInt(-10, 10)
        val y = rect.centerY() + Random.nextInt(-10, 10)

        // 안전선: 화면 밖으로 벗어나지 않게 처리
        val safeX = x.coerceIn(rect.left, rect.right).toFloat()
        val safeY = y.coerceIn(rect.top, rect.bottom).toFloat()

        Log.d(TAG, "Clicking at: ($safeX, $safeY) after ${delayTime}ms delay")

        val path = Path().apply {
            moveTo(safeX, safeY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50)) // 50ms 동안 터치
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Click gesture completed successfully")
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Click gesture cancelled")
            }
        }, null)
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
