// com/lat/os/engine/SemanticParser.kt
package com.lat.os.engine

import com.lat.os.data.Action

object SemanticParser {
    private val patterns = listOf(
        Regex("open (.+)", RegexOption.IGNORE_CASE) to { match: MatchResult -> Action("OPEN_APP", match.groupValues[1]) },
        Regex("scroll down", RegexOption.IGNORE_CASE) to { Action("SCROLL_DOWN", "") },
        Regex("scroll up", RegexOption.IGNORE_CASE) to { Action("SCROLL_UP", "") },
        Regex("go back", RegexOption.IGNORE_CASE) to { Action("GO_BACK", "") },
        Regex("go home", RegexOption.IGNORE_CASE) to { Action("GO_HOME", "") },
        Regex("click (.+)", RegexOption.IGNORE_CASE) to { match: MatchResult -> Action("TAP_TEXT", match.groupValues[1]) },
        Regex("type (.+)", RegexOption.IGNORE_CASE) to { match: MatchResult -> Action("TYPE_TEXT", match.groupValues[1]) }
    )

    fun parse(input: String): Action? {
        for ((pattern, factory) in patterns) {
            val match = pattern.find(input)
            if (match != null) {
                return factory(match)
            }
        }
        return null
    }
}
