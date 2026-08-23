package com.example.posapp.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    private val storeProfileRepository: StoreProfileRepository
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

    fun setQrisImagePath(path: String?) {
        viewModelScope.launch {
            storeProfileRepository.updateQrisImagePath(path)
            _events.emit(StoreProfileEvent.ShowMessage(if (path != null) "Gambar QRIS tersimpan" else "Gambar QRIS dihapus"))
        }
    }

    fun setPinLoginEnabled(enabled: Boolean) {
        viewModelScope.launch { storeProfileRepository.setPinLoginEnabled(enabled) }
    }
}
