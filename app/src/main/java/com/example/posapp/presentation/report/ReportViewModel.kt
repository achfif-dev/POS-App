package com.example.posapp.presentation.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.posapp.data.auth.SessionManager
import com.example.posapp.data.local.dao.DailySalesSummary
import com.example.posapp.data.local.dao.TopSellingItem
import com.example.posapp.data.local.entity.TransactionEntity
import com.example.posapp.data.local.entity.TransactionItemEntity
import com.example.posapp.data.local.entity.UserRole
import com.example.posapp.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    val transactions: List<TransactionEntity> = emptyList(), // riwayat penjualan periode terpilih
    val isAdmin: Boolean = false, // hanya Admin/Manajer yang boleh mengoreksi riwayat penjualan
    val isLoading: Boolean = false
)

/** Detail satu transaksi (header + item) yang sedang dibuka/dikoreksi lewat Riwayat Penjualan. */
data class TransactionDetailUiState(
    val transaction: TransactionEntity,
    val items: List<TransactionItemEntity>,
    val isSaving: Boolean = false
)

sealed class ReportEvent {
    data class ShowMessage(val message: String) : ReportEvent()
}

private fun startOfToday(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

@HiltViewModel
class ReportViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportUiState())
    val uiState: StateFlow<ReportUiState> = _uiState.asStateFlow()

    private val _detailState = MutableStateFlow<TransactionDetailUiState?>(null)
    val detailState: StateFlow<TransactionDetailUiState?> = _detailState.asStateFlow()

    private val _events = MutableSharedFlow<ReportEvent>()
    val events: SharedFlow<ReportEvent> = _events

    init {
        // Sama seperti guard peran di Pengaturan: user null (login PIN tidak wajib) dianggap Admin.
        val currentUser = sessionManager.currentUser.value
        val isAdmin = currentUser == null || currentUser.role == UserRole.ADMIN
        _uiState.value = _uiState.value.copy(isAdmin = isAdmin)
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
            val transactions = transactionRepository.observeRange(state.startMillis, state.endMillis).first()
            _uiState.value = _uiState.value.copy(
                summary = summary, topItems = topItems, transactions = transactions, isLoading = false
            )
        }
    }

    /** Buka detail satu transaksi dari daftar Riwayat Penjualan (lihat saja untuk Kasir, bisa dikoreksi untuk Admin). */
    fun openTransactionDetail(transactionId: Long) {
        viewModelScope.launch {
            val result = transactionRepository.getTransactionWithItems(transactionId)
            if (result == null) {
                _events.emit(ReportEvent.ShowMessage("Transaksi tidak ditemukan"))
                return@launch
            }
            val (transaction, items) = result
            _detailState.value = TransactionDetailUiState(transaction, items)
        }
    }

    fun closeTransactionDetail() {
        _detailState.value = null
    }

    /**
     * Simpan koreksi Admin atas riwayat penjualan (mis. kasir salah input qty/harga/produk).
     * [updatedItems] hanya berisi item yang MASIH ADA (sudah termasuk perubahan qty/harga/diskon);
     * [deletedItemIds] berisi item yang dihapus total dari transaksi. Stok disesuaikan otomatis.
     */
    fun saveTransactionCorrection(
        updatedTransaction: TransactionEntity,
        updatedItems: List<TransactionItemEntity>,
        deletedItemIds: List<Long>
    ) {
        val current = _detailState.value ?: return
        if (updatedItems.isEmpty()) {
            viewModelScope.launch { _events.emit(ReportEvent.ShowMessage("Transaksi harus punya minimal 1 item. Hapus seluruh transaksi lewat menu lain bila diperlukan.")) }
            return
        }
        viewModelScope.launch {
            _detailState.value = current.copy(isSaving = true)
            val editorName = sessionManager.currentUser.value?.name ?: "Admin"
            transactionRepository.updateTransactionWithCorrection(
                updatedTransaction = updatedTransaction,
                originalItems = current.items,
                updatedItems = updatedItems,
                deletedItemIds = deletedItemIds,
                editedByName = editorName
            )
            _detailState.value = null
            _events.emit(ReportEvent.ShowMessage("Riwayat penjualan berhasil dikoreksi"))
            load()
        }
    }
}
