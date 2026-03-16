package appblocker.appblocker.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.net.Uri
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import appblocker.appblocker.data.entities.BlockRule.Companion.normaliseDomain
import appblocker.appblocker.data.repository.BlockRepository
import appblocker.appblocker.ui.screens.BlockedOverlayActivity
import kotlinx.coroutines.*

class FocusAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var repo: BlockRepository

    private var lastCheckedUrl: String? = null
    private var pendingCheckJob: Job? = null  // for debouncing

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onServiceConnected() {
        repo = BlockRepository.get(applicationContext)
        serviceInfo = serviceInfo.apply {
            // FIX: Only listen to STATE_CHANGED (page load committed) for blocking.
            // CONTENT_CHANGED fires while the user is *typing*, which caused premature blocks.
            // We still listen to CONTENT_CHANGED as a supplementary signal for browsers
            // that don't reliably fire STATE_CHANGED (e.g. some Samsung browser builds),
            // but we gate on confirmed navigation inside the handler.
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 200   // raised slightly to reduce event storm
            packageNames = BROWSER_PACKAGES.toTypedArray()
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
    }

    override fun onInterrupt() {
        scope.cancel()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // ── Event handling ────────────────────────────────────────────────────────

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in BROWSER_PACKAGES) return

        // KEY FIX: Only act on TYPE_WINDOW_STATE_CHANGED for the actual block check.
        //
        // TYPE_WINDOW_STATE_CHANGED fires when a new Activity or window becomes active —
        // in a browser this happens when a page finishes loading or the tab switches.
        // This is the "Enter was pressed and navigation committed" signal.
        //
        // TYPE_WINDOW_CONTENT_CHANGED fires on every keystroke in the address bar.
        // If we checked on CONTENT_CHANGED, typing "instagram" without pressing Enter
        // would already trigger the block — exactly the bug reported.
        //
        // Exception: if the event source node itself IS the URL bar (the user just
        // finished editing and focus moved away), treat that as a confirmed navigation too.
        val isConfirmedNavigation = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || isAddressBarLostFocus(event, pkg)

        if (!isConfirmedNavigation) return

        // Recycle nodes properly — the old code had a bug here.
        // The try { ... } finally { recycle } block returns Unit in Kotlin (no catch),
        // so `url` was always null. We now extract the URL separately and recycle after.
        val url = extractUrlSafely(pkg) ?: return

        if (url == lastCheckedUrl) return
        lastCheckedUrl = url

        // Cancel any pending check from a rapid previous event
        pendingCheckJob?.cancel()
        pendingCheckJob = scope.launch {
            try {
                if (repo.isWebsiteBlockedNow(url)) {
                    showBlockOverlay(url)
                    withContext(Dispatchers.Main) {
                        performGlobalAction(GLOBAL_ACTION_BACK)
                    }
                }
            } catch (e: CancellationException) {
                throw e  // always re-throw CancellationException
            } catch (e: Exception) {
                // Swallow repo errors — better to let a page through than crash
                // the accessibility service (Android would disable it on repeated ANRs)
                android.util.Log.w(TAG, "Block check failed for $url", e)
            }
        }
    }

    /**
     * Returns true when a CONTENT_CHANGED event came FROM the address bar node
     * AND that node no longer has focus — meaning the user finished typing and
     * pressed Enter (focus moved to the web view). This is the safest secondary
     * signal for browsers that don't fire STATE_CHANGED reliably.
     */
    private fun isAddressBarLostFocus(event: AccessibilityEvent, pkg: String): Boolean {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return false
        val source = event.source ?: return false
        return try {
            val resId = URL_BAR_IDS[pkg]
            if (resId != null && source.viewIdResourceName == resId) {
                !source.isFocused  // address bar existed but lost focus = Enter pressed
            } else {
                false
            }
        } finally {
            source.recycle()
        }
    }

    // ── URL extraction ────────────────────────────────────────────────────────

    /**
     * Safely extracts the current URL from the active window.
     *
     * Always recycles AccessibilityNodeInfo objects — failure to do so leaks
     * native memory and eventually causes the system to kill the service.
     */
    private fun extractUrlSafely(pkg: String): String? {
        val root = rootInActiveWindow ?: return null
        return try {
            extractUrl(root, pkg)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "URL extraction failed", e)
            null
        } finally {
            root.recycle()  // always recycle, even if extraction threw
        }
    }

    /**
     * Tries the known resource ID for the browser first (fast path),
     * then falls back to a tree walk with URL heuristics.
     */
    private fun extractUrl(root: AccessibilityNodeInfo, pkg: String): String? {
        val resId = URL_BAR_IDS[pkg]
        if (resId != null) {
            val nodes = root.findAccessibilityNodeInfosByViewId(resId)
            try {
                val text = nodes.firstNotNullOfOrNull { nodeText(it) }
                if (!text.isNullOrBlank()) return sanitiseRawBarText(text)
            } finally {
                nodes.forEach { it.recycle() }
            }
        }
        return findUrlInTree(root)
    }

    private fun findUrlInTree(node: AccessibilityNodeInfo): String? {
        val text = nodeText(node)
        if (!text.isNullOrBlank() && looksLikeUrl(text)) return sanitiseRawBarText(text)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = try {
                findUrlInTree(child)
            } finally {
                child.recycle()
            }
            if (result != null) return result
        }
        return null
    }

    private fun nodeText(node: AccessibilityNodeInfo): String? {
        return sequenceOf(node.text, node.contentDescription, node.hintText)
            .mapNotNull { it?.toString()?.trim() }
            .firstOrNull { it.isNotBlank() }
    }

    /**
     * Chrome and some browsers show the bare domain (e.g. "instagram.com") in the
     * address bar when the user is not editing — no scheme, no path. We must handle
     * this gracefully so the block check still works against stored rules.
     *
     * normaliseDomain() (in BlockRule) must strip:
     *   - scheme (https://, http://)
     *   - leading www.
     *   - trailing slash and any path
     *
     * Example transformations:
     *   "https://www.instagram.com/explore/" → "instagram.com"
     *   "instagram.com"                      → "instagram.com"
     *   "www.instagram.com"                  → "instagram.com"
     */
    private fun sanitiseRawBarText(raw: String): String? {
        val trimmed = raw.trim()

        // Heuristic: if the bar text contains spaces it's probably a search query
        // (e.g. the user typed "instagram login" and hasn't committed yet).
        // Never attempt to block search queries.
        if (trimmed.contains(' ')) return null

        // Normalise through the same function used when saving rules so comparisons always match.
        return try {
            normaliseDomain(trimmed).takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }

    private fun looksLikeUrl(text: String): Boolean {
        val t = text.trim().lowercase()
        if (t.contains(' ')) return false
        if (t.length < 4) return false
        val candidate = if ("://" in t) t else "https://$t"
        val host = runCatching { Uri.parse(candidate).host.orEmpty() }.getOrDefault("")
        return host.contains('.')
    }

    private fun showBlockOverlay(url: String) {
        val intent = Intent(this, BlockedOverlayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(BlockedOverlayActivity.EXTRA_DOMAIN, normaliseDomain(url))
        }
        startActivity(intent)
    }

    companion object {
        private const val TAG = "FocusAccessibility"

        val BROWSER_PACKAGES = setOf(
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.microsoft.emmx",
            "com.opera.browser",
            "com.brave.browser",
            "com.sec.android.app.sbrowser",
            "com.ucmobile.intl",
            "com.kiwibrowser.browser"
        )

        val URL_BAR_IDS = mapOf(
            "com.android.chrome"           to "com.android.chrome:id/url_bar",
            "org.mozilla.firefox"          to "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
            "com.microsoft.emmx"           to "com.microsoft.emmx:id/url_bar",
            "com.brave.browser"            to "com.brave.browser:id/url_bar",
            "com.sec.android.app.sbrowser" to "com.sec.android.app.sbrowser:id/location_bar_edit_text",
            "com.opera.browser"            to "com.opera.browser:id/url_field",
            "com.kiwibrowser.browser"      to "com.kiwibrowser.browser:id/url_bar"
        )
    }
}