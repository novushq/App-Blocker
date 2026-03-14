package appblocker.appblocker.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import appblocker.appblocker.data.entities.BlockRule
import appblocker.appblocker.data.entities.BlockRule.Companion.DAY_LABELS
import appblocker.appblocker.ui.components.FGInlineProgress
import appblocker.appblocker.ui.components.FGMetricCard
import appblocker.appblocker.ui.components.FGSectionHeader
import appblocker.appblocker.ui.components.FGStatusPill
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
    startService: () -> Unit,
    onOpenAppUsage: (String) -> Unit,
    modifier: Modifier = Modifier
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
            requestAccessibility = requestAccessibility,
            modifier = modifier
        )
    } else {
        LaunchedEffect(Unit) { startService() }
        BlocksOverviewScreen(
            vm = vm,
            accessOk = accessOk,
            requestAccessibility = requestAccessibility,
            onOpenAppUsage = onOpenAppUsage,
            modifier = modifier
        )
    }
}

// ── Permission Screen (The "Onboarding" Look) ────────────────────────────────

@Composable
private fun PermissionSetupScreen(
    usageOk: Boolean,
    overlayOk: Boolean,
    accessOk: Boolean,
    requestUsage: () -> Unit,
    requestOverlay: () -> Unit,
    requestAccessibility: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = modifier.fillMaxSize()) {
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
                "Required to detect app usage",
                usageOk,
                requestUsage,
                Icons.Default.Timeline
            )
            PermissionCard(
                "Screen Overlay",
                "Required to show block screen",
                overlayOk,
                requestOverlay,
                Icons.Default.Lock
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
private fun PermissionCard(
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
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            } else {
                Button(onClick = onGrant, shape = CircleShape) { Text("Allow") }
            }
        }
    }
}

// ── Blocks Overview (The Content) ─────────────────────────────────────────────

private data class BlockedAppGroup(
    val packageName: String,
    val label: String,
    val rules: List<BlockRule>,
    val usageTodayMs: Long
)

private data class BlockedWebsiteGroup(
    val domain: String,
    val label: String,
    val rules: List<BlockRule>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlocksOverviewScreen(
    vm: MainViewModel,
    accessOk: Boolean,
    requestAccessibility: () -> Unit,
    onOpenAppUsage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by vm.ruleListState.collectAsState()
    val usageByPackage by vm.blockedAppUsage.collectAsState()
    var showAddSheet by remember { mutableStateOf(false) }
    var selectedAppPackage by remember { mutableStateOf<String?>(null) }
    var selectedWebsiteDomain by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        vm.refreshBlockedAppUsage()
    }

    LaunchedEffect(showAddSheet) {
        if (showAddSheet) vm.loadInstalledApps()
    }

    val appGroups = remember(state.rules, usageByPackage) {
        state.rules
            .filter { it.packageName != null }
            .groupBy { it.packageName!! }
            .map { (packageName, rules) ->
                BlockedAppGroup(
                    packageName = packageName,
                    label = rules.firstOrNull()?.label?.ifBlank { packageName } ?: packageName,
                    rules = rules.sortedBy { it.dayOfWeek * 10000 + it.startHour * 100 + it.startMinute },
                    usageTodayMs = usageByPackage[packageName]?.totalTimeMs ?: 0L
                )
            }
            .sortedByDescending { it.usageTodayMs }
    }

    val websiteGroups = remember(state.rules) {
        state.rules
            .filter { it.domain != null }
            .groupBy { it.domain!! }
            .map { (domain, rules) ->
                BlockedWebsiteGroup(
                    domain = domain,
                    label = rules.firstOrNull()?.label?.ifBlank { domain } ?: domain,
                    rules = rules.sortedBy { it.dayOfWeek * 10000 + it.startHour * 100 + it.startMinute }
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            LargeTopAppBar(
                title = { Text("Blocks", fontWeight = FontWeight.Black) },
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
            ) {
                Icon(Icons.Default.Add, "Add")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
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
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
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

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FGMetricCard(
                        title = "Blocked apps",
                        value = appGroups.size.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    FGMetricCard(
                        title = "Blocked websites",
                        value = websiteGroups.size.toString(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                FGSectionHeader(
                    title = "Blocked apps",
                    subtitle = "Tap any app to view active restrictions and usage"
                )
            }

            if (appGroups.isEmpty()) {
                item { EmptySectionCard(text = "No blocked apps yet") }
            } else {
                items(appGroups, key = { it.packageName }) { group ->
                    AppBlockCard(group = group, onClick = { selectedAppPackage = group.packageName })
                }
            }

            item {
                FGSectionHeader(
                    title = "Blocked websites",
                    subtitle = "Domain restrictions currently configured"
                )
            }

            if (websiteGroups.isEmpty()) {
                item { EmptySectionCard(text = "No blocked websites yet") }
            } else {
                items(websiteGroups, key = { it.domain }) { group ->
                    WebsiteBlockCard(group = group, onClick = { selectedWebsiteDomain = group.domain })
                }
            }
        }
    }

    if (showAddSheet) {
        AddRuleSheet(
            vm = vm,
            onDismiss = { showAddSheet = false },
            onSave = { rules ->
                rules.forEach(vm::addRule)
                showAddSheet = false
            }
        )
    }

    val selectedApp = appGroups.firstOrNull { it.packageName == selectedAppPackage }
    selectedApp?.let { app ->
        AppRuleDetailSheet(
            group = app,
            onDismiss = { selectedAppPackage = null },
            onToggle = vm::toggleRule,
            onDelete = vm::deleteRule,
            onOpenUsage = {
                onOpenAppUsage(app.packageName)
                selectedAppPackage = null
            }
        )
    }

    val selectedWebsite = websiteGroups.firstOrNull { it.domain == selectedWebsiteDomain }
    selectedWebsite?.let { website ->
        WebsiteRuleDetailSheet(
            group = website,
            onDismiss = { selectedWebsiteDomain = null },
            onToggle = vm::toggleRule,
            onDelete = vm::deleteRule
        )
    }
}

@Composable
private fun EmptySectionCard(text: String) {
    OutlinedCard(shape = RoundedCornerShape(18.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AppBlockCard(group: BlockedAppGroup, onClick: () -> Unit) {
    val enabledCount = group.rules.count { it.isEnabled }
    val totalCount = group.rules.size
    val dayLabel = group.rules.map { DAY_LABELS[it.dayOfWeek - 1] }.distinct().joinToString(", ")

    OutlinedCard(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(Icons.Default.Apps, null, Modifier.padding(10.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(group.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = formatDuration(group.usageTodayMs) + " today",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FGStatusPill(text = "$enabledCount/$totalCount active", active = enabledCount > 0)
            }
            FGInlineProgress(progress = (enabledCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f))
            Text(
                text = dayLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WebsiteBlockCard(group: BlockedWebsiteGroup, onClick: () -> Unit) {
    val enabledCount = group.rules.count { it.isEnabled }
    val totalCount = group.rules.size

    OutlinedCard(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Icon(Icons.Default.Language, null, Modifier.padding(10.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(group.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "$totalCount restriction(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FGStatusPill(text = "$enabledCount active", active = enabledCount > 0)
        }
    }
}

// ── Rule Detail Sheets (App & Website) ───────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRuleDetailSheet(
    group: BlockedAppGroup,
    onDismiss: () -> Unit,
    onToggle: (Long, Boolean) -> Unit,
    onDelete: (Long) -> Unit,
    onOpenUsage: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FGSectionHeader(title = group.label, subtitle = "${formatDuration(group.usageTodayMs)} used today")
            Button(onClick = onOpenUsage, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Timeline, null)
                Spacer(Modifier.width(8.dp))
                Text("Open usage details")
            }

            group.rules.forEachIndexed { index, rule ->
                OutlinedCard(shape = RoundedCornerShape(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "${DAY_LABELS[rule.dayOfWeek - 1]}  ${rule.timeLabel()}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Switch(checked = rule.isEnabled, onCheckedChange = { onToggle(rule.id, it) })
                        IconButton(onClick = { onDelete(rule.id) }) {
                            Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                if (index < group.rules.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebsiteRuleDetailSheet(
    group: BlockedWebsiteGroup,
    onDismiss: () -> Unit,
    onToggle: (Long, Boolean) -> Unit,
    onDelete: (Long) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FGSectionHeader(title = group.label, subtitle = "${group.rules.size} restriction(s)")
            group.rules.forEachIndexed { index, rule ->
                OutlinedCard(shape = RoundedCornerShape(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "${DAY_LABELS[rule.dayOfWeek - 1]}  ${rule.timeLabel()}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Switch(checked = rule.isEnabled, onCheckedChange = { onToggle(rule.id, it) })
                        IconButton(onClick = { onDelete(rule.id) }) {
                            Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                if (index < group.rules.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}

// ── The Bottom Sheet (Add Rule) ──────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddRuleSheet(
    vm: MainViewModel,
    onDismiss: () -> Unit,
    onSave: (List<BlockRule>) -> Unit
) {
    var ruleType by remember { mutableStateOf(RuleType.APP) }
    var selectedApp by remember { mutableStateOf<InstalledApp?>(null) }
    var websiteInput by remember { mutableStateOf("") }
    var selectedDays by remember { mutableStateOf(setOf<Int>()) }
    var startTime by remember { mutableStateOf("09:00") }
    var endTime by remember { mutableStateOf("17:00") }
    var showAppPicker by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val apps by vm.installedApps.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Add restriction", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

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
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Search, null)
                        Spacer(Modifier.width(12.dp))
                        Text(selectedApp?.label ?: "Choose an application")
                    }
                }
            } else {
                OutlinedTextField(
                    value = websiteInput,
                    onValueChange = { websiteInput = it },
                    placeholder = { Text("domain.com") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = startTime,
                    onValueChange = { startTime = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Start (HH:mm)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = endTime,
                    onValueChange = { endTime = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("End (HH:mm)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DAY_LABELS.forEachIndexed { i, day ->
                    val dayNum = i + 1
                    val isSelected = dayNum in selectedDays
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable {
                                selectedDays = if (isSelected) selectedDays - dayNum else selectedDays + dayNum
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

            if (error != null) {
                Text(
                    text = error.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = {
                    val start = parseTime(startTime)
                    val end = parseTime(endTime)

                    if (start == null || end == null) {
                        error = "Enter valid times in HH:mm format"
                        return@Button
                    }

                    val rules = selectedDays.sorted().mapNotNull { day ->
                        when (ruleType) {
                            RuleType.APP -> {
                                val app = selectedApp ?: return@mapNotNull null
                                BlockRule(
                                    packageName = app.packageName,
                                    label = app.label,
                                    dayOfWeek = day,
                                    startHour = start.first,
                                    startMinute = start.second,
                                    endHour = end.first,
                                    endMinute = end.second
                                )
                            }

                            RuleType.WEBSITE -> {
                                val domain = websiteInput.trim()
                                if (domain.isBlank()) return@mapNotNull null
                                BlockRule(
                                    domain = domain,
                                    label = domain,
                                    dayOfWeek = day,
                                    startHour = start.first,
                                    startMinute = start.second,
                                    endHour = end.first,
                                    endMinute = end.second
                                )
                            }
                        }
                    }

                    if (rules.isEmpty()) {
                        error = "Select target and at least one day"
                        return@Button
                    }
                    error = null
                    onSave(rules)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = selectedDays.isNotEmpty() && (selectedApp != null || websiteInput.isNotBlank())
            ) {
                Text("Create rule", fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showAppPicker) {
        AppPickerDialog(
            apps = apps,
            onSelect = {
                selectedApp = it
                showAppPicker = false
            },
            onDismiss = { showAppPicker = false }
        )
    }
}

@Composable
private fun AppPickerDialog(
    apps: List<InstalledApp>,
    onSelect: (InstalledApp) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        title = { Text("Select app", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search apps") },
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

private enum class RuleType { APP, WEBSITE }

private fun parseTime(value: String): Pair<Int, Int>? {
    val parts = value.trim().split(":")
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour to minute
}

private fun formatDuration(ms: Long): String {
    val h = ms / 3_600_000
    val m = (ms % 3_600_000) / 60_000
    val s = (ms % 60_000) / 1_000
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m ${s}s"
        else -> "${s}s"
    }
}
