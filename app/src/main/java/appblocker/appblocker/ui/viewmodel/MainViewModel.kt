package appblocker.appblocker.ui.viewmodel

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import appblocker.appblocker.data.entities.BlockRule
import appblocker.appblocker.data.repository.BlockRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

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

    // ── Rule list ─────────────────────────────────────────────────────────────

    val ruleListState: StateFlow<RuleListUiState> = repo.allRules()
        .map { RuleListUiState(rules = it, isLoading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RuleListUiState())

    // ── Installed apps (lazy, loaded once) ───────────────────────────────────

    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val installedApps: StateFlow<List<InstalledApp>> = _installedApps.asStateFlow()

    fun loadInstalledApps() {
        if (_installedApps.value.isNotEmpty()) return
        viewModelScope.launch {
            val pm = getApplication<Application>().packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }   // user-installed only
                .map { InstalledApp(it.packageName, pm.getApplicationLabel(it).toString()) }
                .sortedBy { it.label.lowercase() }
            _installedApps.value = apps
        }
    }

    // ── Mutations ─────────────────────────────────────────────────────────────

    fun addRule(rule: BlockRule) = viewModelScope.launch { repo.addRule(rule) }

    fun deleteRule(id: Long) = viewModelScope.launch { repo.deleteRule(id) }

    fun toggleRule(id: Long, enabled: Boolean) =
        viewModelScope.launch { repo.setRuleEnabled(id, enabled) }
}
