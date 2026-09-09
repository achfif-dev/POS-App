package com.example.posapp.presentation.license

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.posapp.data.license.LicenseActivationResult
import com.example.posapp.data.license.LicenseRepository
import com.example.posapp.data.license.LicenseState
import com.example.posapp.data.license.LicenseStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LicenseUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val justActivated: Boolean = false,
)

@HiltViewModel
class LicenseViewModel @Inject constructor(
    private val repository: LicenseRepository,
) : ViewModel() {

    val licenseState: StateFlow<LicenseState> = repository.state.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), LicenseState(status = LicenseStatus.NOT_ACTIVATED)
    )

    private val _uiState = MutableStateFlow(LicenseUiState())
    val uiState: StateFlow<LicenseUiState> = _uiState.asStateFlow()

    fun activate(licenseKey: String) {
        if (licenseKey.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Masukkan kode lisensi terlebih dahulu.")
            return
        }
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = repository.activate(licenseKey)) {
                is LicenseActivationResult.Success ->
                    _uiState.value = LicenseUiState(isLoading = false, justActivated = true)
                is LicenseActivationResult.Error ->
                    _uiState.value = LicenseUiState(isLoading = false, errorMessage = result.message)
            }
        }
    }

    /** Dipanggil dari tombol "Cek Status Lisensi" saat status REVOKED — MURNI mengecek ulang ke
     * server apakah lisensi sudah diaktifkan kembali oleh penjual, BUKAN aktivasi ulang (tidak
     * perlu memasukkan kode lagi, licenseKey yang sudah tersimpan dipakai lagi). Beda dari
     * [activate]: `justActivated` hanya di-set true kalau hasil cek menunjukkan lisensi memang
     * ACTIVE sekarang — supaya tombol ini tidak "keluar" dari layar Aktivasi kalau ternyata
     * masih REVOKED.
     */
    fun checkStatusNow() {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = repository.checkStatus()) {
                is LicenseActivationResult.Success ->
                    _uiState.value = LicenseUiState(
                        isLoading = false,
                        justActivated = result.state.status == LicenseStatus.ACTIVE,
                        errorMessage = result.state.lastError,
                    )
                is LicenseActivationResult.Error ->
                    _uiState.value = LicenseUiState(isLoading = false, errorMessage = result.message)
            }
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
