package com.example.posapp.presentation.shift

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.posapp.data.auth.SessionManager
import com.example.posapp.data.local.entity.ShiftEntity
import com.example.posapp.data.repository.CloseShiftResult
import com.example.posapp.data.repository.OpenShiftResult
import com.example.posapp.data.repository.ShiftRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ShiftEvent {
    data class ShowMessage(val message: String) : ShiftEvent()
    data class ShiftClosed(val expectedCash: Double, val actualCash: Double, val difference: Double) : ShiftEvent()
}

@HiltViewModel
class ShiftViewModel @Inject constructor(
    private val shiftRepository: ShiftRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    val activeShift: StateFlow<ShiftEntity?> = shiftRepository.observeActiveShift()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val history: StateFlow<List<ShiftEntity>> = shiftRepository.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _events = MutableSharedFlow<ShiftEvent>()
    val events: SharedFlow<ShiftEvent> = _events

    /** Nama kasir yang akan tercatat di shift baru — dari sesi login, atau default untuk mode single-user. */
    fun currentCashierLabel(): String = sessionManager.currentUser.value?.name ?: "Pemilik Toko"

    fun openShift(startCash: Double) {
        viewModelScope.launch {
            val user = sessionManager.currentUser.value
            when (val result = shiftRepository.openShift(user?.id, user?.name ?: "Pemilik Toko", startCash)) {
                is OpenShiftResult.Success -> _events.emit(ShiftEvent.ShowMessage("Shift dibuka"))
                is OpenShiftResult.Error -> _events.emit(ShiftEvent.ShowMessage(result.message))
            }
        }
    }

    fun closeShift(actualCash: Double, note: String?) {
        viewModelScope.launch {
            when (val result = shiftRepository.closeShift(actualCash, note?.takeIf { it.isNotBlank() })) {
                is CloseShiftResult.Success -> _events.emit(
                    ShiftEvent.ShiftClosed(result.expectedCash, actualCash, result.difference)
                )
                is CloseShiftResult.Error -> _events.emit(ShiftEvent.ShowMessage(result.message))
            }
        }
    }
}
