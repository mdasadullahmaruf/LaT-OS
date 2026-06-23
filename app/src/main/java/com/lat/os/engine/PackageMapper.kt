// app/src/main/java/com/lat/os/engine/PackageMapper.kt
package com.lat.os.engine

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo

object PackageMapper {

    private val knownApps = mapOf(
        // Google Apps
        "google" to "com.google.android.googlequicksearchbox",
        "google search" to "com.google.android.googlequicksearchbox",
        "youtube" to "com.google.android.youtube",
        "youtube music" to "com.google.android.apps.youtube.music",
        "gmail" to "com.google.android.gm",
        "google maps" to "com.google.android.apps.maps",
        "maps" to "com.google.android.apps.maps",
        "google drive" to "com.google.android.apps.docs",
        "drive" to "com.google.android.apps.docs",
        "google photos" to "com.google.android.apps.photos",
        "photos" to "com.google.android.apps.photos",
        "google meet" to "com.google.android.apps.meetings",
        "meet" to "com.google.android.apps.meetings",
        "google calendar" to "com.google.android.calendar",
        "calendar" to "com.google.android.calendar",
        "google translate" to "com.google.android.apps.translate",
        "translate" to "com.google.android.apps.translate",
        "play store" to "com.android.vending",
        "google play" to "com.android.vending",
        "gemini" to "com.google.android.apps.bard",
        "google gemini" to "com.google.android.apps.bard",
        "google assistant" to "com.google.android.googlequicksearchbox",
        "google chrome" to "com.android.chrome",
        "chrome" to "com.android.chrome",

        // Social & Messaging
        "whatsapp" to "com.whatsapp",
        "instagram" to "com.instagram.android",
        "facebook" to "com.facebook.katana",
        "messenger" to "com.facebook.orca",
        "facebook messenger" to "com.facebook.orca",
        "twitter" to "com.twitter.android",
        "x" to "com.twitter.android",
        "snapchat" to "com.snapchat.android",
        "tiktok" to "com.zhiliaoapp.musically",
        "linkedin" to "com.linkedin.android",
        "telegram" to "org.telegram.messenger",
        "discord" to "com.discord",
        "signal" to "org.thoughtcrime.securesms",
        "pinterest" to "com.pinterest",
        "reddit" to "com.reddit.frontpage",
        "threads" to "com.instagram.barcelona",

        // Phone & SMS — Vivo specific first
        "phone" to "com.vivo.phone",
        "dialer" to "com.vivo.phone",
        "call" to "com.vivo.phone",
        "messages" to "com.vivo.message",
        "message" to "com.vivo.message",
        "sms" to "com.vivo.message",
        "contacts" to "com.vivo.contacts",

        // Vivo System Apps — from your scan
        "gallery" to "com.vivo.gallery",
        "albums" to "com.vivo.gallery",
        "browser" to "com.vivo.browser",
        "calculator" to "com.vivo.calculator",
        "camera" to "com.android.camera",
        "clock" to "com.android.deskclock",
        "alarm" to "com.android.deskclock",
        "compass" to "com.vivo.compass",
        "easy share" to "com.vivo.easyshare",
        "easyshare" to "com.vivo.easyshare",
        "file manager" to "com.android.filemanager",
        "files" to "com.android.filemanager",
        "imanager" to "com.vivo.imanager",
        "music" to "com.android.bbkmusic",
        "notes" to "com.vivo.notes",
        "recorder" to "com.vivo.soundrecorder",
        "settings" to "com.android.settings",
        "themes" to "com.bbk.theme",
        "tips" to "com.vivo.Tips",
        "app store" to "com.vivo.appstore",
        "vivo store" to "com.vivo.website",
        "weather" to "com.vivo.weather",
        "smart remote" to "com.vivo.vhome",

        // Streaming
        "netflix" to "com.netflix.mediaclient",
        "spotify" to "com.spotify.music",
        "hotstar" to "in.startv.hotstar",
        "disney" to "in.startv.hotstar",
        "prime video" to "com.amazon.avod.thirdpartyclient",
        "amazon" to "com.amazon.mShop.android.shopping",
        "jio cinema" to "com.jio.jiocinema",
        "mx player" to "com.mxtech.videoplayer.ad",
        "vlc" to "org.videolan.vlc",

        // Productivity
        "zoom" to "us.zoom.videomeetings",
        "teams" to "com.microsoft.teams",
        "outlook" to "com.microsoft.office.outlook",
        "word" to "com.microsoft.office.word",
        "excel" to "com.microsoft.office.excel",
        "powerpoint" to "com.microsoft.office.powerpoint",
        "slack" to "com.Slack",
        "notion" to "notion.id",
        "claude" to "com.anthropic.claude",
        "chatgpt" to "com.openai.chatgpt",
        "brave" to "com.brave.browser",
        "opera" to "com.opera.browser",
        "firefox" to "org.mozilla.firefox"
    )

    fun findPackage(context: Context, query: String): String? {
        val q = query.lowercase().trim()
            .replace(Regex("[^a-z0-9 ]"), "")

        // 1. Direct known map match
        knownApps[q]?.let { pkg ->
            if (isInstalled(context, pkg)) return pkg
        }

        // 2. Partial known map match
        for ((name, pkg) in knownApps) {
            if (q == name || q.contains(name) || name.contains(q)) {
                if (isInstalled(context, pkg)) return pkg
            }
        }

        // 3. Scan ALL packages on device — not just launcher apps
        val allPackages = getAllPackages(context)

        // Exact label match
        for ((label, pkg) in allPackages) {
            if (label == q) return pkg
        }

        // Label contains query
        for ((label, pkg) in allPackages) {
            if (label.contains(q) || q.contains(label)) return pkg
        }

        // Package name contains query
        for ((_, pkg) in allPackages) {
            val pkgLower = pkg.lowercase()
            val queryNoSpace = q.replace(" ", "")
            if (pkgLower.contains(queryNoSpace) ||
                pkgLower.substringAfterLast(".").contains(queryNoSpace)) {
                return pkg
            }
        }

        // 4. Fuzzy Levenshtein match
        var bestPkg: String? = null
        var bestDist = Int.MAX_VALUE
        for ((label, pkg) in allPackages) {
            val dist = levenshtein(q, label)
            if (dist < bestDist && dist <= 3) {
                bestDist = dist
                bestPkg = pkg
            }
        }
        return bestPkg
    }

    // Gets ALL installed apps — not just launcher ones
    private fun getAllPackages(context: Context): List<Pair<String, String>> {
        val pm = context.packageManager
        val results = mutableListOf<Pair<String, String>>()

        // Method 1: Launcher apps
        try {
            val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            pm.queryIntentActivities(launcherIntent, 0).forEach { info ->
                results.add(Pair(
                    info.loadLabel(pm).toString().lowercase().trim(),
                    info.activityInfo.packageName
                ))
            }
        } catch (e: Exception) { }

        // Method 2: All installed packages
        try {
            pm.getInstalledPackages(0).forEach { pkgInfo ->
                try {
                    val appInfo = pkgInfo.applicationInfo
                    val label = pm.getApplicationLabel(appInfo)
                        .toString().lowercase().trim()
                    val pkg = pkgInfo.packageName
                    // Only add if has a launch intent
                    if (pm.getLaunchIntentForPackage(pkg) != null) {
                        results.add(Pair(label, pkg))
                    }
                } catch (e: Exception) { }
            }
        } catch (e: Exception) { }

        // Method 3: All main activities
        try {
            val mainIntent = Intent(Intent.ACTION_MAIN)
            pm.queryIntentActivities(mainIntent,
                PackageManager.GET_META_DATA).forEach { info ->
                try {
                    results.add(Pair(
                        info.loadLabel(pm).toString().lowercase().trim(),
                        info.activityInfo.packageName
                    ))
                } catch (e: Exception) { }
            }
        } catch (e: Exception) { }

        return results.distinctBy { it.second }
    }

    fun isInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getLaunchIntentForPackage(packageName) != null
        } catch (e: Exception) { false }
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                dp[i][j] = if (a[i-1] == b[j-1]) dp[i-1][j-1]
                else 1 + minOf(dp[i-1][j], dp[i][j-1], dp[i-1][j-1])
            }
        }
        return dp[a.length][b.length]
    }
}
