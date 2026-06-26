package com.mdasadullahmaruf.latos

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.util.Log
import java.util.Locale

object PackageMapper {

    private val TAG = "LatOS_PackageMapper"

    private var cachedApps: List<Pair<String, String>>? = null
    private var cacheTimestamp: Long = 0
    private val CACHE_TTL = 5 * 60 * 1000

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
        "app store" to "com.android.vending",
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

        // Phone & SMS
        "phone" to "com.android.dialer",
        "dialer" to "com.android.dialer",
        "contacts" to "com.android.contacts",
        "messages" to "com.google.android.apps.messaging",
        "message" to "com.google.android.apps.messaging",
        "sms" to "com.google.android.apps.messaging",

        // System Apps
        "gallery" to "com.vivo.gallery",
        "albums" to "com.vivo.gallery",
        "browser" to "com.vivo.browser",
        "calculator" to "com.vivo.calculator",
        "camera" to "com.android.camera",
        "clock" to "com.android.deskclock",
        "alarm" to "com.android.deskclock",
        "file manager" to "com.android.filemanager",
        "files" to "com.android.filemanager",
        "music" to "com.android.bbkmusic",
        "settings" to "com.android.settings",
        "weather" to "com.vivo.weather",
        "notes" to "com.vivo.notes",
        "recorder" to "com.vivo.soundrecorder",
        "compass" to "com.vivo.compass",
        "easyshare" to "com.vivo.easyshare",
        "tips" to "com.vivo.Tips",
        "vivo store" to "com.vivo.website",
        "v-appstore" to "com.vivo.appstore",
        "simple view" to "com.vivo.simplelauncher",
        "smart remote" to "com.vivo.vhome",
        "digital wellbeing" to "com.google.android.apps.wellbeing",

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

        // Productivity
        "zoom" to "us.zoom.videomeetings",
        "teams" to "com.microsoft.teams",
        "microsoft teams" to "com.microsoft.teams",
        "outlook" to "com.microsoft.office.outlook",
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
        "paytm" to "net.one97.paytm",

        // Other
        "botim" to "im.thebot.messenger",
        "deepseek" to "com.deepseek.chat",
        "duolingo" to "com.duolingo",
        "moveon" to "com.moveon.global",
        "qcy" to "com.qcymall.googleearphonesetup",
        "quran" to "org.chromium.webapk.ad435df6874c797da_v2",
        "uae pass" to "ae.uaepass.mainapp",
        "uno" to "com.matteljv.uno",
        "viking rise" to "com.igg.android.vikingriseglobal",
        "smartlife" to "com.tuya.smartlife",
        "switch access" to "com.google.android.accessibility.switchaccess",
        "live transcribe" to "com.google.audio.hearing.visualization.accessibility.scribe",
        "mi fitness" to "com.xiaomi.wearable",
        "xiami earbuds" to "com.mi.earphone",
        "the majestic reading" to "com.themajesticreading"
    )

    fun findPackage(context: Context, query: String): String? {
        return try {
            val q = query.lowercase(Locale.getDefault()).trim()
                .replace(Regex("[^a-z0-9 ]"), "")
                .replace(Regex("\\s+"), " ")
                .trim()

            if (q.isEmpty()) {
                Log.w(TAG, "Empty query")
                return null
            }

            Log.d(TAG, "Finding package for query: '$q'")

            // 1. Exact match in known map
            knownApps[q]?.let { pkg ->
                Log.d(TAG, "Exact match in known map: '$q' -> $pkg")
                if (isInstalled(context, pkg)) return pkg
            }

            // 2. Scored match in known map (prevents "google" matching "google play")
            val knownMatch = findBestKnownMatch(q)
            if (knownMatch != null) {
                Log.d(TAG, "Scored known match: '$q' -> $knownMatch")
                if (isInstalled(context, knownMatch)) return knownMatch
            }

            // 3. Scan ALL installed apps on device (with cache)
            val allApps = getAllPackages(context)
            Log.d(TAG, "Scanned ${allApps.size} apps, searching for '$q'")

            // Exact label match
            for ((label, pkg) in allApps) {
                if (label == q) {
                    Log.d(TAG, "Exact label match: '$label' -> $pkg")
                    return pkg
                }
            }

            // Label contains query
            for ((label, pkg) in allApps) {
                if (label.contains(q)) {
                    Log.d(TAG, "Label contains query: '$label' -> $pkg")
                    return pkg
                }
            }

            // Query contains label
            for ((label, pkg) in allApps) {
                if (q.contains(label) && label.length > 2) {
                    Log.d(TAG, "Query contains label: '$label' -> $pkg")
                    return pkg
                }
            }

            // Package name contains query
            for ((_, pkg) in allApps) {
                val pkgLower = pkg.lowercase(Locale.getDefault())
                val queryNoSpace = q.replace(" ", "")
                if (pkgLower.contains(queryNoSpace)) {
                    Log.d(TAG, "Package contains query: $pkg")
                    return pkg
                }
                val lastPart = pkgLower.substringAfterLast(".")
                if (lastPart.contains(queryNoSpace)) {
                    Log.d(TAG, "Package last part matches: $pkg")
                    return pkg
                }
            }

            // 4. Fuzzy Levenshtein
            var bestPkg: String? = null
            var bestDist = Int.MAX_VALUE
            for ((label, pkg) in allApps) {
                if (label.length < 3) continue
                val dist = levenshtein(q, label)
                if (dist < bestDist && dist <= 3) {
                    bestDist = dist
                    bestPkg = pkg
                }
            }
            if (bestPkg != null) {
                Log.d(TAG, "Fuzzy match (dist=$bestDist): $bestPkg")
            } else {
                Log.w(TAG, "No match found for '$q'")
            }
            bestPkg

        } catch (e: Exception) {
            Log.e(TAG, "Error finding package for '$query'", e)
            null
        }
    }

    /**
     * Finds the best known app match using scoring to prevent substring false positives.
     * "google" should match "google" (score 1000) not "google play" (score 50).
     */
    private fun findBestKnownMatch(query: String): String? {
        var bestPkg: String? = null
        var bestScore = -1

        for ((name, pkg) in knownApps) {
            val score = when {
                name == query -> 1000
                name.startsWith(query) -> 500 + (100 / (name.length - query.length + 1))
                name.contains(query) -> {
                    val idx = name.indexOf(query)
                    val isWordBoundary = (idx == 0 || name[idx - 1] == ' ') &&
                            (idx + query.length == name.length || name[idx + query.length] == ' ')
                    if (isWordBoundary) 300 else 50
                }
                query.contains(name) -> 200
                else -> 0
            }

            if (score > bestScore) {
                bestScore = score
                bestPkg = pkg
            }
        }

        return if (bestScore >= 200) bestPkg else null
    }

    @Suppress("DEPRECATION")
    fun getAllPackages(context: Context): List<Pair<String, String>> {
        val now = System.currentTimeMillis()
        if (cachedApps != null && (now - cacheTimestamp) < CACHE_TTL) {
            Log.d(TAG, "Using cached app list (${cachedApps!!.size} apps)")
            return cachedApps!!
        }

        val pm = context.packageManager
        val results = mutableListOf<Pair<String, String>>()

        // Method 1: All installed applications (PRIMARY — this is what finds 90 apps)
        try {
            val apps = pm.getInstalledApplications(0)
            for (appInfo in apps) {
                try {
                    val pkg = appInfo.packageName
                    val launchIntent = pm.getLaunchIntentForPackage(pkg)
                    if (launchIntent != null) {
                        val label = pm.getApplicationLabel(appInfo)
                            .toString()
                            .lowercase(Locale.getDefault())
                            .trim()
                        if (results.none { it.second == pkg }) {
                            results.add(Pair(label, pkg))
                        }
                    }
                } catch (e: Exception) {
                    // ignore unreadable apps
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying installed applications", e)
        }

        // Method 2: Launcher category (fallback)
        if (results.size < 50) {
            try {
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val apps: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)
                for (info in apps) {
                    try {
                        val label = info.loadLabel(pm).toString()
                            .lowercase(Locale.getDefault())
                            .trim()
                        val pkg = info.activityInfo.packageName
                        if (results.none { it.second == pkg }) {
                            results.add(Pair(label, pkg))
                        }
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error querying launcher apps", e)
            }
        }

        // Method 3: All main activities (fallback)
        if (results.size < 50) {
            try {
                val intent = Intent(Intent.ACTION_MAIN)
                val apps: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)
                for (info in apps) {
                    try {
                        val label = info.loadLabel(pm).toString()
                            .lowercase(Locale.getDefault())
                            .trim()
                        val pkg = info.activityInfo.packageName
                        if (results.none { it.second == pkg }) {
                            results.add(Pair(label, pkg))
                        }
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error querying MAIN apps", e)
            }
        }

        cachedApps = results.distinctBy { it.second }
        cacheTimestamp = now
        Log.d(TAG, "Cached ${cachedApps!!.size} apps")
        return cachedApps!!
    }

    fun refreshCache(context: Context): List<Pair<String, String>> {
        cachedApps = null
        return getAllPackages(context)
    }

    fun isInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getLaunchIntentForPackage(packageName) != null
        } catch (e: Exception) {
            false
        }
    }

    fun getInstalledAppsList(context: Context): List<String> {
        return getAllPackages(context).map { "${it.first} -> ${it.second}" }.sorted()
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
