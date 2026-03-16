package appblocker.appblocker.ui.screens

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import appblocker.appblocker.ui.viewmodel.SettingsViewModel

private data class PermissionItem(
    val title: String,
    val subtitle: String,
    val granted: Boolean,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: SettingsViewModel,
    hasUsagePermission: () -> Boolean,
    hasOverlayPermission: () -> Boolean,
    hasAccessibility: () -> Boolean,
    requestUsagePermission: () -> Unit,
    requestOverlayPermission: () -> Unit,
    requestAccessibility: () -> Unit,
    startService: () -> Unit,
    stopService: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by vm.state.collectAsState()

    val permissionRows = listOf(
        PermissionItem(
            title = "Usage access",
            subtitle = "Required for app and report usage metrics",
            granted = hasUsagePermission(),
            onClick = requestUsagePermission
        ),
        PermissionItem(
            title = "Overlay permission",
            subtitle = "Required to display the blocking screen",
            granted = hasOverlayPermission(),
            onClick = requestOverlayPermission
        ),
        PermissionItem(
            title = "Accessibility web guard",
            subtitle = "Required for browser website blocking",
            granted = hasAccessibility(),
            onClick = requestAccessibility
        )
    )

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text("Settings", fontWeight = FontWeight.Black) })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = 96.dp,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Protection status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = if (permissionRows.all { it.granted }) "All required permissions are enabled."
                            else "Enable every permission for reliable blocking and reports.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            items(permissionRows, key = { it.title }) { item ->
                SettingsActionRow(
                    icon = if (item.granted) Icons.Default.Security else Icons.Default.ErrorOutline,
                    title = item.title,
                    subtitle = item.subtitle,
                    trailing = if (item.granted) "Enabled" else "Enable",
                    onClick = item.onClick
                )
            }

            item {
                SettingsToggleRow(
                    icon = Icons.Default.Power,
                    title = "Monitoring service",
                    subtitle = "Continuously monitor foreground apps for active blocks.",
                    checked = state.monitoringEnabled,
                    onToggle = { enabled ->
                        vm.setMonitoringEnabled(enabled)
                        if (enabled) startService() else stopService()
                    }
                )
            }

            item {
                SettingsToggleRow(
                    icon = Icons.Default.RocketLaunch,
                    title = "Warm usage cache",
                    subtitle = "Preload common ranges in background for faster usage screens.",
                    checked = state.warmupEnabled,
                    onToggle = vm::setWarmupEnabled
                )
            }

            item {
                SettingsToggleRow(
                    icon = Icons.Default.Tune,
                    title = "Compact charts",
                    subtitle = "Use tighter chart spacing on smaller displays.",
                    checked = state.compactCharts,
                    onToggle = vm::setCompactCharts
                )
            }

            item {
                SettingsSliderRow(
                    icon = Icons.Default.Timer,
                    title = "Pause confirmation delay",
                    subtitle = "Require a reverse countdown before pausing restrictions.",
                    value = state.pauseCountdownSec.toFloat(),
                    valueLabel = "${state.pauseCountdownSec}s",
                    range = 5f..120f,
                    steps = 22,
                    onValueChange = { vm.setPauseCountdownSec(it.toInt()) }
                )
            }

            item {
                SettingsActionRow(
                    icon = Icons.Default.Notifications,
                    title = "Notification settings",
                    subtitle = "Control persistent monitor notification behavior.",
                    trailing = "Open",
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                            )
                        }
                    }
                )
            }

            item {
                SettingsActionRow(
                    icon = Icons.Default.Settings,
                    title = "Battery optimization",
                    subtitle = "Allow unrestricted battery for consistent background blocking.",
                    trailing = "Manage",
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }
                )
            }

            item {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = false, onClick = requestUsagePermission, label = { Text("Usage") })
                    FilterChip(selected = false, onClick = requestOverlayPermission, label = { Text("Overlay") })
                    FilterChip(selected = false, onClick = requestAccessibility, label = { Text("Accessibility") })
                }
            }
        }
    }
}

@Composable
private fun SettingsSliderRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    value: Float,
    valueLabel: String,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit
) {
    Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(valueLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Slider(value = value, onValueChange = onValueChange, valueRange = range, steps = steps)
        }
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailing: String,
    onClick: () -> Unit
) {
    Card(onClick = onClick, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(trailing, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(6.dp))
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onToggle)
        }
    }
}
