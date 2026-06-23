// app/src/main/java/com/lat/os/engine/PackageMapper.kt
package com.lat.os.engine

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

object PackageMapper {

    private val knownApps = mapOf(
        // Google apps — CORRECT packages
        "google" to "com.google.android.googlequicksearchbox",
        "google search" to "com.google.android.googlequicksearchbox",
        "google app" to "com.google.android.googlequicksearchbox",
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
        "google docs" to "com.google.android.apps.docs.editors.docs",
        "docs" to "com.google.android.apps.docs.editors.docs",
        "google sheets" to "com.google.android.apps.docs.editors.sheets",
        "sheets" to "com.google.android.apps.docs.editors.sheets",
        "google translate" to "com.google.android.apps.translate",
        "translate" to "com.google.android.apps.translate",
        "play store" to "com.android.vending",
        "google play" to "com.android.vending",

        // Browsers
        "chrome" to "com.android.chrome",
        "google chrome" to "com.android.chrome",
        "brave" to "com.brave.browser",
        "opera" to "com.opera.browser",
        "firefox" to "org.mozilla.firefox",
        "edge" to "com.microsoft.emmx",

        // Social
        "whatsapp" to "com.whatsapp",
        "instagram" to "com.instagram.android",
        "facebook" to "com.facebook.katana",
        "messenger" to "com.facebook.orca",
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

        // Phone/Messages — Vivo + standard
        "phone" to "com.vivo.phone",
        "dialer" to "com.vivo.phone",
        "call" to "com.vivo.phone",
        "messages" to "com.vivo.message",
        "message" to "com.vivo.message",
        "sms" to "com.vivo.message",
        "contacts" to "com.vivo.contacts",

        // System
        "settings" to "com.android.settings",
        "camera" to "com.android.camera2",
        "gallery" to "com.android.gallery3d",
        "calculator" to "com.android.calculator2",
        "clock" to "com.android.deskclock",
        "alarm" to "com.android.deskclock",
        "files" to "com.android.fileexplorer",
        "file manager" to "com.android.fileexplorer",
        "recorder" to "com.android.soundrecorder",
        "notes" to "com.android.notes",
        "weather" to "com.android.weather",

        // Streaming
        "netflix" to "com.netflix.mediaclient",
        "spotify" to "com.spotify.music",
        "hotstar" to "in.startv.hotstar",
        "prime video" to "com.amazon.avod.thirdpartyclient",
        "amazon" to "com.amazon.mShop.android.shopping",

        // Productivity
        "zoom" to "us.zoom.videomeetings",
        "teams" to "com.microsoft.teams",
        "outlook" to "com.microsoft.office.outlook",
        "word" to "com.microsoft.office.word",
        "excel" to "com.microsoft.office.excel",
        "slack" to "com.Slack",
        "notion" to "notion.id",
        "claude" to "com.anthropic.claude",
        "chatgpt" to "com.openai.chatgpt",

        // Vivo specific
        "app store" to "com.vivo.appstore",
        "vivo store" to "com.vivo.appstore",
        "jovi" to "com.vivo.assistant",
        "theme store" to "com.vivo.themestore",
        "i manager" to "com.vivo.iManager"
    )

    fun findPackage(context: Context, query: String): String? {
        val q = query.lowercase().trim()
            .replace(Regex("[^a-z0-9 ]"), "")

        // 1. Exact match in known map
        knownApps[q]?.let { pkg ->
            if (isInstalled(context, pkg)) return pkg
        }

        // 2. Known map partial match
        for ((name, pkg) in knownApps) {
            if (q == name || q.contains(name) || name.contains(q)) {
                if (isInstalled(context, pkg)) return pkg
            }
        }

        // 3. Scan ALL installed apps — exact label match
        val installed = getAllInstalledApps(context)
        for ((label, pkg) in installed) {
            if (label == q) return pkg
        }

        // 4. Installed app label contains query
        for ((label, pkg) in installed) {
            if (label.contains(q) || q.contains(label)) return pkg
        }

        // 5. Package name contains query
        for ((_, pkg) in installed) {
            if (pkg.lowercase().contains(q.replace(" ", ""))) return pkg
        }

        // 6. Levenshtein fuzzy — catches typos
        var bestPkg: String? = null
        var bestDist = Int.MAX_VALUE
        for ((label, pkg) in installed) {
            val dist = levenshtein(q, label)
            if (dist < bestDist && dist <= 3) {
                bestDist = dist
                bestPkg = pkg
            }
        }
        return bestPkg
    }

    private fun isInstalled(context: Context, pkg: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(pkg, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) { false }
    }

    private fun getAllInstalledApps(context: Context): List<Pair<String, String>> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        return pm.queryIntentActivities(intent, 0).map { info ->
            Pair(
                info.loadLabel(pm).toString().lowercase().trim(),
                info.activityInfo.packageName
            )
        }
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
