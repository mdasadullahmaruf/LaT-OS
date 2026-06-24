package com.lat.os.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo

object DeviceAutomator {

    private var serviceInstance: VoiceAccessibilityService? = null

    fun setService(service: VoiceAccessibilityService?) {
        serviceInstance = service
    }

    // ── APP OPENING ──────────────────────────────────────────────
    fun openApp(context: Context, packageName: String) {
        val intent = context.packageManager
            .getLaunchIntentForPackage(packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    // ── GLOBAL ACTIONS ───────────────────────────────────────────
    fun pressBack() {
        serviceInstance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    }

    fun pressHome() {
        serviceInstance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
    }

    fun openRecents() {
        serviceInstance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
    }

    fun openNotifications() {
        serviceInstance?.performGlobalAction(
            AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
    }

    fun openQuickSettings() {
        serviceInstance?.performGlobalAction(
            AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
    }

    fun takeScreenshot() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            serviceInstance?.performGlobalAction(
                AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
        }
    }

    // ── SCROLL ───────────────────────────────────────────────────
    fun scrollDown() {
        val service = serviceInstance ?: return
        val root = service.rootInActiveWindow
        val scrollable = root?.let { findScrollableNode(it) }
        if (scrollable != null) {
            scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        } else {
            val w = service.resources.displayMetrics.widthPixels
            val h = service.resources.displayMetrics.heightPixels
            performSwipe(service, w / 2f, h * 0.7f, w / 2f, h * 0.3f, 300)
        }
    }

    fun scrollUp() {
        val service = serviceInstance ?: return
        val root = service.rootInActiveWindow
        val scrollable = root?.let { findScrollableNode(it) }
        if (scrollable != null) {
            scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
        } else {
            val w = service.resources.displayMetrics.widthPixels
            val h = service.resources.displayMetrics.heightPixels
            performSwipe(service, w / 2f, h * 0.3f, w / 2f, h * 0.7f, 300)
        }
    }

    fun scrollLeft() {
        val service = serviceInstance ?: return
        val w = service.resources.displayMetrics.widthPixels
        val h = service.resources.displayMetrics.heightPixels
        performSwipe(service, w * 0.8f, h / 2f, w * 0.2f, h / 2f, 300)
    }

    fun scrollRight() {
        val service = serviceInstance ?: return
        val w = service.resources.displayMetrics.widthPixels
        val h = service.resources.displayMetrics.heightPixels
        performSwipe(service, w * 0.2f, h / 2f, w * 0.8f, h / 2f, 300)
    }

    // ── TAP BY SCREEN POSITION ───────────────────────────────────
    fun tapPosition(position: String) {
        val service = serviceInstance ?: return
        val w = service.resources.displayMetrics.widthPixels.toFloat()
        val h = service.resources.displayMetrics.heightPixels.toFloat()

        val (x, y) = when (position) {
            "top_left"      -> Pair(w * 0.15f, h * 0.15f)
            "top_center"    -> Pair(w * 0.50f, h * 0.15f)
            "top_right"     -> Pair(w * 0.85f, h * 0.15f)
            "center_left"   -> Pair(w * 0.15f, h * 0.50f)
            "center"        -> Pair(w * 0.50f, h * 0.50f)
            "center_right"  -> Pair(w * 0.85f, h * 0.50f)
            "bottom_left"   -> Pair(w * 0.15f, h * 0.85f)
            "bottom_center" -> Pair(w * 0.50f, h * 0.85f)
            "bottom_right"  -> Pair(w * 0.85f, h * 0.85f)
            else            -> Pair(w * 0.50f, h * 0.50f)
        }
        performTap(service, x, y)
    }

    // ── TAP EXACT COORDINATES ────────────────────────────────────
    fun tapCoordinates(x: Float, y: Float) {
        val service = serviceInstance ?: return
        performTap(service, x, y)
    }

    // ── CALL ACTIONS ─────────────────────────────────────────────
    fun handleCallAction(action: String) {
        val service = serviceInstance ?: return
        val w = service.resources.displayMetrics.widthPixels.toFloat()
        val h = service.resources.displayMetrics.heightPixels.toFloat()

        when (action) {
            "answer", "left" -> {
                performTap(service, w * 0.25f, h * 0.80f)
            }
            "decline", "right" -> {
                performTap(service, w * 0.75f, h * 0.80f)
            }
        }
    }

    // ── TEXT INTERACTION (IMPROVED) ─────────────────────────────
    fun tapOnText(text: String, requireClickable: Boolean = true): Boolean {
        val service = serviceInstance ?: return false
        val root = service.rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText(text)

        // Prefer clickable nodes, then exact match, then contains
        val target = nodes.filter { node ->
            if (requireClickable) node.isClickable else true
        }.minByOrNull { node ->
            val nodeText = node.text?.toString() ?: ""
            if (nodeText.equals(text, ignoreCase = true)) 0 else 1
        }

        return target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
    }

    fun typeText(targetLabel: String? = null, text: String): Boolean {
        val service = serviceInstance ?: return false
        val root = service.rootInActiveWindow ?: return false

        val target = if (targetLabel != null) {
            service.findNodeByText(targetLabel)
        } else {
            root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        } ?: return false

        // First tap to focus if not already focused
        if (!target.isFocused) {
            target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        }

        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    // ── PRIVATE HELPERS ──────────────────────────────────────────
    private fun performTap(service: VoiceAccessibilityService, x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()
        service.dispatchGesture(gesture, null, null)
    }

    private fun performSwipe(
        service: VoiceAccessibilityService,
        x1: Float, y1: Float,
        x2: Float, y2: Float,
        duration: Long
    ) {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        service.dispatchGesture(gesture, null, null)
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
}
