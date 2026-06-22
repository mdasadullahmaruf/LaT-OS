// com/lat/os/automation/DeviceAutomator.kt
package com.lat.os.automation

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

object DeviceAutomator {

    private var serviceInstance: VoiceAccessibilityService? = null

    fun setService(service: VoiceAccessibilityService?) {
        serviceInstance = service
    }

    fun openApp(context: Context, packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    fun pressBack() {
        serviceInstance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    }

    fun pressHome() {
        serviceInstance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
    }

    fun scrollDown() {
        val service = serviceInstance ?: return
        val root = service.rootInActiveWindow ?: return
        val scrollable = findScrollableNode(root)
        if (scrollable != null) {
            scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        } else {
            // Fallback gesture scroll
            val displayMetrics = service.resources.displayMetrics
            val midX = displayMetrics.widthPixels / 2f
            val startY = displayMetrics.heightPixels * 0.7f
            val endY = displayMetrics.heightPixels * 0.3f
            performSwipe(service, midX, startY, midX, endY, 300)
        }
    }

    fun scrollUp() {
        val service = serviceInstance ?: return
        val root = service.rootInActiveWindow ?: return
        val scrollable = findScrollableNode(root)
        if (scrollable != null) {
            scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
        } else {
            val displayMetrics = service.resources.displayMetrics
            val midX = displayMetrics.widthPixels / 2f
            val startY = displayMetrics.heightPixels * 0.3f
            val endY = displayMetrics.heightPixels * 0.7f
            performSwipe(service, midX, startY, midX, endY, 300)
        }
    }

    fun tapOnText(text: String): Boolean {
        val service = serviceInstance ?: return false
        val root = service.rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText(text)
        if (nodes.isNotEmpty()) {
            nodes[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return true
        }
        return false
    }

    fun typeText(text: String) {
        val service = serviceInstance ?: return
        val root = service.rootInActiveWindow ?: return
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findScrollableNode(child)
            if (result != null) return result
        }
        return null
    }

    private fun performSwipe(service: VoiceAccessibilityService,
                             x1: Float, y1: Float, x2: Float, y2: Float, duration: Long) {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        service.dispatchGesture(gesture, null, null)
    }
}
