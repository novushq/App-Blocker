package appblocker.appblocker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import appblocker.appblocker.service.AppMonitorService

/**
 * Restarts AppMonitorService after device boot or app update.
 * Requires RECEIVE_BOOT_COMPLETED permission.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            AppMonitorService.start(context)
        }
    }
}
