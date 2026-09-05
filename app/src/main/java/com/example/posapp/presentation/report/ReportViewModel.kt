package com.example.posapp.presentation.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.posapp.data.auth.SessionManager
import com.example.posapp.data.export.FileShareHelper
import com.example.posapp.data.export.PdfInvoiceGenerator
import com.example.posapp.data.local.dao.DailySalesSummary
import com.example.posapp.data.local.dao.TopSellingItem
import com.example.posapp.data.local.entity.PaymentMethod
import com.example.posapp.data.local.entity.TransactionEntity
import com.example.posapp.data.local.entity.TransactionItemEntity
import com.example.posapp.data.local.entity.UserRole
import com.example.posapp.data.printer.PrintResult
import com.example.posapp.data.printer.PrinterRepository
import com.example.posapp.data.repository.ExpenseRepository
import com.example.posapp.data.repository.ReturnItemRequest
import com.example.posapp.data.repository.ReturnValidationException
import com.example.posapp.data.repository.ReturnWithItems
import com.example.posapp.data.repository.TransactionRepository
import com.example.posapp.data.settings.StoreProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import javax.inject.Inject

enum class ReportRangePreset { TODAY, THIS_WEEK, THIS_MONTH, CUSTOM }

data class ReportUiState(
    val preset: ReportRangePreset = ReportRangePreset.TODAY,
    val startMillis: Long = startOfToday(),
    val endMillis: Long = System.currentTimeMillis(),
    val summary: DailySalesSummary = DailySalesSummary(0.0, 0, 0.0),
    val totalExpenses: Double = 0.0, // Total Beban Usaha (diprorata) untuk periode terpilih — hanya dihitung/ditampilkan untuk Admin
    val netProfit: Double = 0.0, // Laba Bersih = Laba Kotor - Total Beban Usaha — ringkasan khusus Admin
    val topItems: List<TopSellingItem> = emptyList(),
    val transactions: List<TransactionEntity> = emptyList(), // riwayat penjualan periode terpilih
    val isAdmin: Boolean = false, // hanya Admin/Manajer yang boleh mengoreksi riwayat penjualan
    val isLoading: Boolean = false
)

/** Detail satu transaksi (header + item) yang sedang dibuka/dikoreksi lewat Riwayat Penjualan. */
data class TransactionDetailUiState(
    val transaction: TransactionEntity,
    val items: List<TransactionItemEntity>,
    val returnHistory: List<ReturnWithItems> = emptyList(), // riwayat retur/void transaksi ini
    val isSaving: Boolean = false
)

sealed class ReportEvent {
    data class ShowMessage(val message: String) : ReportEvent()
    data class PdfReady(val file: File) : ReportEvent() // invoice PDF hasil export ulang dari Riwayat Penjualan, siap dibagikan
}

private fun startOfToday(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

@HiltViewModel
class ReportViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val expenseRepository: ExpenseRepository,
    private val sessionManager: SessionManager,
    private val printerRepository: PrinterRepository,
    private val pdfInvoiceGenerator: PdfInvoiceGenerator,
    private val fileShareHelper: FileShareHelper,
    private val storeProfileRepository: StoreProfileRepository
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
            // Laba Bersih hanya berguna & sensitif untuk Admin (menyingkap struktur biaya toko),
            // jadi hitungan Beban Usaha hanya dijalankan bila user saat ini Admin — Kasir tidak
            // pernah melihat maupun memicu perhitungan ini.
            val totalExpenses = if (state.isAdmin) {
                expenseRepository.getTotalForRange(state.startMillis, state.endMillis)
            } else 0.0
            _uiState.value = _uiState.value.copy(
                summary = summary,
                topItems = topItems,
                transactions = transactions,
                totalExpenses = totalExpenses,
                netProfit = summary.totalGrossProfit - totalExpenses,
                isLoading = false
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
            val returnHistory = transactionRepository.getReturnHistory(transactionId)
            _detailState.value = TransactionDetailUiState(transaction, items, returnHistory)
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
            viewModelScope.launch { _events.emit(ReportEvent.ShowMessage("Transaksi harus punya minimal 1 item. Gunakan tombol \"Hapus Transaksi\" bila ingin menghapus seluruhnya.")) }
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

    /**
     * Membatalkan (VOID) SATU transaksi sepenuhnya — pengganti fungsi hapus permanen yang lama.
     * Beda dari hapus permanen: transaksi tetap tersimpan (statusnya jadi VOIDED) supaya ada
     * jejak audit, hanya dikeluarkan dari perhitungan Laporan. Stok dikembalikan otomatis.
     * Hanya boleh dipanggil untuk Admin — dicek di layer UI (tombol cuma tampil utk Admin,
     * sama seperti tombol "Hapus Transaksi" yang lama).
     */
    fun voidTransaction(transactionId: Long, reason: String) {
        viewModelScope.launch {
            _detailState.value = _detailState.value?.copy(isSaving = true)
            val byName = sessionManager.currentUser.value?.name ?: "Admin"
            try {
                transactionRepository.voidTransaction(transactionId, reason, byName)
                _detailState.value = null
                _events.emit(ReportEvent.ShowMessage("Transaksi berhasil dibatalkan (void)"))
                load()
            } catch (e: ReturnValidationException) {
                _detailState.value = _detailState.value?.copy(isSaving = false)
                _events.emit(ReportEvent.ShowMessage(e.message ?: "Gagal membatalkan transaksi"))
            }
        }
    }

    /**
     * Memproses retur barang (sebagian atau seluruh item) untuk transaksi yang sedang dibuka di
     * detail Riwayat Penjualan. Tersedia untuk Kasir maupun Admin (lihat Permission.canProcessReturn)
     * — alasan retur wajib diisi di layer UI sebelum fungsi ini dipanggil.
     */
    fun processReturn(
        items: List<ReturnItemRequest>,
        reason: String,
        refundAmount: Double,
        refundMethod: PaymentMethod?
    ) {
        val transactionId = _detailState.value?.transaction?.id ?: return
        viewModelScope.launch {
            _detailState.value = _detailState.value?.copy(isSaving = true)
            val processedByName = sessionManager.currentUser.value?.name ?: "Kasir"
            try {
                transactionRepository.processReturn(
                    transactionId = transactionId,
                    items = items,
                    reason = reason,
                    refundAmount = refundAmount,
                    refundMethod = refundMethod,
                    processedByName = processedByName
                )
                _detailState.value = null
                _events.emit(ReportEvent.ShowMessage("Retur berhasil diproses"))
                load()
            } catch (e: ReturnValidationException) {
                _detailState.value = _detailState.value?.copy(isSaving = false)
                _events.emit(ReportEvent.ShowMessage(e.message ?: "Gagal memproses retur"))
            }
        }
    }

    /**
     * Cetak ulang struk (thermal printer Bluetooth) untuk transaksi LAMA yang sedang dibuka di
     * detail Riwayat Penjualan — dipakai saat pelanggan minta invoice/struk susulan, tidak harus
     * langsung dicetak saat transaksi selesai. Tersedia untuk Admin maupun Kasir (bukan aksi
     * yang mengubah data, jadi tidak perlu digembok seperti koreksi/hapus transaksi).
     */
    fun printTransaction() {
        val detail = _detailState.value ?: return
        viewModelScope.launch {
            val profile = storeProfileRepository.profile.first()
            val result = withContext(Dispatchers.IO) {
                printerRepository.printReceipt(
                    storeName = profile.name,
                    transaction = detail.transaction,
                    items = detail.items,
                    storeAddress = profile.address,
                    receiptFooter = profile.receiptFooter,
                    logoImagePath = profile.logoImagePath,
                    language = profile.receiptLanguage,
                    printerName = profile.selectedPrinterName
                )
            }
            when (result) {
                is PrintResult.Success -> _events.emit(ReportEvent.ShowMessage("Struk berhasil dicetak"))
                is PrintResult.Error -> _events.emit(ReportEvent.ShowMessage(result.message))
            }
        }
    }

    /** Buat ulang invoice PDF untuk transaksi lama, siap dibagikan (WhatsApp, email, dll). */
    fun exportTransactionPdf() {
        val detail = _detailState.value ?: return
        viewModelScope.launch {
            val profile = storeProfileRepository.profile.first()
            val file = withContext(Dispatchers.IO) {
                pdfInvoiceGenerator.generate(
                    storeName = profile.name,
                    transaction = detail.transaction,
                    items = detail.items,
                    storeAddress = profile.address,
                    receiptFooter = profile.receiptFooter,
                    logoImagePath = profile.logoImagePath,
                    language = profile.receiptLanguage
                )
            }
            _events.emit(ReportEvent.PdfReady(file))
        }
    }

    fun createShareIntent(file: File) = fileShareHelper.createShareIntent(file, "application/pdf")
}
