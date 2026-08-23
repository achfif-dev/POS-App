package com.example.posapp.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.posapp.data.auth.SessionManager
import com.example.posapp.data.local.entity.UserRole
import com.example.posapp.data.repository.UserRepository
import com.example.posapp.data.settings.StoreProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val pin: String = "",
    val isFirstRun: Boolean = false, // belum ada user sama sekali -> minta buat PIN admin pertama
    val errorMessage: String? = null,
    val isLoading: Boolean = false,
    val loginSuccess: Boolean = false
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager,
    private val storeProfileRepository: StoreProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val hasUser = userRepository.hasAnyUser()
            _uiState.value = _uiState.value.copy(isFirstRun = !hasUser)
        }
    }

    fun onDigit(digit: String) {
        val current = _uiState.value
        if (current.pin.length >= 6) return
        _uiState.value = current.copy(pin = current.pin + digit, errorMessage = null)
    }

    fun onBackspace() {
        val current = _uiState.value
        _uiState.value = current.copy(pin = current.pin.dropLast(1), errorMessage = null)
    }

    fun onClear() {
        _uiState.value = _uiState.value.copy(pin = "", errorMessage = null)
    }

    fun submit() {
        val pin = _uiState.value.pin
        if (pin.length < 4) {
            _uiState.value = _uiState.value.copy(errorMessage = "PIN minimal 4 digit")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            if (_uiState.value.isFirstRun) {
                userRepository.createUser("Admin", pin, UserRole.ADMIN)
                // Ambil kembali entity yang baru dibuat (dengan pinHash yang benar) alih-alih
                // memakai entity kosong, supaya sesi login konsisten dengan data di database.
                val createdUser = userRepository.login(pin)
                if (createdUser != null) {
                    sessionManager.login(createdUser)
                    // Admin pertama sudah dibuat -> aktifkan proteksi PIN secara otomatis
                    // supaya aplikasi benar-benar meminta login di pembukaan berikutnya.
                    storeProfileRepository.setPinLoginEnabled(true)
                }
                _uiState.value = _uiState.value.copy(isLoading = false, loginSuccess = true)
            } else {
                val user = userRepository.login(pin)
                if (user != null) {
                    sessionManager.login(user)
                    _uiState.value = _uiState.value.copy(isLoading = false, loginSuccess = true)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false, pin = "", errorMessage = "PIN salah, coba lagi"
                    )
                }
            }
        }
    }
}
