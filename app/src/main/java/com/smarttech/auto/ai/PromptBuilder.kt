package com.smarttech.auto.ai

import com.smarttech.auto.model.RecordedAction

object PromptBuilder {

    fun build(userRequest: String): String = """
You are a macro script generator for Android automation.
Generate a JSON array of action steps based on the user's request.

SUPPORTED ACTIONS (use Korean for UI text matching):

1. BASIC ACTIONS:
   {"action": "click", "targetType": "text", "targetValue": "버튼텍스트"}
   {"action": "click", "targetType": "id", "targetValue": "com.example:id/button"}
   {"action": "click", "targetType": "coordinate", "x": 500, "y": 1000}
   {"action": "click", "targetType": "description", "description": "텍스트가 포함된 UI 요소 설명"}
   {"action": "wait", "ms": 2000}
   {"action": "type", "text": "입력할 내용"}
   {"action": "swipe", "fromX": 500, "fromY": 1500, "toX": 500, "toY": 300}
   {"action": "scroll", "direction": "down"}
   {"action": "scroll", "direction": "up"}
   {"action": "back"}
   {"action": "home"}
   {"action": "launch_app", "packageName": "com.google.android.youtube"}

2. CONDITIONAL ACTIONS:
   {"action": "click_if_exists", "targetType": "text", "targetValue": "X", "description": "X버튼이 있으면 닫기"}
   {"action": "click_if_exists", "targetType": "text", "targetValue": "닫기"}
   {"action": "click_if_exists", "targetType": "id", "targetValue": "com.example:id/close_button"}

3. REPEATED CLICK UNTIL CONDITION:
   {"action": "click_until", "targetType": "text", "targetValue": "버튼텍스트",
    "conditionType": "text_gone", "conditionValue": "0",
    "maxAttempts": 20, "intervalMs": 1000,
    "description": "0이 사라질때까지 버튼 클릭"}

4. LOOP:
   {"action": "loop", "count": 3, "steps": [ ... 하위 액션들 ... ]}

5. WAIT UNTIL:
   {"action": "wait_until", "conditionType": "text_appears",
    "conditionValue": "시작", "timeoutMs": 10000}

6. FIND AND INTERACT:
   {"action": "find_and_click", "targetType": "description",
    "description": "재생버튼 또는 광고 재생 버튼",
    "maxAttempts": 5, "intervalMs": 2000}

IMPORTANT RULES:
- Output ONLY the JSON array, no markdown, no explanation.
- Use Korean text values for UI element matching.
- For "click_until", always include maxAttempts (max 30) and intervalMs.
- Use "click_if_exists" for optional elements like ad close buttons.
- Add appropriate wait times between actions (500ms-3000ms).
- For video ads: wait first, then try to find and click close/X button.
- Break complex tasks into clear sequential steps.
- All text matching values should be in Korean (the app's UI language).

User request: $userRequest

Output ONLY valid JSON array:
""".trimIndent()

    fun buildFromRecording(actions: List<RecordedAction>): String {
        val timeline = buildString {
            appendLine("The user recorded the following actions on their Android device:")
            appendLine()
            actions.forEachIndexed { i, a ->
                when (a.type) {
                    RecordedAction.Type.TOUCH -> {
                        appendLine("${i + 1}. User touched at coordinate (${a.x}, ${a.y})")
                        a.uiText?.let { appendLine("   UI text found: '$it'") }
                        a.uiId?.let { appendLine("   UI ID: '$it'") }
                        a.uiClass?.let { appendLine("   UI class: '$it'") }
                    }
                    RecordedAction.Type.NOTE -> {
                        appendLine("${i + 1}. User note: \"${a.note}\"")
                    }
                }
            }
        }

        return """
You are a macro script generator for Android automation.
Generate a JSON array of action steps based on the user's recorded actions and notes.

$timeline

SUPPORTED ACTIONS (use Korean for UI text matching):

1. BASIC ACTIONS:
   {"action": "click", "targetType": "text", "targetValue": "버튼텍스트"}
   {"action": "click", "targetType": "id", "targetValue": "com.example:id/button"}
   {"action": "click", "targetType": "coordinate", "x": 500, "y": 1000}
   {"action": "click", "targetType": "description", "description": "설명"}
   {"action": "wait", "ms": 2000}
   {"action": "type", "text": "입력할 내용"}
   {"action": "swipe", "fromX": 500, "fromY": 1500, "toX": 500, "toY": 300}
   {"action": "scroll", "direction": "down"}
   {"action": "back"}
   {"action": "home"}
   {"action": "launch_app", "packageName": "com.example.app"}

2. CONDITIONAL:
   {"action": "click_if_exists", "targetType": "text", "targetValue": "X", "description": "있으면 닫기"}

3. REPEATED CLICK:
   {"action": "click_until", "targetType": "text", "targetValue": "버튼",
    "conditionType": "text_gone", "conditionValue": "0",
    "maxAttempts": 20, "intervalMs": 1000}

4. LOOP:
   {"action": "loop", "count": 3, "steps": [...]}

RULES:
- Output ONLY the JSON array, no markdown.
- Use Korean text for UI matching.
- Use the coordinates from recordings as fallback, prefer text/ID matching.
- Add appropriate wait times between actions.
- Interpret the user's notes as intent and generate optimized actions.
- For "click_until", include maxAttempts and intervalMs.
- Break complex tasks into clear steps.

Output ONLY valid JSON array:
""".trimIndent()
    }
}
