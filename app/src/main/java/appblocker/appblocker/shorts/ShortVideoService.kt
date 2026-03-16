package appblocker.appblocker.shorts

import android.R
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max

/**
 * ShortVideoService — detects and blocks short-form video screens.
 *
 * Detection strategy (from DigiPaws / Blokr research):
 * 1. TYPE_WINDOW_STATE_CHANGED  → new activity/fragment opened
 *    → if package is known, scan UI tree immediately
 * 2. TYPE_WINDOW_CONTENT_CHANGED → debounced (500ms) re-scan
 *    → catches tab switches within the same activity (e.g. Instagram)
 *
 * Blocking: performGlobalAction(GLOBAL_ACTION_BACK)
 * Tracking: start/end sessions in Room via ShortsDb
 */
class ShortVideoService : AccessibilityService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val sessionMutex = Mutex()
    private lateinit var db: ShortsDb

    // Current tracking state
    private var activeSessionId = -1L
    private var activePkg: String? = null
    private var inShortsMode = false

    // Debounce for content-change events
    private var lastScanAt = 0L
    private var lastBlockedAt = 0L
    private var scanJob: Job? = null

    // Schedule cache — refreshed every 30s so we don't hit DB on every event
    private var cachedSchedules = emptyList<ScheduleEntity>()
    private var schedulesCachedAt = 0L

    companion object {
        var running = false
            private set
        private const val CHANNEL = "shorts_svc"
        private const val NOTIF_TRACKING = 101
        private const val NOTIF_BLOCKED = 102
        private const val CACHE_TTL = 30_000L
        private const val DEBOUNCE = 300L
        private const val BLOCK_COOLDOWN = 1_200L
    }

    override fun onServiceConnected() {
        running = true
        db = ShortsDb.get(applicationContext)
        createChannel()

        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                         AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                         AccessibilityEvent.TYPE_VIEW_SCROLLED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }

        scope.launch { refreshSchedules() }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return

        if (inShortsMode && activePkg != null && pkg != activePkg) {
            exitShorts()
        }

        if (pkg == packageName) return

        val platform = ShortsPlatforms.forPackage(pkg) ?: return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                scheduleScan(platform, 120L)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                val now = System.currentTimeMillis()
                if (now - lastScanAt > DEBOUNCE) {
                    lastScanAt = now
                    scheduleScan(platform, DEBOUNCE)
                }
            }
        }
    }

    private fun scheduleScan(platform: ShortsPlatforms.Platform, delayMs: Long) {
        scanJob?.cancel()
        scanJob = scope.launch {
            delay(delayMs)
            scan(platform)
        }
    }

    /**
     * Scan the UI tree for short-video indicators for [platform].
     * Calls enterShorts() or exitShorts() based on result.
     */
    private suspend fun scan(platform: ShortsPlatforms.Platform) {
        refreshSchedulesIfStale()

        if (isAlwaysBlockedNow(cachedSchedules, platform.packageName)) {
            blockPlatform(platform, "Always blocked")
            return
        }

        val detected = if (platform.alwaysShortFormApp) {
            true
        } else {
            val root = rootInActiveWindow ?: return
            hasShortVideoNode(root, platform)
        }

        if (detected && (!inShortsMode || activePkg != platform.packageName)) {
            enterShorts(platform)
        } else if (!detected && inShortsMode && activePkg == platform.packageName) {
            exitShorts()
        }
    }

    /**
     * Recursive UI tree scan. Checks viewIdResourceName and contentDescription.
     * Returns true as soon as any matching node is found (early exit).
     */
    private fun hasShortVideoNode(
        node: AccessibilityNodeInfo?,
        platform: ShortsPlatforms.Platform
    ): Boolean {
        node ?: return false
        val viewId = node.viewIdResourceName ?: ""
        val desc = node.contentDescription?.toString() ?: ""

        if (platform.viewIds.any { viewId.contains(it, ignoreCase = true) }) {
            // Extra guard: node must be visible and on-screen
            // This prevents home-feed tray elements from triggering a block
            if (!isNodeOnScreen(node)) return false
            return true
        }

        if (hasStrictShortsKeywordSignal(desc, platform.contentDescKeywords)) return true

        for (i in 0 until node.childCount) {
            if (hasShortVideoNode(node.getChild(i), platform)) return true
        }
        return false
    }

    /**
     * Returns true only if the node is both visible to the user AND
     * occupies a large portion of the screen (heuristic for full-screen player).
     * Prevents home-feed embedded reels from triggering the blocker.
     */
    private fun isNodeOnScreen(node: AccessibilityNodeInfo): Boolean {
        if (!node.isVisibleToUser) return false

        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)

        val screenHeight = resources.displayMetrics.heightPixels
        val screenWidth  = resources.displayMetrics.widthPixels

        // Node must cover at least 60% of screen height AND 80% of screen width
        // A home-feed reel tray is short; a full-screen player is near-full-screen
        val heightRatio = rect.height().toFloat() / screenHeight
        val widthRatio  = rect.width().toFloat()  / screenWidth

        return heightRatio >= 0.60f && widthRatio >= 0.80f
    }

    private fun enterShorts(platform: ShortsPlatforms.Platform) {
        // End any prior session from a different platform first
        if (inShortsMode) exitShorts()

        inShortsMode = true
        activePkg = platform.packageName

        scope.launch {
            if (isBlockedNow(cachedSchedules, platform.packageName)) {
                blockPlatform(platform, "Active during your focus hours")
                inShortsMode = false
                activePkg = null
            } else {
                // Track
                sessionMutex.withLock {
                    activeSessionId = db.sessions().insert(
                        SessionEntity(
                            packageName = platform.packageName, label = platform.label,
                            startMs = System.currentTimeMillis()
                        )
                    )
                }
                notify(NOTIF_TRACKING, "Tracking ${platform.label}", "Screen time is being recorded")
            }
        }
    }

    private suspend fun blockPlatform(platform: ShortsPlatforms.Platform, body: String) {
        val now = System.currentTimeMillis()
        if (now - lastBlockedAt < BLOCK_COOLDOWN) return
        lastBlockedAt = now

        withContext(Dispatchers.Main) {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
        notify(NOTIF_BLOCKED, "⛔ ${platform.label} Blocked", body)
        db.sessions().insert(
            SessionEntity(
                packageName = platform.packageName,
                label = platform.label,
                startMs = now,
                endMs = now,
                durationSec = 0L,
                wasBlocked = true
            )
        )
    }

    private fun exitShorts() {
        if (!inShortsMode) return
        val sid = activeSessionId
        inShortsMode = false
        activePkg = null
        activeSessionId = -1L
        cancelNotif(NOTIF_TRACKING)

        scope.launch {
            sessionMutex.withLock {
                val open = db.sessions().openSession() ?: return@withLock
                if (sid > 0L && open.id != sid) return@withLock
                val now = System.currentTimeMillis()
                db.sessions().update(
                    SessionEntity(
                        id = open.id,
                        packageName = open.packageName,
                        label = open.label,
                        startMs = open.startMs,
                        endMs = now,
                        durationSec = max(0L, (now - open.startMs) / 1000L),
                        wasBlocked = open.wasBlocked
                    )
                )
            }
        }
    }

    override fun onInterrupt() {
        running = false
        exitShorts()
    }

    override fun onDestroy() {
        running = false
        scanJob?.cancel()
        cancelNotif(NOTIF_TRACKING)
        runBlocking {
            sessionMutex.withLock {
                db.sessions().openSession()?.let { open ->
                    val now = System.currentTimeMillis()
                    db.sessions().update(
                        open.copy(endMs = now, durationSec = max(0L, (now - open.startMs) / 1000L))
                    )
                }
            }
        }
        scope.cancel()
        super.onDestroy()
    }

    // ── Schedule cache ──────────────────────────────────────────────

    private suspend fun refreshSchedulesIfStale() {
        if (System.currentTimeMillis() - schedulesCachedAt > CACHE_TTL) refreshSchedules()
    }

    private suspend fun refreshSchedules() {
        cachedSchedules = db.schedules().active()
        schedulesCachedAt = System.currentTimeMillis()
    }

    // ── Notifications ───────────────────────────────────────────────

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Shorts Blocker", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "Short video tracking and blocking" }
            )
        }
    }

    private fun notify(id: Int, title: String, body: String) {
        val n = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(id == NOTIF_TRACKING)
            .setAutoCancel(id == NOTIF_BLOCKED)
            .build()
        getSystemService(NotificationManager::class.java).notify(id, n)
    }

    private fun cancelNotif(id: Int) =
        getSystemService(NotificationManager::class.java).cancel(id)
}
