// app/src/main/java/com/lat/os/engine/PackageMapper.kt
package com.lat.os.engine

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo

object PackageMapper {

    // Common app names -> package names
    // Includes standard + Vivo/OriginOS variants
    private val knownApps = mapOf(
        // Browsers
        "chrome" to "com.android.chrome",
        "google chrome" to "com.android.chrome",
        "brave" to "com.brave.browser",
        "opera" to "com.opera.browser",
        "firefox" to "org.mozilla.firefox",
        "edge" to "com.microsoft.emmx",

        // Google apps
        "google" to "com.google.android.googlequicksearchbox",
        "youtube" to "com.google.android.youtube",
        "maps" to "com.google.android.apps.maps",
        "google maps" to "com.google.android.apps.maps",
        "gmail" to "com.google.android.gm",
        "google drive" to "com.google.android.apps.docs",
        "drive" to "com.google.android.apps.docs",
        "google meet" to "com.google.android.apps.meetings",
        "meet" to "com.google.android.apps.meetings",
        "google photos" to "com.google.android.apps.photos",
        "photos" to "com.google.android.apps.photos",
        "play store" to "com.android.vending",
        "google play" to "com.android.vending",
        "calendar" to "com.google.android.calendar",
        "google calendar" to "com.google.android.calendar",
        "google docs" to "com.google.android.apps.docs.editors.docs",
        "docs" to "com.google.android.apps.docs.editors.docs",

        // Social & messaging
        "whatsapp" to "com.whatsapp",
        "telegram" to "org.telegram.messenger",
        "instagram" to "com.instagram.android",
        "facebook" to "com.facebook.katana",
        "twitter" to "com.twitter.android",
        "x" to "com.twitter.android",
        "snapchat" to "com.snapchat.android",
        "tiktok" to "com.zhiliaoapp.musically",
        "linkedin" to "com.linkedin.android",
        "discord" to "com.discord",
        "signal" to "org.thoughtcrime.securesms",

        // Messages & calls — multiple variants for different phones
        "messages" to "com.google.android.apps.messaging",
        "message" to "com.google.android.apps.messaging",
        "sms" to "com.google.android.apps.messaging",
        "phone" to "com.google.android.dialer",
        "dialer" to "com.google.android.dialer",
        "call" to "com.google.android.dialer",
        "contacts" to "com.google.android.contacts",

        // Vivo/OriginOS specific
        "vivo messages" to "com.vivo.message",
        "vivo phone" to "com.vivo.phone",
        "vivo contacts" to "com.vivo.contacts",
        "i manager" to "com.vivo.iManager",
        "app store" to "com.vivo.appstore",
        "vivo store" to "com.vivo.appstore",
        "theme store" to "com.vivo.themestore",
        "easy share" to "com.vivo.easyshare",
        "jovi" to "com.vivo.assistant",
        "vivo assistant" to "com.vivo.assistant",

        // System apps
        "settings" to "com.android.settings",
        "camera" to "com.android.camera2",
        "gallery" to "com.android.gallery3d",
        "calculator" to "com.android.calculator2",
        "clock" to "com.android.deskclock",
        "alarm" to "com.android.deskclock",
        "file manager" to "com.android.fileexplorer",
        "files" to "com.android.fileexplorer",
        "music" to "com.android.music",
        "recorder" to "com.android.soundrecorder",
        "notes" to "com.android.notes",
        "weather" to "com.android.weather",
        "compass" to "com.android.compass",
        "flashlight" to "com.android.flashlight",

        // Streaming
        "netflix" to "com.netflix.mediaclient",
        "spotify" to "com.spotify.music",
        "amazon" to "com.amazon.mShop.android.shopping",
        "prime video" to "com.amazon.avod.thirdpartyclient",
        "hotstar" to "in.startv.hotstar",
        "disney" to "in.startv.hotstar",

        // Productivity
        "zoom" to "us.zoom.videomeetings",
        "teams" to "com.microsoft.teams",
        "word" to "com.microsoft.office.word",
        "excel" to "com.microsoft.office.excel",
        "powerpoint" to "com.microsoft.office.powerpoint",
        "outlook" to "com.microsoft.office.outlook",
        "notion" to "notion.id",
        "slack" to "com.Slack",
        "claude" to "com.anthropic.claude",
        "chatgpt" to "com.openai.chatgpt"
    )

    fun findPackage(context: Context, query: String): String? {
        val q = query.lowercase().trim()

        // 1. Direct known map lookup
        knownApps[q]?.let { pkg ->
            if (isInstalled(context, pkg)) return pkg
        }

        // 2. Partial match in known map
        for ((name, pkg) in knownApps) {
            if (q.contains(name) || name.contains(q)) {
                if (isInstalled(context, pkg)) return pkg
            }
        }

        // 3. Search all installed apps dynamically
        val installedApps = getInstalledApps(context)

        // Exact app name match
        for ((appName, pkg) in installedApps) {
            if (appName == q) return pkg
        }

        // App name contains query
        for ((appName, pkg) in installedApps) {
            if (appName.contains(q) || q.contains(appName)) return pkg
        }

        // Package name contains query
        for ((appName, pkg) in installedApps) {
            val pkgSimple = pkg.substringAfterLast(".")
            if (pkgSimple.contains(q.replace(" ", ""))) return pkg
        }

        // 4. Levenshtein fuzzy match as last resort
        var bestPkg: String? = null
        var bestDist = Int.MAX_VALUE
        for ((appName, pkg) in installedApps) {
            val dist = levenshtein(q, appName)
            if (dist < bestDist && dist <= 3) {
                bestDist = dist
                bestPkg = pkg
            }
        }
        return bestPkg
    }

    private fun isInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun getInstalledApps(context: Context): List<Pair<String, String>> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        return pm.queryIntentActivities(intent, 0).map { info ->
            val name = info.loadLabel(pm).toString().lowercase().trim()
            val pkg = info.activityInfo.packageName
            Pair(name, pkg)
        }
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                dp[i][j] = if (a[i - 1] == b[j - 1]) {
                    dp[i - 1][j - 1]
                } else {
                    1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
                }
            }
        }
        return dp[a.length][b.length]
    }
}
