package com.smarttech.auto

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

object TargetMatcher {

    fun findTargetNode(node: AccessibilityNodeInfo, target: LearnedTarget): AccessibilityNodeInfo? {
        val vid = target.viewId
        if (vid != null) {
            val nodes = node.findAccessibilityNodeInfosByViewId(vid)
            for (targetNode in nodes) {
                val clickable = findClickableAncestor(targetNode)
                if (clickable != null) {
                    if (clickable != targetNode) targetNode.recycle()
                    nodes.forEach { it.recycle() }
                    return clickable
                }
                targetNode.recycle()
            }
            nodes.forEach { it.recycle() }
            return null
        }
        val text = target.text ?: return null
        return when {
            target.isNextToBased() -> findSiblingNode(node, text.removePrefix("NEXTTO:"))
            target.isPathBased() -> findNodeByPath(node, text.removePrefix("PATH:"))
            target.isCoordsBased() -> {
                val parts = text.removePrefix("COORDS:").split(",")
                if (parts.size == 2) {
                    val cx = parts[0].toIntOrNull() ?: return null
                    val cy = parts[1].toIntOrNull() ?: return null
                    findClickableNodeAt(node, cx, cy)
                } else null
            }
            target.isClassBased() -> findClickableNodeByClass(node, text.removePrefix("CLASS:"))
            else -> findNodeByText(node, text)
        }
    }

    fun findNodeByPath(root: AccessibilityNodeInfo, pathStr: String): AccessibilityNodeInfo? {
        val indices = pathStr.split(",").mapNotNull { it.toIntOrNull() }
        if (indices.isEmpty()) return null

        var current = root
        var node: AccessibilityNodeInfo? = null

        for ((step, index) in indices.withIndex()) {
            if (index < 0 || index >= current.childCount) {
                if (step > 0) current.recycle()
                return null
            }
            node = current.getChild(index)
            if (node == null) {
                if (step > 0) current.recycle()
                return null
            }
            if (step > 0) current.recycle()
            current = node
        }

        val clickable = findClickableAncestor(current)
        if (clickable != null && clickable !== current) {
            current.recycle()
            return clickable
        }
        return current
    }

    fun findClickableNodeAt(node: AccessibilityNodeInfo, x: Int, y: Int): AccessibilityNodeInfo? {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (!rect.contains(x, y)) return null
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findClickableNodeAt(child, x, y)
            if (found != null) {
                child.recycle()
                return found
            }
            child.recycle()
        }
        return if (node.isClickable) node else null
    }

    fun findClickableNodeByClass(node: AccessibilityNodeInfo, className: String): AccessibilityNodeInfo? {
        if (node.className?.toString() == className && node.isClickable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findClickableNodeByClass(child, className)
            if (found != null) {
                child.recycle()
                return found
            }
            child.recycle()
        }
        return null
    }

    fun findSiblingNode(node: AccessibilityNodeInfo, siblingText: String): AccessibilityNodeInfo? {
        val textNodes = node.findAccessibilityNodeInfosByText(siblingText)
        for (textNode in textNodes) {
            val parent = textNode.parent ?: continue
            for (i in 0 until parent.childCount) {
                val sibling = parent.getChild(i)
                if (sibling != null && sibling != textNode) {
                    val clickable = findClickableAncestor(sibling)
                    if (clickable != null) {
                        if (clickable != sibling) sibling.recycle()
                        textNode.recycle()
                        parent.recycle()
                        return clickable
                    }
                    sibling.recycle()
                }
            }
            textNode.recycle()
            parent.recycle()
        }
        return null
    }

    fun findClickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isClickable) return node
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable) return parent
            parent = parent.parent
        }
        return null
    }

    fun captureScreenText(node: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        captureTextRecursive(node, sb)
        return sb.toString()
    }

    private fun captureTextRecursive(node: AccessibilityNodeInfo, sb: StringBuilder) {
        node.text?.let { sb.append(it) }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            captureTextRecursive(child, sb)
            child.recycle()
        }
    }

    fun findNodeByText(node: AccessibilityNodeInfo, keyword: String): AccessibilityNodeInfo? {
        val nodes = node.findAccessibilityNodeInfosByText(keyword)
        for (targetNode in nodes) {
            if (targetNode.isClickable) return targetNode
            val clickable = findClickableAncestor(targetNode)
            if (clickable != null) {
                if (clickable != targetNode) targetNode.recycle()
                return clickable
            }
        }
        return null
    }
}
