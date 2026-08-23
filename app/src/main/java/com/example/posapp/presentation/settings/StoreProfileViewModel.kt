package com.example.posapp.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.posapp.data.qris.QrisImageDecoder
import com.example.posapp.data.settings.StoreProfile
import com.example.posapp.data.settings.StoreProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class StoreProfileEvent {
    data class ShowMessage(val message: String) : StoreProfileEvent()
}

@HiltViewModel
class StoreProfileViewModel @Inject constructor(
    private val storeProfileRepository: StoreProfileRepository,
    private val qrisImageDecoder: QrisImageDecoder
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

    /** Aktifkan/nonaktifkan pajak (PPN) & atur persentasenya. Berlaku untuk transaksi berikutnya. */
    fun setTaxSettings(enabled: Boolean, percent: Double) {
        viewModelScope.launch {
            storeProfileRepository.updateTaxSettings(enabled, percent.coerceIn(0.0, 100.0))
            _events.emit(
                StoreProfileEvent.ShowMessage(if (enabled) "Pajak diaktifkan (${percent}%)" else "Pajak dinonaktifkan")
            )
        }
    }
}
