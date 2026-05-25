package com.smarttech.auto.model

data class RecordedAction(
    val type: Type,
    val x: Int? = null,
    val y: Int? = null,
    val uiText: String? = null,
    val uiId: String? = null,
    val uiClass: String? = null,
    val note: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    enum class Type { TOUCH, NOTE }
}
