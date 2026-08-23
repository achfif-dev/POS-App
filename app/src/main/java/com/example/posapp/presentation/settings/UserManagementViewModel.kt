package com.example.posapp.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.posapp.data.local.entity.UserEntity
import com.example.posapp.data.local.entity.UserRole
import com.example.posapp.data.repository.UserRepository
import com.example.posapp.data.settings.StoreProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UserManagementUiState(
    val users: List<UserEntity> = emptyList(),
    val pinLoginEnabled: Boolean = false
)

sealed class UserManagementEvent {
    data class ShowMessage(val message: String) : UserManagementEvent()
}

@HiltViewModel
class UserManagementViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val storeProfileRepository: StoreProfileRepository
) : ViewModel() {

    private val _events = MutableSharedFlow<UserManagementEvent>()
    val events: SharedFlow<UserManagementEvent> = _events

    val uiState: StateFlow<UserManagementUiState> = combine(
        userRepository.observeAll(), storeProfileRepository.profile
    ) { users, profile ->
        UserManagementUiState(users = users, pinLoginEnabled = profile.pinLoginEnabled)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserManagementUiState())

    fun setPinLoginEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled && userRepository.hasAnyUser().not()) {
                _events.emit(UserManagementEvent.ShowMessage("Tambahkan minimal satu kasir/admin dengan PIN terlebih dahulu"))
                return@launch
            }
            storeProfileRepository.setPinLoginEnabled(enabled)
        }
    }

    fun addUser(name: String, pin: String, role: UserRole) {
        if (name.isBlank() || pin.length < 4) {
            viewModelScope.launch { _events.emit(UserManagementEvent.ShowMessage("Nama & PIN (min 4 digit) wajib diisi")) }
            return
        }
        viewModelScope.launch {
            try {
                userRepository.createUser(name, pin, role)
                _events.emit(UserManagementEvent.ShowMessage("Pengguna \"$name\" ditambahkan"))
            } catch (e: Exception) {
                _events.emit(UserManagementEvent.ShowMessage("Gagal menambah pengguna: nama mungkin sudah dipakai"))
            }
        }
    }

    fun deleteUser(user: UserEntity) {
        viewModelScope.launch {
            userRepository.deleteUser(user.id)
            _events.emit(UserManagementEvent.ShowMessage("${user.name} dihapus"))
        }
    }
}
