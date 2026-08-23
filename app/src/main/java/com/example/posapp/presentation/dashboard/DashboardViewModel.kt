package com.example.posapp.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.posapp.data.auth.SessionManager
import com.example.posapp.data.local.dao.TopSellingItem
import com.example.posapp.data.repository.ProductRepository
import com.example.posapp.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class DashboardUiState(
    val cashierName: String? = null,
    val storeName: String = "Toko Saya",
    val todayRevenue: Double = 0.0,
    val todayTransactions: Int = 0,
    val todayGrossProfit: Double = 0.0,
    val topProducts: List<TopSellingItem> = emptyList(),
    val lowStockCount: Int = 0,
    val isLoading: Boolean = true
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val productRepository: ProductRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            combine(sessionManager.currentUser, productRepository.observeLowStock()) { user, lowStock ->
                user to lowStock
            }.collect { (user, lowStock) ->
                _uiState.value = _uiState.value.copy(
                    cashierName = user?.name,
                    lowStockCount = lowStock.size
                )
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val (start, end) = todayRange()
            val summary = transactionRepository.getSalesSummary(start, end)
            val top = transactionRepository.getTopSellingItems(start, end, limit = 5)
            val lowStock = productRepository.observeLowStock().first()
            _uiState.value = _uiState.value.copy(
                todayRevenue = summary.totalRevenue,
                todayTransactions = summary.totalTransactions,
                todayGrossProfit = summary.totalGrossProfit,
                topProducts = top,
                lowStockCount = lowStock.size,
                cashierName = sessionManager.currentUser.value?.name,
                isLoading = false
            )
        }
    }

    private fun todayRange(): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_MONTH, 1)
        val end = cal.timeInMillis - 1
        return start to end
    }
}
