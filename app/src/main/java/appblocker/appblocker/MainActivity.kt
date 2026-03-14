package appblocker.appblocker

import android.app.AppOpsManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import appblocker.appblocker.ui.navigation.AppNavigation
import appblocker.appblocker.ui.theme.AppBlockerTheme
import appblocker.appblocker.ui.viewmodel.AppUsageViewModel
import appblocker.appblocker.ui.viewmodel.MainViewModel
import appblocker.appblocker.ui.viewmodel.ReportsViewModel
import appblocker.appblocker.service.AppMonitorService
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppBlockerTheme(darkTheme = true, dynamicColor = false) {
                AppNavigation(
                    mainVm               = viewModel<MainViewModel>(),
                    usageVm              = viewModel<AppUsageViewModel>(),
                    reportsVm            = viewModel<ReportsViewModel>(),
                    hasUsagePermission   = ::hasUsageStatsPermission,
                    hasOverlayPermission = ::hasOverlayPermission,
                    hasAccessibility     = ::hasAccessibilityEnabled,
                    requestUsagePermission = ::openUsageSettings,
                    requestOverlay       = ::openOverlaySettings,
                    requestAccessibility = ::openAccessibilitySettings,
                    startService         = { AppMonitorService.start(this) }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasUsageStatsPermission() && hasOverlayPermission()) {
            AppMonitorService.start(this)
        }
    }

    fun hasUsageStatsPermission(): Boolean {
        val ops  = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun hasOverlayPermission(): Boolean = Settings.canDrawOverlays(this)

    fun hasAccessibilityEnabled(): Boolean {
        val service = "$packageName/appblocker.appblocker.service.FocusAccessibilityService"
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.contains(service)
    }

    fun openUsageSettings()      = startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    fun openOverlaySettings()    = startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    fun openAccessibilitySettings() = startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
}
