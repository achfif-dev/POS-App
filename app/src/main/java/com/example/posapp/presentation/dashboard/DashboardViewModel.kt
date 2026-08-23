package com.example.posapp.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.posapp.data.auth.SessionManager
import com.example.posapp.data.local.dao.TopSellingItem
import com.example.posapp.data.local.entity.UserRole
import com.example.posapp.data.repository.ProductRepository
import com.example.posapp.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

data class DashboardUiState(
    val cashierName: String? = null,
    val storeName: String = "Toko Saya",
    val todayRevenue: Double = 0.0,
    val todayTransactions: Int = 0,
    val todayGrossProfit: Double = 0.0,
    val topProducts: List<TopSellingItem> = emptyList(),
    val lowStockCount: Int = 0,
    val revenueTrend: List<DayRevenue> = emptyList(),
    val isAdmin: Boolean = true,
    val isLoading: Boolean = true
)

/** Total omzet untuk satu hari, dipakai grafik tren 7 hari terakhir di Dashboard. */
data class DayRevenue(val label: String, val date: java.util.Date, val revenue: Double)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val productRepository: ProductRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        // Muat ringkasan awal, lalu berlangganan perubahan transaksi/stok/sesi secara reaktif
        // (Room Flow) agar Dashboard langsung update setelah checkout tanpa perlu menutup-buka
        // ulang aplikasi — sebelumnya ringkasan hanya dihitung sekali saat ViewModel dibuat.
        viewModelScope.launch {
            combine(
                sessionManager.currentUser,
                productRepository.observeLowStock(),
                transactionRepository.observeAll()
            ) { user, lowStock, _ -> user to lowStock }
                .collect { (user, lowStock) ->
                    _uiState.value = _uiState.value.copy(
                        cashierName = user?.name,
                        isAdmin = user == null || user.role == UserRole.ADMIN,
                        lowStockCount = lowStock.size
                    )
                    loadSummary()
                }
        }
    }

    fun refresh() {
        viewModelScope.launch { loadSummary() }
    }

    private suspend fun loadSummary() {
        val (start, end) = todayRange()
        val summary = transactionRepository.getSalesSummary(start, end)
        val top = transactionRepository.getTopSellingItems(start, end, limit = 5)
        val trend = loadRevenueTrend()
        _uiState.value = _uiState.value.copy(
            todayRevenue = summary.totalRevenue,
            todayTransactions = summary.totalTransactions,
            todayGrossProfit = summary.totalGrossProfit,
            topProducts = top,
            revenueTrend = trend,
            isLoading = false
        )
    }

    /** Omzet 7 hari terakhir (termasuk hari ini), untuk grafik tren mini di Dashboard. */
    private suspend fun loadRevenueTrend(): List<DayRevenue> {
        val labelFormat = java.text.SimpleDateFormat("EEE", Locale("in", "ID"))
        val results = mutableListOf<DayRevenue>()
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_MONTH, -6)
        repeat(7) {
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            val dayStart = cal.timeInMillis
            val dayEnd = dayStart + 24L * 60 * 60 * 1000 - 1
            val summary = transactionRepository.getSalesSummary(dayStart, dayEnd)
            results.add(DayRevenue(label = labelFormat.format(java.util.Date(dayStart)), date = java.util.Date(dayStart), revenue = summary.totalRevenue))
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return results
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
