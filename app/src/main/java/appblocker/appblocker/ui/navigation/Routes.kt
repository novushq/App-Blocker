package appblocker.appblocker.ui.navigation

import android.net.Uri

object Routes {
    const val BLOCKS   = "blocks"
    const val USAGE    = "usage"
    const val REPORTS  = "reports"
    const val SETTINGS = "settings"

    // ── Full-screen (no bottom bar) ──────────────────────────────────────────
    const val APP_DETAIL  = "app_detail/{packageName}"
    const val WEEK_DETAIL = "week_detail/{weeksAgo}"

    fun appDetail(packageName: String) = "app_detail/${Uri.encode(packageName)}"
    fun weekDetail(weeksAgo: Int)      = "week_detail/$weeksAgo"

    val TOP_LEVEL = setOf(BLOCKS, USAGE, REPORTS, SETTINGS)
}

