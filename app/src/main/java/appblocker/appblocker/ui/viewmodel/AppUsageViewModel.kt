package appblocker.appblocker.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import appblocker.appblocker.data.repository.UsageStatsRepository
import appblocker.appblocker.data.repository.UsageStatsRepository.AppUsageStat
import appblocker.appblocker.data.repository.UsageStatsRepository.HourlyBucket
import appblocker.appblocker.data.repository.UsageStatsRepository.UsageSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class UsageRange { TODAY, YESTERDAY, LAST_7_DAYS }

data class AppUsageUiState(
    val isLoading: Boolean = true,
    val summary: UsageSummary? = null,
    val selectedRange: UsageRange = UsageRange.TODAY,
    // drill-down: if non-null, show per-session detail for this app
    val drillDownApp: AppUsageStat? = null,
    val error: String? = null
)

class AppUsageViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = UsageStatsRepository.get(app)

    private val _state = MutableStateFlow(AppUsageUiState())
    val state = _state.asStateFlow()

    init { loadUsage(UsageRange.TODAY) }

    fun loadUsage(range: UsageRange) {
        _state.value = _state.value.copy(isLoading = true, selectedRange = range, drillDownApp = null)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (start, end) = when (range) {
                    UsageRange.TODAY       -> repo.todayRange()
                    UsageRange.YESTERDAY   -> repo.yesterdayRange()
                    UsageRange.LAST_7_DAYS -> repo.lastNDaysRange(7)
                }
                val summary = repo.getUsageSummary(start, end)
                _state.value = _state.value.copy(isLoading = false, summary = summary, error = null)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun drillInto(app: AppUsageStat) {
        _state.value = _state.value.copy(drillDownApp = app)
    }

    fun clearDrillDown() {
        _state.value = _state.value.copy(drillDownApp = null)
    }
}
