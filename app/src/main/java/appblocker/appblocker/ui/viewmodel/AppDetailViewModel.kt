package appblocker.appblocker.ui.viewmodel

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import appblocker.appblocker.data.repository.UsageStatsRepository
import appblocker.appblocker.data.repository.UsageStatsRepository.AppUsageStat
import appblocker.appblocker.data.repository.UsageStatsRepository.HourlyBucket
import appblocker.appblocker.data.repository.UsageStatsRepository.DailyBucket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AppDetailRange(val label: String) {
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    LAST_7("7 Days"),
    LAST_30("30 Days")
}

data class AppDetailUiState(
    val packageName: String = "",
    val appName: String = "",
    val isLoading: Boolean = true,
    val selectedRange: AppDetailRange = AppDetailRange.TODAY,
    val appStat: AppUsageStat? = null,
    val hourlyBuckets: List<HourlyBucket> = emptyList(),
    val dailyBuckets: List<DailyBucket> = emptyList(),
    val rangeStart: Long = 0L,
    val rangeEnd: Long = 0L,
    val error: String? = null
)

class AppDetailViewModel(
    app: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(app) {

    private val repo = UsageStatsRepository.get(app)

    val packageName: String = checkNotNull(savedStateHandle["packageName"])

    private val _state = MutableStateFlow(AppDetailUiState(packageName = packageName))
    val state = _state.asStateFlow()

    init {
        val displayName = runCatching {
            app.packageManager.getApplicationLabel(
                app.packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        }.getOrDefault(packageName)
        _state.value = _state.value.copy(appName = displayName)
        loadRange(AppDetailRange.TODAY)
    }

    fun loadRange(range: AppDetailRange) {
        _state.value = _state.value.copy(isLoading = true, selectedRange = range)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (start, end) = when (range) {
                    AppDetailRange.TODAY      -> repo.todayRange()
                    AppDetailRange.YESTERDAY  -> repo.yesterdayRange()
                    AppDetailRange.LAST_7     -> repo.lastNDaysRange(7)
                    AppDetailRange.LAST_30    -> repo.lastNDaysRange(30)
                }
                val summary = repo.getUsageSummary(start, end)
                val appStat = summary.appStats.firstOrNull { it.packageName == packageName }

                // Per-app hourly buckets (computed from this app's sessions only)
                val hourly = if (appStat != null)
                    repo.computeAppHourlyBuckets(appStat.sessions, start, end)
                else emptyList()

                // Per-app daily buckets
                val daily = if (appStat != null) summary.dailyBuckets.let { _ ->
                    // Rebuild daily buckets only for this app's sessions
                    val dailyCal = java.util.Calendar.getInstance().apply {
                        timeInMillis = start
                        set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
                        set(java.util.Calendar.SECOND, 0);       set(java.util.Calendar.MILLISECOND, 0)
                    }
                    val days = mutableListOf<Triple<Long, Long, String>>()
                    val sdf  = java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault())
                    while (dailyCal.timeInMillis < end) {
                        val ds = dailyCal.timeInMillis
                        dailyCal.add(java.util.Calendar.DAY_OF_YEAR, 1)
                        days.add(Triple(ds, minOf(dailyCal.timeInMillis, end), sdf.format(java.util.Date(ds))))
                    }
                    days.mapIndexed { idx, (ds, de, lbl) ->
                        val ms = appStat.sessions.sumOf { s ->
                            val ss = s.startTime.coerceAtLeast(ds)
                            val se = s.endTime.coerceAtMost(de)
                            if (se > ss) se - ss else 0L
                        }
                        DailyBucket(idx, lbl, ds, ms)
                    }
                } else emptyList()

                _state.value = _state.value.copy(
                    isLoading    = false,
                    appStat      = appStat,
                    hourlyBuckets = hourly,
                    dailyBuckets = daily,
                    rangeStart   = start,
                    rangeEnd     = end,
                    error        = null
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun isInstalled(): Boolean = try {
        getApplication<Application>().packageManager
            .getApplicationInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) { false }
}

