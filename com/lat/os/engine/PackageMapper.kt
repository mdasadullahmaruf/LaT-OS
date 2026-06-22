// com/lat/os/engine/PackageMapper.kt
package com.lat.os.engine

import android.content.Context
import android.content.pm.PackageManager

object PackageMapper {
    private val common = mapOf(
        "chrome" to "com.android.chrome",
        "settings" to "com.android.settings",
        "camera" to "com.android.camera2",
        "phone" to "com.android.dialer",
        "messages" to "com.google.android.apps.messaging",
        "youtube" to "com.google.android.youtube",
        "maps" to "com.google.android.apps.maps",
        "gmail" to "com.google.android.gm",
        "calculator" to "com.android.calculator2",
        "calendar" to "com.google.android.calendar"
    )

    fun findPackage(context: Context, query: String): String? {
        val q = query.lowercase().trim()
        // Direct match from common
        common[q]?.let { return it }

        // Search installed launchable apps with Levenshtein
        val pm = context.packageManager
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        }
        val activities = pm.queryIntentActivities(intent, 0)
        var bestMatch: String? = null
        var bestDistance = Int.MAX_VALUE
        for (resolveInfo in activities) {
            val appName = resolveInfo.loadLabel(pm).toString().lowercase()
            val dist = levenshtein(q, appName)
            if (dist < bestDistance && dist <= 2) {
                bestDistance = dist
                bestMatch = resolveInfo.activityInfo.packageName
            }
            // Also check package name suffix
            val pkg = resolveInfo.activityInfo.packageName
            if (pkg.contains(q.replace(" ", "")) || appName.contains(q)) {
                return pkg
            }
        }
        return bestMatch
    }

    private fun levenshtein(a: String, b: String): Int {
        val costs = IntArray(b.length + 1)
        for (i in 0..a.length) {
            var lastValue = i
            for (j in 0..b.length) {
                if (i == 0) costs[j] = j
                else {
                    if (j > 0) {
                        var newValue = costs[j - 1]
                        if (a[i - 1] != b[j - 1])
                            newValue = minOf(newValue, lastValue, costs[j]) + 1
                        costs[j - 1] = lastValue
                        lastValue = newValue
                    }
                }
            }
            if (i > 0) costs[b.length] = lastValue
        }
        return costs[b.length]
    }
}
