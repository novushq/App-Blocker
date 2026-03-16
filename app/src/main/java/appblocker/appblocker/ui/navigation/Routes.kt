package appblocker.appblocker.ui.navigation

import android.net.Uri

object Routes {
    const val BLOCKS   = "blocks"
    const val USAGE    = "usage"
    const val REPORTS  = "reports"
    const val SETTINGS = "settings"
    const val SHORTS = "shorts"
    const val SHORTS_BLOCKS = "shorts/blocks"
    const val SHORTS_ADD = "shorts/add/{pkg}"
    const val SHORTS_SESSIONS = "shorts/sessions/{label}"
    const val SHORTS_PERMISSION = "shorts/permission"

    // ── Full-screen (no bottom bar) ──────────────────────────────────────────
    const val APP_DETAIL  = "app_detail/{packageName}"
    const val WEEK_DETAIL = "week_detail/{weeksAgo}"

    fun appDetail(packageName: String) = "app_detail/${Uri.encode(packageName)}"
    fun weekDetail(weeksAgo: Int)      = "week_detail/$weeksAgo"
    fun shortsAdd(pkg: String)         = "shorts/add/${Uri.encode(pkg)}"
    fun shortsSessions(label: String)  = "shorts/sessions/${Uri.encode(label)}"

    val TOP_LEVEL = setOf(BLOCKS, USAGE, SHORTS, REPORTS, SETTINGS)
}

