// app/src/main/java/com/lat/os/engine/SemanticParser.kt
package com.lat.os.engine

import com.lat.os.data.Action

object SemanticParser {

    fun parse(input: String): Action? {
        val text = input.lowercase().trim()

        // ── APP OPENING ──────────────────────────────────────────
        Regex("^(open|launch|start|run|go to|show)\\s+(.+)$")
            .find(text)?.let {
                return Action("OPEN_APP", it.groupValues[2].trim())
            }

        // ── NAVIGATION ───────────────────────────────────────────
        if (text.matches(Regex(".*(go back|back button|navigate back).*")))
            return Action("GO_BACK", "")
        if (text.matches(Regex(".*(go home|home screen|home button).*")))
            return Action("GO_HOME", "")
        if (text.matches(Regex(".*(recent apps|recent|recents|app switcher).*")))
            return Action("RECENTS", "")

        // ── SCROLLING ────────────────────────────────────────────
        if (text.matches(Regex(".*(scroll down|swipe up|move down).*")))
            return Action("SCROLL_DOWN", "")
        if (text.matches(Regex(".*(scroll up|swipe down|move up).*")))
            return Action("SCROLL_UP", "")
        if (text.matches(Regex(".*(scroll left|swipe left|move left).*")))
            return Action("SCROLL_LEFT", "")
        if (text.matches(Regex(".*(scroll right|swipe right|move right).*")))
            return Action("SCROLL_RIGHT", "")

        // ── SCREEN TAPPING BY POSITION ───────────────────────────
        // "tap top left", "touch center", "click bottom right" etc.
        if (text.matches(Regex(".*(tap|touch|click|press).*(top left|upper left).*")))
            return Action("TAP_POSITION", "top_left")
        if (text.matches(Regex(".*(tap|touch|click|press).*(top right|upper right).*")))
            return Action("TAP_POSITION", "top_right")
        if (text.matches(Regex(".*(tap|touch|click|press).*(bottom left|lower left).*")))
            return Action("TAP_POSITION", "bottom_left")
        if (text.matches(Regex(".*(tap|touch|click|press).*(bottom right|lower right).*")))
            return Action("TAP_POSITION", "bottom_right")
        if (text.matches(Regex(".*(tap|touch|click|press).*(center|middle).*")))
            return Action("TAP_POSITION", "center")
        if (text.matches(Regex(".*(tap|touch|click|press).*(top|upper).*(center)?.*")))
            return Action("TAP_POSITION", "top_center")
        if (text.matches(Regex(".*(tap|touch|click|press).*(bottom|lower).*(center)?.*")))
            return Action("TAP_POSITION", "bottom_center")
        if (text.matches(Regex(".*(tap|touch|click|press).*(left).*")))
            return Action("TAP_POSITION", "center_left")
        if (text.matches(Regex(".*(tap|touch|click|press).*(right).*")))
            return Action("TAP_POSITION", "center_right")

        // ── CALL LEFT / CALL RIGHT (answer/decline) ──────────────
        if (text.matches(Regex(".*(call left|answer left|swipe call left).*")))
            return Action("CALL_ACTION", "left")
        if (text.matches(Regex(".*(call right|answer right|swipe call right).*")))
            return Action("CALL_ACTION", "right")
        if (text.matches(Regex(".*(answer|accept).*(call)?.*")))
            return Action("CALL_ACTION", "answer")
        if (text.matches(Regex(".*(decline|reject|hang up|end call).*")))
            return Action("CALL_ACTION", "decline")

        // ── TEXT INTERACTION ─────────────────────────────────────
        Regex("^(click|tap|press)\\s+(.+)$").find(text)?.let {
            return Action("TAP_TEXT", it.groupValues[2].trim())
        }
        Regex("^type\\s+(.+)$").find(text)?.let {
            return Action("TYPE_TEXT", it.groupValues[1].trim())
        }
        Regex("^(search|find|look up)\\s+(.+)$").find(text)?.let {
            return Action("TYPE_TEXT", it.groupValues[2].trim())
        }

        // ── VOLUME ───────────────────────────────────────────────
        if (text.matches(Regex(".*(volume up|increase volume|louder).*")))
            return Action("VOLUME_UP", "")
        if (text.matches(Regex(".*(volume down|decrease volume|quieter|mute).*")))
            return Action("VOLUME_DOWN", "")

        // ── BRIGHTNESS ───────────────────────────────────────────
        if (text.matches(Regex(".*(brightness up|increase brightness|brighter).*")))
            return Action("BRIGHTNESS_UP", "")
        if (text.matches(Regex(".*(brightness down|decrease brightness|dimmer).*")))
            return Action("BRIGHTNESS_DOWN", "")

        // ── SCREENSHOT ───────────────────────────────────────────
        if (text.matches(Regex(".*(screenshot|screen shot|capture screen).*")))
            return Action("SCREENSHOT", "")

        // ── NOTIFICATIONS ────────────────────────────────────────
        if (text.matches(Regex(".*(open notifications|notification bar|pull down).*")))
            return Action("OPEN_NOTIFICATIONS", "")

        // ── QUICK SETTINGS ───────────────────────────────────────
        if (text.matches(Regex(".*(quick settings|control center|settings panel).*")))
            return Action("OPEN_QUICK_SETTINGS", "")

        return null
    }
}
