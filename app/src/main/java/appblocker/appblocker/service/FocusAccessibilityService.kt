package appblocker.appblocker.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import appblocker.appblocker.data.entities.BlockRule.Companion.normaliseDomain
import appblocker.appblocker.data.repository.BlockRepository
import appblocker.appblocker.ui.screens.BlockedOverlayActivity
import kotlinx.coroutines.*

/**
 * Accessibility Service that reads the URL bar in popular browsers.
 *
 * How it works:
 *   1. Listens for TYPE_WINDOW_CONTENT_CHANGED events in browser packages.
 *   2. Traverses the view tree to find the address bar (by resource id or text heuristic).
 *   3. Extracts the URL, checks the repository, and shows the block overlay if needed.
 *
 * Registered in AndroidManifest + res/xml/accessibility_service_config.xml
 * User enables it via:  Settings → Accessibility → FocusGuard Website Blocker → On
 *
 * Browser package → address bar resource id mapping:
 *   Chrome         com.android.chrome              : com.android.chrome:id/url_bar
 *   Firefox        org.mozilla.firefox             : org.mozilla.firefox:id/mozac_browser_toolbar_url_view
 *   Edge           com.microsoft.emmx              : com.microsoft.emmx:id/url_bar
 *   Brave          com.brave.browser               : com.brave.browser:id/url_bar
 *   Samsung        com.sec.android.app.sbrowser    : com.sec.android.app.sbrowser:id/location_bar_edit_text
 */
class FocusAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var repo: BlockRepository

    private var lastCheckedUrl: String? = null   // debounce: only check when URL changes

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onServiceConnected() {
        repo = BlockRepository.get(applicationContext)
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                         AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
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
        val pkg = event?.packageName?.toString() ?: return
        if (pkg !in BROWSER_PACKAGES) return

        val root = rootInActiveWindow ?: return
        val url = extractUrl(root, pkg) ?: return
        root.recycle()

        if (url == lastCheckedUrl) return
        lastCheckedUrl = url

        scope.launch {
            if (repo.isWebsiteBlockedNow(url)) {
                showBlockOverlay(url)
                // Navigate back to break the browser loading cycle
                withContext(Dispatchers.Main) {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }
            }
        }
    }

    // ── URL extraction ────────────────────────────────────────────────────────

    /**
     * Tries known resource IDs first, then falls back to a text heuristic
     * (any EditText or TextView whose text looks like a URL).
     */
    private fun extractUrl(root: AccessibilityNodeInfo, pkg: String): String? {
        val resId = URL_BAR_IDS[pkg]
        if (resId != null) {
            val nodes = root.findAccessibilityNodeInfosByViewId(resId)
            if (nodes.isNotEmpty()) {
                val text = nodes[0].text?.toString()
                nodes.forEach { it.recycle() }
                if (!text.isNullOrBlank()) return text
            }
        }
        // Fallback: walk tree looking for a node whose text contains a dot and looks like a URL
        return findUrlInTree(root)
    }

    private fun findUrlInTree(node: AccessibilityNodeInfo): String? {
        val text = node.text?.toString()
        if (!text.isNullOrBlank() && looksLikeUrl(text)) return text
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findUrlInTree(child)
            child.recycle()
            if (result != null) return result
        }
        return null
    }

    private fun looksLikeUrl(text: String): Boolean {
        val t = text.trim()
        return (t.startsWith("http") || t.contains(".")) &&
               !t.contains(" ") && t.length > 4
    }

    private fun showBlockOverlay(url: String) {
        val intent = Intent(this, BlockedOverlayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(BlockedOverlayActivity.EXTRA_DOMAIN, normaliseDomain(url))
        }
        startActivity(intent)
    }

    companion object {
        val BROWSER_PACKAGES = setOf(
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.microsoft.emmx",
            "com.opera.browser",
            "com.brave.browser",
            "com.sec.android.app.sbrowser",
            "com.UCMobile.intl",
            "com.kiwibrowser.browser"
        )

        /** Known address-bar resource IDs per browser package */
        val URL_BAR_IDS = mapOf(
            "com.android.chrome"             to "com.android.chrome:id/url_bar",
            "org.mozilla.firefox"            to "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
            "com.microsoft.emmx"             to "com.microsoft.emmx:id/url_bar",
            "com.brave.browser"              to "com.brave.browser:id/url_bar",
            "com.sec.android.app.sbrowser"   to "com.sec.android.app.sbrowser:id/location_bar_edit_text",
            "com.opera.browser"              to "com.opera.browser:id/url_field",
            "com.kiwibrowser.browser"        to "com.kiwibrowser.browser:id/url_bar"
        )
    }
}
