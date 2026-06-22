// com/lat/os/automation/ScreenScraper.kt
package com.lat.os.automation

import android.view.accessibility.AccessibilityNodeInfo

object ScreenScraper {
    fun scrapeScreen(): String {
        val service = VoiceAccessibilityService.instance ?: return "No accessibility service"
        val root = service.rootInActiveWindow ?: return "No active window"
        val sb = StringBuilder()
        traverseNode(root, sb, 0)
        root.recycle()
        return sb.toString()
    }

    private fun traverseNode(node: AccessibilityNodeInfo, sb: StringBuilder, depth: Int) {
        val indent = "  ".repeat(depth)
        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        val className = node.className?.toString() ?: ""
        if (text.isNotBlank() || desc.isNotBlank()) {
            sb.appendLine("$indent- Text: \"$text\", Desc: \"$desc\", Class: $className")
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            traverseNode(child, sb, depth + 1)
            child.recycle()
        }
    }
}
