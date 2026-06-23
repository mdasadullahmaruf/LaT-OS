// app/src/main/java/com/lat/os/engine/PackageMapper.kt
package com.lat.os.engine

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

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
        "chrome" to "com.android.chrome",
        "google chrome" to "com.android.chrome",
        "google assistant" to "com.google.android.googlequicksearchbox",

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

        // Phone & SMS — Vivo specific
        "phone" to "com.vivo.phone",
        "dialer" to "com.vivo.phone",
        "contacts" to "com.vivo.contacts",
        "messages" to "com.vivo.message",
        "message" to "com.vivo.message",
        "sms" to "com.vivo.message",

        // Vivo System Apps from your scan
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
        "digital wellbeing" to "com.google.android.apps.wellbeing",
        "wellbeing" to "com.google.android.apps.wellbeing",

        // Streaming
        "netflix" to "com.netflix.mediaclient",
        "spotify" to "com.spotify.music",
        "hotstar" to "in.startv.hotstar",
        "disney" to "in.startv.hotstar",
        "disney hotstar" to "in.startv.hotstar",
        "prime video" to "com.amazon.avod.thirdpartyclient",
        "amazon prime" to "com.amazon.avod.thirdpartyclient",
        "amazon" to "com.amazon.mShop.android.shopping",
        "mx player" to "com.mxtech.videoplayer.ad",
        "vlc" to "org.videolan.vlc",
        "jio cinema" to "com.jio.jiocinema",

        // Productivity & Work
        "zoom" to "us.zoom.videomeetings",
        "teams" to "com.microsoft.teams",
        "microsoft teams" to "com.microsoft.teams",
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
        "firefox" to "org.mozilla.firefox",
        "edge" to "com.microsoft.emmx",

        // Shopping
        "flipkart" to "com.flipkart.android",
        "meesho" to "com.meesho.supply",
        "myntra" to "com.myntra.android",

        // Payments
        "gpay" to "com.google.android.apps.nbu.paisa.user",
        "google pay" to "com.google.android.apps.nbu.paisa.user",
        "phonepe" to "com.phonepe.app",
        "paytm" to "net.one97.paytm"
    )

    fun findPackage(context: Context, query: String): String? {
        val q = query.lowercase().trim()
            .replace(Regex("[^a-z0-9 ]"), "")

        // 1. Exact match in known map
        knownApps[q]?.let { pkg ->
            if (isInstalled(context, pkg)) return pkg
        }

        // 2. Partial match in known map
        for ((name, pkg) in knownApps) {
            if (q == name || q.contains(name) || name.contains(q)) {
                if (isInstalled(context, pkg)) return pkg
            }
        }

        // 3. Scan ALL installed apps on device
        val allApps = getAllPackages(context)

        // Exact label match
        for ((label, pkg) in allApps) {
            if (label == q) return pkg
        }

        // Label contains query or query contains label
        for ((label, pkg) in allApps) {
            if (label.contains(q) || q.contains(label)) return pkg
        }

        // Package name contains query
        for ((_, pkg) in allApps) {
            val pkgLower = pkg.lowercase()
            val queryNoSpace = q.replace(" ", "")
            if (pkgLower.contains(queryNoSpace) ||
                pkgLower.substringAfterLast(".").contains(queryNoSpace)
            ) return pkg
        }

        // 4. Fuzzy Levenshtein — catches typos
        var bestPkg: String? = null
        var bestDist = Int.MAX_VALUE
        for ((label, pkg) in allApps) {
            val dist = levenshtein(q, label)
            if (dist < bestDist && dist <= 3) {
                bestDist = dist
                bestPkg = pkg
            }
        }
        return bestPkg
    }

    private fun getAllPackages(context: Context): List<Pair<String, String>> {
        val pm = context.packageManager
        val results = mutableListOf<Pair<String, String>>()

        // Method 1: All installed applications
        try {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .forEach { appInfo ->
                    try {
                        val pkg = appInfo.packageName
                        val launchIntent = pm.getLaunchIntentForPackage(pkg)
                        if (launchIntent != null) {
                            val label = pm.getApplicationLabel(appInfo)
                                .toString().lowercase().trim()
                            if (results.none { it.second == pkg }) {
                                results.add(Pair(label, pkg))
                            }
                        }
                    } catch (e: Exception) { }
                }
        } catch (e: Exception) { }

        // Method 2: Launcher category
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            pm.queryIntentActivities(intent, 0).forEach { info ->
                try {
                    val label = info.loadLabel(pm).toString()
                        .lowercase().trim()
                    val pkg = info.activityInfo.packageName
                    if (results.none { it.second == pkg }) {
                        results.add(Pair(label, pkg))
                    }
                } catch (e: Exception) { }
            }
        } catch (e: Exception) { }

        // Method 3: All main activities
        try {
            val intent = Intent(Intent.ACTION_MAIN)
            pm.queryIntentActivities(
                intent, PackageManager.GET_META_DATA
            ).forEach { info ->
                try {
                    val label = info.loadLabel(pm).toString()
                        .lowercase().trim()
                    val pkg = info.activityInfo.packageName
                    if (results.none { it.second == pkg }) {
                        results.add(Pair(label, pkg))
                    }
                } catch (e: Exception) { }
            }
        } catch (e: Exception) { }

        return results.distinctBy { it.second }
    }

    fun isInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getLaunchIntentForPackage(packageName) != null
        } catch (e: Exception) {
            false
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
