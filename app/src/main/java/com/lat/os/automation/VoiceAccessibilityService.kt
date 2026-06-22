// com/lat/os/automation/VoiceAccessibilityService.kt
package com.lat.os.automation

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class VoiceAccessibilityService : AccessibilityService() {
    companion object {
        var instance: VoiceAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        DeviceAutomator.setService(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        DeviceAutomator.setService(null)
        super.onDestroy()
    }
}
