package com.example.posapp.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.posapp.data.printer.PrinterRepository
import com.example.posapp.data.qris.QrisImageDecoder
import com.example.posapp.data.settings.StoreProfile
import com.example.posapp.data.settings.StoreProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import com.example.posapp.data.settings.quickCashAmountList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class StoreProfileEvent {
    data class ShowMessage(val message: String) : StoreProfileEvent()
}

@HiltViewModel
class StoreProfileViewModel @Inject constructor(
    private val storeProfileRepository: StoreProfileRepository,
    private val qrisImageDecoder: QrisImageDecoder,
    private val printerRepository: PrinterRepository
) : ViewModel() {

    val uiState: StateFlow<StoreProfile> = storeProfileRepository.profile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StoreProfile())

    private val _events = MutableSharedFlow<StoreProfileEvent>()
    val events: SharedFlow<StoreProfileEvent> = _events

    fun save(name: String, address: String, phone: String, footer: String) {
        if (name.isBlank()) {
            viewModelScope.launch { _events.emit(StoreProfileEvent.ShowMessage("Nama toko tidak boleh kosong")) }
            return
        }
        viewModelScope.launch {
            storeProfileRepository.update(name.trim(), address.trim(), phone.trim(), footer.trim())
            _events.emit(StoreProfileEvent.ShowMessage("Profil toko tersimpan"))
        }
    }

    /**
     * Simpan gambar QRIS baru, lalu coba baca ulang kode QR di dalamnya untuk mendapatkan
     * payload EMVCo mentah — bila berhasil, QRIS Dinamis (nominal otomatis) aktif di layar
     * pembayaran; bila gagal (gambar bukan kode QR EMVCo yang valid), fallback ke gambar statis.
     */
    fun setQrisImagePath(path: String?) {
        viewModelScope.launch {
            storeProfileRepository.updateQrisImagePath(path)
            if (path == null) {
                storeProfileRepository.updateQrisRawContent(null)
                _events.emit(StoreProfileEvent.ShowMessage("Gambar QRIS dihapus"))
                return@launch
            }
            val rawContent = qrisImageDecoder.decode(path)
            storeProfileRepository.updateQrisRawContent(rawContent)
            _events.emit(
                StoreProfileEvent.ShowMessage(
                    if (rawContent != null) "Gambar QRIS tersimpan — QRIS Dinamis aktif (nominal otomatis)"
                    else "Gambar QRIS tersimpan, tapi kode QR tidak terbaca — nominal tidak akan otomatis terisi"
                )
            )
        }
    }

    fun setPinLoginEnabled(enabled: Boolean) {
        viewModelScope.launch { storeProfileRepository.setPinLoginEnabled(enabled) }
    }

    /** Simpan logo toko baru (dipakai di header struk cetak & PDF invoice), atau null untuk menghapus. */
    fun setLogoImagePath(path: String?) {
        viewModelScope.launch {
            storeProfileRepository.updateLogoImagePath(path)
            _events.emit(
                StoreProfileEvent.ShowMessage(if (path != null) "Logo toko tersimpan" else "Logo toko dihapus")
            )
        }
    }

    /** @param hex Format "#RRGGBB", atau null untuk kembali ke warna default aplikasi. */
    fun setAppColor(hex: String?) {
        viewModelScope.launch {
            storeProfileRepository.updateAppColorHex(hex)
            _events.emit(
                StoreProfileEvent.ShowMessage(if (hex != null) "Warna aplikasi diperbarui" else "Warna aplikasi dikembalikan ke default")
            )
        }
    }

    /** Ganti font tampilan aplikasi (lihat PosFontOption di presentation/theme/Font.kt). */
    fun setFontChoice(fontKey: String) {
        viewModelScope.launch {
            storeProfileRepository.updateFontChoice(fontKey)
            _events.emit(StoreProfileEvent.ShowMessage("Font aplikasi diperbarui"))
        }
    }

    /** Ganti bahasa yang dipakai di struk cetak & PDF invoice ("id" atau "en"). */
    fun setReceiptLanguage(languageCode: String) {
        viewModelScope.launch {
            storeProfileRepository.updateReceiptLanguage(languageCode)
            _events.emit(
                StoreProfileEvent.ShowMessage(if (languageCode == "en") "Bahasa struk diubah ke English" else "Bahasa struk diubah ke Indonesia")
            )
        }
    }

    /** Aktifkan/nonaktifkan pajak (PPN) & atur persentasenya. Berlaku untuk transaksi berikutnya. */
    fun setTaxSettings(enabled: Boolean, percent: Double) {
        viewModelScope.launch {
            storeProfileRepository.updateTaxSettings(enabled, percent.coerceIn(0.0, 100.0))
            _events.emit(
                StoreProfileEvent.ShowMessage(if (enabled) "Pajak diaktifkan (${percent}%)" else "Pajak dinonaktifkan")
            )
        }
    }

    /** Tambah satu nominal cepat Cash baru (mis. 50000) ke daftar tombol cepat di layar Pembayaran. */
    fun addQuickCashAmount(amount: Long) {
        if (amount <= 0) return
        viewModelScope.launch {
            val current = storeProfileRepository.profile.first().quickCashAmountList()
            val updated = (current + amount).distinct().sorted()
            storeProfileRepository.updateQuickCashAmounts(updated.joinToString(","))
            _events.emit(StoreProfileEvent.ShowMessage("Nominal cepat ditambahkan"))
        }
    }

    /** Hapus satu nominal cepat Cash dari daftar tombol cepat di layar Pembayaran. */
    fun removeQuickCashAmount(amount: Long) {
        viewModelScope.launch {
            val current = storeProfileRepository.profile.first().quickCashAmountList()
            val updated = current.filter { it != amount }
            storeProfileRepository.updateQuickCashAmounts(updated.joinToString(","))
            _events.emit(StoreProfileEvent.ShowMessage("Nominal cepat dihapus"))
        }
    }

    /** Daftar nama printer Bluetooth yang sudah di-pair di sistem, untuk pilihan di UI Pengaturan
     * (dulu fungsi ini sudah ada di PrinterRepository tapi tidak pernah dipakai di mana pun —
     * struk selalu tercetak ke printer pertama yang ditemukan walau toko punya >1 printer). */
    fun listPairedPrinters(): List<String> = printerRepository.listPairedPrinters()

    /** @param name Nama printer yang dipilih pengguna dari [listPairedPrinters], atau null untuk
     * kembali ke perilaku default (pakai printer ter-pairing pertama). */
    fun setSelectedPrinter(name: String?) {
        viewModelScope.launch {
            storeProfileRepository.setSelectedPrinterName(name)
            _events.emit(
                StoreProfileEvent.ShowMessage(if (name != null) "Printer struk diatur ke \"$name\"" else "Kembali memakai printer ter-pairing pertama")
            )
        }
    }
}
