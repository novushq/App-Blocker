package appblocker.appblocker

import android.app.AppOpsManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.compose.viewModel
import appblocker.appblocker.service.AppMonitorService
import appblocker.appblocker.ui.screens.AppUsageScreen
import appblocker.appblocker.ui.screens.MainScreen
import appblocker.appblocker.ui.viewmodel.MainViewModel

/**
 * Single activity. Checks required permissions on resume; starts the monitor service
 * once PACKAGE_USAGE_STATS is granted.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: MainViewModel = viewModel()
            AppUsageScreen(viewModel())
//            MainScreen(
//                vm = vm,
//                hasUsagePermission = ::hasUsageStatsPermission,
//                hasOverlayPermission = ::hasOverlayPermission,
//                hasAccessibility = ::hasAccessibilityEnabled,
//                requestUsagePermission = ::openUsageSettings,
//                requestOverlay = ::openOverlaySettings,
//                requestAccessibility = ::openAccessibilitySettings,
//                startService = { AppMonitorService.start(this) }
//            )
        }
    }

    override fun onResume() {
        super.onResume()
        // Auto-start service when all critical permissions are in place
        if (hasUsageStatsPermission() && hasOverlayPermission()) {
            AppMonitorService.start(this)
        }
    }

    // ── Permission helpers ────────────────────────────────────────────────────

    fun hasUsageStatsPermission(): Boolean {
        val ops = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        val mode = ops.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(), packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun hasOverlayPermission(): Boolean = Settings.canDrawOverlays(this)

    fun hasAccessibilityEnabled(): Boolean {
        val service = "$packageName/appblocker.appblocker.service.FocusAccessibilityService"
        val enabledServices = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(service)
    }

    fun openUsageSettings() =
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))

    fun openOverlaySettings() =
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
        )

    fun openAccessibilitySettings() =
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
}
