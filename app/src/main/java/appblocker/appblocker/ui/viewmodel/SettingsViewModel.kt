package appblocker.appblocker.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import appblocker.appblocker.data.repository.UsageStatsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val monitoringEnabled: Boolean = true,
    val warmupEnabled: Boolean = true,
    val compactCharts: Boolean = false,
    val pauseCountdownSec: Int = 30
)

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val usageRepo = UsageStatsRepository.get(app)

    private val _state = MutableStateFlow(
        SettingsUiState(
            monitoringEnabled = prefs.getBoolean(KEY_MONITORING_ENABLED, true),
            warmupEnabled = prefs.getBoolean(KEY_WARMUP_ENABLED, true),
            compactCharts = prefs.getBoolean(KEY_COMPACT_CHARTS, false),
            pauseCountdownSec = prefs.getInt(KEY_PAUSE_COUNTDOWN_SEC, 30).coerceIn(5, 120)
        )
    )
    val state = _state.asStateFlow()

    fun setMonitoringEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MONITORING_ENABLED, enabled).apply()
        _state.value = _state.value.copy(monitoringEnabled = enabled)
    }

    fun setWarmupEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WARMUP_ENABLED, enabled).apply()
        _state.value = _state.value.copy(warmupEnabled = enabled)
        if (enabled) {
            viewModelScope.launch(Dispatchers.IO) { usageRepo.preloadCommonRanges() }
        }
    }

    fun setCompactCharts(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_COMPACT_CHARTS, enabled).apply()
        _state.value = _state.value.copy(compactCharts = enabled)
    }

    fun setPauseCountdownSec(seconds: Int) {
        val clamped = seconds.coerceIn(5, 120)
        prefs.edit().putInt(KEY_PAUSE_COUNTDOWN_SEC, clamped).apply()
        _state.value = _state.value.copy(pauseCountdownSec = clamped)
    }

    companion object {
        private const val PREFS = "focusguard_settings"
        private const val KEY_MONITORING_ENABLED = "monitoring_enabled"
        private const val KEY_WARMUP_ENABLED = "warmup_enabled"
        private const val KEY_COMPACT_CHARTS = "compact_charts"
        private const val KEY_PAUSE_COUNTDOWN_SEC = "pause_countdown_sec"
    }
}

