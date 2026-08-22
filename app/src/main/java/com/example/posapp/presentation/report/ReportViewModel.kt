package com.example.posapp.presentation.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.posapp.data.local.dao.DailySalesSummary
import com.example.posapp.data.local.dao.TopSellingItem
import com.example.posapp.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

enum class ReportRangePreset { TODAY, THIS_WEEK, THIS_MONTH, CUSTOM }

data class ReportUiState(
    val preset: ReportRangePreset = ReportRangePreset.TODAY,
    val startMillis: Long = startOfToday(),
    val endMillis: Long = System.currentTimeMillis(),
    val summary: DailySalesSummary = DailySalesSummary(0.0, 0, 0.0),
    val topItems: List<TopSellingItem> = emptyList(),
    val isLoading: Boolean = false
)

private fun startOfToday(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

@HiltViewModel
class ReportViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportUiState())
    val uiState: StateFlow<ReportUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun selectPreset(preset: ReportRangePreset) {
        val calendar = Calendar.getInstance()
        val start: Long
        when (preset) {
            ReportRangePreset.TODAY -> {
                start = startOfToday()
            }
            ReportRangePreset.THIS_WEEK -> {
                calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0); calendar.set(Calendar.SECOND, 0)
                start = calendar.timeInMillis
            }
            ReportRangePreset.THIS_MONTH -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0); calendar.set(Calendar.SECOND, 0)
                start = calendar.timeInMillis
            }
            ReportRangePreset.CUSTOM -> start = _uiState.value.startMillis
        }
        _uiState.value = _uiState.value.copy(preset = preset, startMillis = start, endMillis = System.currentTimeMillis())
        load()
    }

    fun setCustomRange(start: Long, end: Long) {
        _uiState.value = _uiState.value.copy(preset = ReportRangePreset.CUSTOM, startMillis = start, endMillis = end)
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val state = _uiState.value
            val summary = transactionRepository.getSalesSummary(state.startMillis, state.endMillis)
            val topItems = transactionRepository.getTopSellingItems(state.startMillis, state.endMillis)
            _uiState.value = _uiState.value.copy(summary = summary, topItems = topItems, isLoading = false)
        }
    }
}
