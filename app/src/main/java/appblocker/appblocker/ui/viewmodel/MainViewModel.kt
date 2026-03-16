package appblocker.appblocker.ui.viewmodel

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import appblocker.appblocker.data.entities.BlockRule
import appblocker.appblocker.data.repository.BlockRepository
import appblocker.appblocker.data.repository.UsageStatsRepository
import appblocker.appblocker.data.repository.UsageStatsRepository.AppUsageStat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.lang.SecurityException

// ── Simple data class for installed app picker ────────────────────────────────
data class InstalledApp(
    val packageName: String,
    val label: String
)

// ── UI state for the rule list screen ────────────────────────────────────────
data class RuleListUiState(
    val rules: List<BlockRule> = emptyList(),
    val isLoading: Boolean = true
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = BlockRepository.get(app)
    private val usageRepo = UsageStatsRepository.get(app)

    // ── Rule list ─────────────────────────────────────────────────────────────

    val ruleListState: StateFlow<RuleListUiState> = repo.allRules()
        .map { RuleListUiState(rules = it, isLoading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RuleListUiState())

    // ── Installed apps (lazy, loaded once) ───────────────────────────────────

    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val installedApps: StateFlow<List<InstalledApp>> = _installedApps.asStateFlow()

    private val _blockedAppUsage = MutableStateFlow<Map<String, AppUsageStat>>(emptyMap())
    val blockedAppUsage: StateFlow<Map<String, AppUsageStat>> = _blockedAppUsage.asStateFlow()

    private val _isUsageLoading = MutableStateFlow(false)
    val isUsageLoading: StateFlow<Boolean> = _isUsageLoading.asStateFlow()

    init {
        refreshBlockedAppUsage()
    }

    fun loadInstalledApps() {
        if (_installedApps.value.isNotEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val pm = getApplication<Application>().packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }   // user-installed only
                .map { InstalledApp(it.packageName, pm.getApplicationLabel(it).toString()) }
                .sortedBy { it.label.lowercase() }
            _installedApps.value = apps
        }
    }

    fun refreshBlockedAppUsage() {
        viewModelScope.launch(Dispatchers.IO) {
            _isUsageLoading.value = true
            try {
                val packageNames = repo.allRules()
                    .first()
                    .mapNotNull { it.packageName }
                    .toSet()

                if (packageNames.isEmpty()) {
                    _blockedAppUsage.value = emptyMap()
                    return@launch
                }

                val (start, end) = usageRepo.todayRange()
                val appStats = usageRepo.getUsageSummary(start, end).appStats
                _blockedAppUsage.value = appStats
                    .filter { it.packageName in packageNames }
                    .associateBy { it.packageName }
            } catch (_: SecurityException) {
                _blockedAppUsage.value = emptyMap()
            } finally {
                _isUsageLoading.value = false
            }
        }
    }

    // ── Mutations ─────────────────────────────────────────────────────────────

    fun addRule(rule: BlockRule) = viewModelScope.launch {
        repo.addRule(rule)
        refreshBlockedAppUsage()
    }

    fun deleteRule(id: Long) = viewModelScope.launch {
        repo.deleteRule(id)
        refreshBlockedAppUsage()
    }

    fun deleteRules(ids: List<Long>) = viewModelScope.launch {
        for (id in ids.distinct()) {
            repo.deleteRule(id)
        }
        refreshBlockedAppUsage()
    }

    fun toggleRule(id: Long, enabled: Boolean) =
        viewModelScope.launch { repo.setRuleEnabled(id, enabled) }

    fun toggleRules(ids: List<Long>, enabled: Boolean) =
        viewModelScope.launch { ids.distinct().forEach { repo.setRuleEnabled(it, enabled) } }

    fun pauseRules(ids: List<Long>, minutes: Int) = viewModelScope.launch {
        val until = System.currentTimeMillis() + minutes.coerceAtLeast(1) * 60_000L
        ids.forEach { repo.setRulePausedUntil(it, until) }
    }

    fun resumeRules(ids: List<Long>) = viewModelScope.launch {
        ids.forEach { repo.setRulePausedUntil(it, 0L) }
    }
}
