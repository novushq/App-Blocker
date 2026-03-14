package appblocker.appblocker.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import appblocker.appblocker.data.repository.UsageStatsRepository
import appblocker.appblocker.data.repository.UsageStatsRepository.DailyBucket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class WeekReport(
    val weeksAgo: Int,
    val label: String,
    val rangeStart: Long,
    val rangeEnd: Long,
    val totalScreenTimeMs: Long,
    val topAppName: String?,
    val topAppPackage: String?,
    val topAppMs: Long,
    val dailyBuckets: List<DailyBucket>
)

data class ReportsUiState(
    val isLoading: Boolean = true,
    val weeks: List<WeekReport> = emptyList(),
    val error: String? = null
)

class ReportsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo    = UsageStatsRepository.get(app)
    private val dateFmt = SimpleDateFormat("MMM d", Locale.getDefault())

    private val _state = MutableStateFlow(ReportsUiState())
    val state = _state.asStateFlow()

    init { loadReports() }

    fun loadReports() {
        _state.value = ReportsUiState(isLoading = true, weeks = emptyList())
        viewModelScope.launch(Dispatchers.IO) {
            val accumulated = mutableListOf<WeekReport>()

            for (weeksAgo in 0 until 8) {
                try {
                    val (start, end) = repo.isoWeekRange(weeksAgo)
                    val summary = repo.getUsageSummary(start, end)

                    // Skip weeks with no data
                    if (summary.totalScreenTimeMs <= 0L) continue

                    val topApp = summary.appStats.firstOrNull()
                    val label  = weekLabel(weeksAgo, start, end)

                    accumulated.add(
                        WeekReport(
                            weeksAgo          = weeksAgo,
                            label             = label,
                            rangeStart        = start,
                            rangeEnd          = end,
                            totalScreenTimeMs = summary.totalScreenTimeMs,
                            topAppName        = topApp?.appName,
                            topAppPackage     = topApp?.packageName,
                            topAppMs          = topApp?.totalTimeMs ?: 0L,
                            dailyBuckets      = summary.dailyBuckets
                        )
                    )

                    // Emit partial results progressively so UI shows up as data arrives
                    _state.value = ReportsUiState(isLoading = true, weeks = accumulated.toList())

                } catch (_: Exception) { /* skip problematic weeks */ }
            }

            _state.value = ReportsUiState(
                isLoading = false,
                weeks     = accumulated.toList(),
                error     = if (accumulated.isEmpty()) "No usage data found in the last 8 weeks" else null
            )
        }
    }

    private fun weekLabel(weeksAgo: Int, start: Long, end: Long): String {
        val endDisplay = end - 1
        return when (weeksAgo) {
            0    -> "This week (${dateFmt.format(Date(start))} – ${dateFmt.format(Date(endDisplay))})"
            1    -> "Last week (${dateFmt.format(Date(start))} – ${dateFmt.format(Date(endDisplay))})"
            else -> "${dateFmt.format(Date(start))} – ${dateFmt.format(Date(endDisplay))}"
        }
    }
}
