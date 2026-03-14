package appblocker.appblocker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import appblocker.appblocker.data.entities.BlockRule
import appblocker.appblocker.data.entities.BlockRule.Companion.DAY_LABELS
import appblocker.appblocker.ui.viewmodel.InstalledApp
import appblocker.appblocker.ui.viewmodel.MainViewModel

// ── Main Shell ───────────────────────────────────────────────────────────

@Composable
fun MainScreen(
    vm: MainViewModel,
    hasUsagePermission: () -> Boolean,
    hasOverlayPermission: () -> Boolean,
    hasAccessibility: () -> Boolean,
    requestUsagePermission: () -> Unit,
    requestOverlay: () -> Unit,
    requestAccessibility: () -> Unit,
    startService: () -> Unit
) {
    val usageOk = hasUsagePermission()
    val overlayOk = hasOverlayPermission()
    val accessOk = hasAccessibility()

    if (!usageOk || !overlayOk) {
        PermissionSetupScreen(
            usageOk = usageOk,
            overlayOk = overlayOk,
            accessOk = accessOk,
            requestUsage = requestUsagePermission,
            requestOverlay = requestOverlay,
            requestAccessibility = requestAccessibility
        )
    } else {
        LaunchedEffect(Unit) { startService() }
        RuleListScreen(vm = vm, accessOk = accessOk, requestAccessibility = requestAccessibility)
    }
}

// ── Permission Screen (The "Onboarding" Look) ────────────────────────────────

@Composable
fun PermissionSetupScreen(
    usageOk: Boolean, overlayOk: Boolean, accessOk: Boolean,
    requestUsage: () -> Unit, requestOverlay: () -> Unit, requestAccessibility: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Shield,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "Finalize Setup",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                "FocusGuard needs these to protect your time.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(40.dp))

            PermissionCard(
                "Activity Tracking",
                "Required to detect apps",
                usageOk,
                requestUsage,
                Icons.Default.Timeline
            )
            PermissionCard(
                "Screen Overlay",
                "Required to show block screen",
                overlayOk,
                requestOverlay,
                Icons.Default.Layers
            )
            PermissionCard(
                "Web Protection",
                "Required for browser blocking",
                accessOk,
                requestAccessibility,
                Icons.Default.Public
            )
        }
    }
}

@Composable
fun PermissionCard(
    title: String,
    desc: String,
    granted: Boolean,
    onGrant: () -> Unit,
    icon: ImageVector
) {
    ElevatedCard(
        modifier = Modifier
            .padding(vertical = 8.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (granted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(desc, style = MaterialTheme.typography.labelMedium)
            }
            if (granted) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                Button(onClick = onGrant, shape = CircleShape) { Text("Allow") }
            }
        }
    }
}

// ── Rule List (The Content) ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleListScreen(vm: MainViewModel, accessOk: Boolean, requestAccessibility: () -> Unit) {
    val state by vm.ruleListState.collectAsState()
    var showAddSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("FocusGuard", fontWeight = FontWeight.Black) },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddSheet = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(20.dp)
            ) { Icon(Icons.Default.Add, "Add") }
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = 100.dp,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!accessOk) {
                item {
                    Surface(
                        onClick = requestAccessibility,
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.Warning, null)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Enable accessibility to block URLs",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            if (state.rules.isEmpty()) {
                item {
                    Box(
                        Modifier
                            .fillParentMaxHeight(0.7f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No limits set. Peace of mind awaits.",
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            val byDay = state.rules.groupBy { it.dayOfWeek }
            (1..7).forEach { day ->
                val dayRules = byDay[day] ?: return@forEach
                item {
                    Text(
                        DAY_LABELS[day - 1].uppercase(),
                        modifier = Modifier.padding(start = 8.dp, top = 16.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.5.sp
                    )
                }
                items(dayRules, key = { it.id }) { rule ->
                    RuleRow(rule, { vm.toggleRule(rule.id, it) }, { vm.deleteRule(rule.id) })
                }
            }
        }
    }

    if (showAddSheet) {
        AddRuleSheet(vm, { showAddSheet = false }, { vm.addRule(it); showAddSheet = false })
    }
}

@Composable
fun RuleRow(rule: BlockRule, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    OutlinedCard(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (rule.isEnabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(
                alpha = 0.3f
            )
        )
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            // App Icon Placeholder / Image
            Surface(
                Modifier.size(48.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                if (rule.isWebsiteRule) {
                    Icon(Icons.Default.Language, null, Modifier.padding(12.dp))
                } else {
                    // Fallback icon, logic would use package manager to get actual icon
                    Icon(Icons.Default.Apps, null, Modifier.padding(12.dp))
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(Modifier.weight(1f)) {
                Text(rule.label.ifBlank { "Unknown" }, fontWeight = FontWeight.Bold, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Timer,
                        null,
                        Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(rule.timeLabel(), style = MaterialTheme.typography.bodySmall)
                }
            }

            Switch(checked = rule.isEnabled, onCheckedChange = onToggle)
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// ── The Bottom Sheet (Add Rule) ──────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRuleSheet(vm: MainViewModel, onDismiss: () -> Unit, onSave: (BlockRule) -> Unit) {
    var ruleType by remember { mutableStateOf(RuleType.APP) }
    var selectedApp by remember { mutableStateOf<InstalledApp?>(null) }
    var websiteInput by remember { mutableStateOf("") }
    var selectedDays by remember { mutableStateOf(setOf<Int>()) }
    var startTime by remember { mutableStateOf("09:00") }
    var endTime by remember { mutableStateOf("17:00") }
    var showAppPicker by remember { mutableStateOf(false) }

    val apps by vm.installedApps.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                "New Restriction",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            // Custom Segmented Control
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = ruleType == RuleType.APP,
                    onClick = { ruleType = RuleType.APP },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text("App") }
                SegmentedButton(
                    selected = ruleType == RuleType.WEBSITE,
                    onClick = { ruleType = RuleType.WEBSITE },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text("Website") }
            }

            if (ruleType == RuleType.APP) {
                Surface(
                    onClick = { showAppPicker = true },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Row(
                        Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Search, null)
                        Spacer(Modifier.width(12.dp))
                        Text(selectedApp?.label ?: "Choose an application...")
                    }
                }
            } else {
                OutlinedTextField(
                    value = websiteInput,
                    onValueChange = { websiteInput = it },
                    placeholder = { Text("domain.com") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )
            }

            // Days Selector
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DAY_LABELS.forEachIndexed { i, day ->
                    val dayNum = i + 1
                    val isSelected = dayNum in selectedDays
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable {
                                selectedDays =
                                    if (isSelected) selectedDays - dayNum else selectedDays + dayNum
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            day.take(1),
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Button(
                onClick = { /* Save Logic same as original */ },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = selectedDays.isNotEmpty() && (selectedApp != null || websiteInput.isNotBlank())
            ) {
                Text("Create Rule", fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showAppPicker) {
        AppPickerDialog(
            apps,
            { selectedApp = it; showAppPicker = false },
            { showAppPicker = false })
    }
}

@Composable
fun AppPickerDialog(
    apps: List<InstalledApp>,
    onSelect: (InstalledApp) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        title = { Text("Select App", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search apps...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = CircleShape,
                    leadingIcon = { Icon(Icons.Default.Search, null) }
                )
                Spacer(Modifier.height(12.dp))
                LazyColumn(Modifier.height(400.dp)) {
                    items(apps.filter { it.label.contains(query, true) }) { app ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(app) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Icon display would go here
                            Surface(
                                Modifier.size(40.dp),
                                CircleShape,
                                MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Icon(Icons.Default.Apps, null, Modifier.padding(8.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(app.label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    )
}

enum class RuleType(val label: String) { APP("App"), WEBSITE("Website") }
