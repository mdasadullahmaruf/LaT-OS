// app/src/main/java/com/lat/os/engine/SemanticParser.kt
package com.lat.os.engine

import com.lat.os.data.Action

object SemanticParser {

    fun parse(input: String): Action? {
        val t = input.lowercase().trim()
            .replace(Regex("[^a-z0-9 ]"), "")

        // ── OPEN APP ─────────────────────────────────────────────
        listOf("open ", "launch ", "start ", "run ", "go to ", "show me ")
            .forEach { prefix ->
                if (t.startsWith(prefix)) {
                    val app = t.removePrefix(prefix).trim()
                    if (app.isNotBlank()) return Action("OPEN_APP", app)
                }
            }

        // ── NAVIGATION ───────────────────────────────────────────
        if (t.contains("go back") || t == "back") return Action("GO_BACK", "")
        if (t.contains("go home") || t == "home") return Action("GO_HOME", "")
        if (t.contains("recent") || t.contains("recents")) return Action("RECENTS", "")

        // ── SCROLL / SWIPE ────────────────────────────────────────
        if (t.contains("scroll down") || t.contains("swipe up"))
            return Action("SCROLL_DOWN", "")
        if (t.contains("scroll up") || t.contains("swipe down"))
            return Action("SCROLL_UP", "")
        if (t.contains("scroll left") || t.contains("swipe left"))
            return Action("SCROLL_LEFT", "")
        if (t.contains("scroll right") || t.contains("swipe right"))
            return Action("SCROLL_RIGHT", "")

        // ── TAP POSITION ─────────────────────────────────────────
        if (t.contains("top left") || t.contains("upper left"))
            return Action("TAP_POSITION", "top_left")
        if (t.contains("top right") || t.contains("upper right"))
            return Action("TAP_POSITION", "top_right")
        if (t.contains("bottom left") || t.contains("lower left"))
            return Action("TAP_POSITION", "bottom_left")
        if (t.contains("bottom right") || t.contains("lower right"))
            return Action("TAP_POSITION", "bottom_right")
        if (t.contains("center") || t.contains("middle"))
            return Action("TAP_POSITION", "center")

        // ── CALL ─────────────────────────────────────────────────
        if (t.contains("answer") || t.contains("call left") || t.contains("accept call"))
            return Action("CALL_ACTION", "answer")
        if (t.contains("decline") || t.contains("reject") ||
            t.contains("call right") || t.contains("hang up") || t.contains("end call"))
            return Action("CALL_ACTION", "decline")

        // ── TYPE ─────────────────────────────────────────────────
        if (t.startsWith("type "))
            return Action("TYPE_TEXT", t.removePrefix("type ").trim())
        if (t.startsWith("write "))
            return Action("TYPE_TEXT", t.removePrefix("write ").trim())

        // ── VOLUME ───────────────────────────────────────────────
        if (t.contains("volume up") || t.contains("louder") ||
            t.contains("increase volume")) return Action("VOLUME_UP", "")
        if (t.contains("volume down") || t.contains("mute") ||
            t.contains("quieter") || t.contains("lower volume"))
            return Action("VOLUME_DOWN", "")

        // ── SCREENSHOT ───────────────────────────────────────────
        if (t.contains("screenshot") || t.contains("screen shot") ||
            t.contains("capture screen")) return Action("SCREENSHOT", "")

        // ── NOTIFICATIONS ────────────────────────────────────────
        if (t.contains("notification") || t.contains("pull down"))
            return Action("OPEN_NOTIFICATIONS", "")

        // ── QUICK SETTINGS ───────────────────────────────────────
        if (t.contains("quick settings") || t.contains("control center"))
            return Action("OPEN_QUICK_SETTINGS", "")

        return null
    }
}
