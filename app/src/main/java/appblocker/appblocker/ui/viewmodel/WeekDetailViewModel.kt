package appblocker.appblocker.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import appblocker.appblocker.data.repository.UsageStatsRepository
import appblocker.appblocker.data.repository.UsageStatsRepository.AppUsageStat
import appblocker.appblocker.data.repository.UsageStatsRepository.DailyBucket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class WeekDetailUiState(
    val isLoading: Boolean = true,
    val weekLabel: String = "",
    val rangeStart: Long = 0L,
    val rangeEnd: Long = 0L,
    val totalScreenTimeMs: Long = 0L,
    val dailyBuckets: List<DailyBucket> = emptyList(),
    val appStats: List<AppUsageStat> = emptyList(),
    val error: String? = null
)

class WeekDetailViewModel(
    app: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(app) {

    private val repo = UsageStatsRepository.get(app)
    val weeksAgo: Int = checkNotNull(savedStateHandle["weeksAgo"])

    private val _state = MutableStateFlow(WeekDetailUiState())
    val state = _state.asStateFlow()

    private val dateFmt = SimpleDateFormat("MMM d", Locale.getDefault())

    init {
        loadWeek()
    }

    fun loadWeek() {
        _state.value = WeekDetailUiState(isLoading = true)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (start, end) = repo.isoWeekRange(weeksAgo)
                val summary = repo.getUsageSummary(start, end)
                val label = when (weeksAgo) {
                    0 -> "This week (${dateFmt.format(Date(start))} – ${dateFmt.format(Date(end - 1))})"
                    1 -> "Last week (${dateFmt.format(Date(start))} – ${dateFmt.format(Date(end - 1))})"
                    else -> "${dateFmt.format(Date(start))} – ${dateFmt.format(Date(end - 1))}"
                }
                _state.value = WeekDetailUiState(
                    isLoading         = false,
                    weekLabel         = label,
                    rangeStart        = start,
                    rangeEnd          = end,
                    totalScreenTimeMs = summary.totalScreenTimeMs,
                    dailyBuckets      = summary.dailyBuckets,
                    appStats          = summary.appStats,
                    error             = null
                )
            } catch (e: Exception) {
                _state.value = WeekDetailUiState(isLoading = false, error = e.message)
            }
        }
    }
}

