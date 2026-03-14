package appblocker.appblocker.ui.viewmodel

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import appblocker.appblocker.data.repository.UsageStatsRepository
import appblocker.appblocker.data.repository.UsageStatsRepository.AppUsageStat
import appblocker.appblocker.data.repository.UsageStatsRepository.UsageSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class UsageRange { TODAY, YESTERDAY, LAST_7_DAYS }

data class AppUsageUiState(
    val isLoading: Boolean = true,
    val summary: UsageSummary? = null,
    val selectedRange: UsageRange = UsageRange.TODAY,
    val customRangeLabel: String? = null,
    val error: String? = null
)

class AppUsageViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = UsageStatsRepository.get(app)

    private val _state = MutableStateFlow(AppUsageUiState())
    val state = _state.asStateFlow()

    // ── Search ───────────────────────────────────────────────────────────────
    private val _allApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    val searchResults = combine(_allApps, _searchQuery) { apps, q ->
        if (q.isBlank()) emptyList()
        else apps.filter { it.label.contains(q, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setSearchQuery(q: String) { _searchQuery.value = q }

    init {
        // Preload common ranges in background so screens are instant
        viewModelScope.launch(Dispatchers.IO) { repo.preloadCommonRanges() }
        loadUsage(UsageRange.TODAY)
        loadAllApps()
    }

    fun loadUsage(range: UsageRange) {
        _state.value = _state.value.copy(isLoading = true, selectedRange = range, customRangeLabel = null)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (s, e) = when (range) {
                    UsageRange.TODAY       -> repo.todayRange()
                    UsageRange.YESTERDAY   -> repo.yesterdayRange()
                    UsageRange.LAST_7_DAYS -> repo.lastNDaysRange(7)
                }
                _state.value = _state.value.copy(isLoading = false, summary = repo.getUsageSummary(s, e), error = null)
            } catch (ex: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = ex.message)
            }
        }
    }

    fun loadUsageCustom(rangeStart: Long, rangeEnd: Long, label: String) {
        _state.value = _state.value.copy(isLoading = true, customRangeLabel = label)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _state.value = _state.value.copy(isLoading = false, summary = repo.getUsageSummary(rangeStart, rangeEnd), error = null)
            } catch (ex: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = ex.message)
            }
        }
    }

    private fun loadAllApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = getApplication<Application>().packageManager
            _allApps.value = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
                .map { InstalledApp(it.packageName, pm.getApplicationLabel(it).toString()) }
                .sortedBy { it.label.lowercase() }
        }
    }
}
