package appblocker.appblocker.service

import android.R
import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import appblocker.appblocker.data.repository.BlockRepository
import appblocker.appblocker.ui.screens.BlockedOverlayActivity
import kotlinx.coroutines.*

class AppMonitorService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var repo: BlockRepository
    private lateinit var usageStats: UsageStatsManager
    private lateinit var wakeLock: PowerManager.WakeLock

    // Track state to avoid hammering the overlay
    private var currentForegroundPkg: String? = null
    private var overlayShowing = false

    override fun onCreate() {
        super.onCreate()
        repo = BlockRepository.get(applicationContext)
        usageStats = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager

        // No timeout — hold until we release manually
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FocusGuard::MonitorLock")
            .also { it.acquire() }

        startForeground(NOTIFICATION_ID, buildNotification())
        startMonitorLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        scope.cancel()
        if (wakeLock.isHeld) wakeLock.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Polling loop ──────────────────────────────────────────────────────────

    private fun startMonitorLoop() {
        scope.launch {
            while (isActive) {
                try {
                    checkForegroundApp()
                } catch (e: Exception) {
                    // Never let a crash kill the loop
                }
                delay(500) // 500 ms is snappy enough without killing battery
            }
        }
    }

    private suspend fun checkForegroundApp() {
        val pkg = getForegroundPackage() ?: return

        // Ignore our own overlay — without this the service would see BlockedOverlayActivity
        // as foreground, think the blocked app left, dismiss the overlay, then re-trigger it
        if (pkg == packageName) return

        if (currentForegroundPkg != pkg) {
            // App switch detected
            currentForegroundPkg = pkg

            // Dismiss overlay if the user navigated away from a blocked app
            if (overlayShowing) {
                dismissOverlay()
            }
        }

        // Check every tick whether current app is blocked right now
        // (time may have entered a block window even without an app switch)
        val isBlocked = repo.isAppBlockedNow(pkg)

        if (isBlocked && !overlayShowing) {
            overlayShowing = true
            showOverlay(pkg)
        } else if (!isBlocked && overlayShowing) {
            overlayShowing = false
            dismissOverlay()
        }
    }

    // ── Foreground detection — CORRECT approach ───────────────────────────────
    //
    // queryUsageStats(INTERVAL_DAILY) returns AGGREGATED stats where lastTimeUsed
    // can be hours stale — unreliable for 1-second polling.
    //
    // queryEvents() returns a real event stream. We look at the most recent
    // ACTIVITY_RESUMED event in the last 2 seconds to get the true foreground app.

    private fun getForegroundPackage(): String? {
        val now = System.currentTimeMillis()
        val events = usageStats.queryEvents(now - 2_000, now) ?: return null

        var latestPkg: String? = null
        var latestTime = 0L
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
                && event.timeStamp > latestTime
            ) {
                latestTime = event.timeStamp
                latestPkg = event.packageName
            }
        }
        return latestPkg
    }

    // ── Overlay control ───────────────────────────────────────────────────────

    private fun showOverlay(blockedPackage: String) {
        val intent = Intent(this, BlockedOverlayActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK        or
                Intent.FLAG_ACTIVITY_CLEAR_TOP       or
                Intent.FLAG_ACTIVITY_SINGLE_TOP      // don't stack multiple overlays
            )
            putExtra(BlockedOverlayActivity.EXTRA_PACKAGE, blockedPackage)
        }
        startActivity(intent)
    }

    private fun dismissOverlay() {
        sendBroadcast(Intent(BlockedOverlayActivity.ACTION_DISMISS).apply {
            setPackage(packageName) // explicit package — required on API 34+
        })
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun buildNotification(): Notification {
        val channelId = "fg_monitor"
        val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(channelId) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(channelId, "FocusGuard Monitor",
                    NotificationManager.IMPORTANCE_LOW)
            )
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("FocusGuard is active")
            .setContentText("Monitoring your app usage")
            .setSmallIcon(R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        fun start(context: Context) =
            context.startForegroundService(Intent(context, AppMonitorService::class.java))
        fun stop(context: Context) =
            context.stopService(Intent(context, AppMonitorService::class.java))
    }
}
