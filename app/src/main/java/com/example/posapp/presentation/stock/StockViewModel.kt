package com.example.posapp.presentation.stock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.posapp.data.local.entity.ProductEntity
import com.example.posapp.data.local.entity.ProductVariantEntity
import com.example.posapp.data.local.entity.StockAdjustmentEntity
import com.example.posapp.data.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class StockEvent {
    data class ShowMessage(val message: String) : StockEvent()
}

@HiltViewModel
class StockViewModel @Inject constructor(
    private val productRepository: ProductRepository
) : ViewModel() {

    val products: StateFlow<List<ProductEntity>> = productRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lowStockProducts: StateFlow<List<ProductEntity>> = productRepository.observeLowStock()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Riwayat penyesuaian stok gabungan (produk biasa & tiap kombinasi varian), terbaru dulu. */
    val adjustmentHistory: StateFlow<List<StockAdjustmentEntity>> = productRepository.observeAdjustmentHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _events = MutableSharedFlow<StockEvent>()
    val events: SharedFlow<StockEvent> = _events

    /**
     * @param type "IN" untuk stok masuk, "OUT" untuk stok keluar/rusak, "OPNAME" untuk set
     * stok ke jumlah hasil hitung fisik langsung (bukan penambahan/pengurangan).
     */
    fun adjustStock(productId: Long, type: String, quantity: Int, reason: String?) {
        if (quantity < 0) {
            viewModelScope.launch { _events.emit(StockEvent.ShowMessage("Jumlah tidak boleh negatif")) }
            return
        }
        viewModelScope.launch {
            productRepository.adjustStock(productId, type, quantity, reason)
            _events.emit(StockEvent.ShowMessage("Stok berhasil diperbarui"))
        }
    }

    /** Penyesuaian stok untuk satu kombinasi varian tertentu (mis. "Merah / L"). */
    fun adjustVariantStock(productId: Long, variant: ProductVariantEntity, type: String, quantity: Int, reason: String?) {
        if (quantity < 0) {
            viewModelScope.launch { _events.emit(StockEvent.ShowMessage("Jumlah tidak boleh negatif")) }
            return
        }
        viewModelScope.launch {
            productRepository.adjustVariantStock(productId, variant.id, variant.variantLabel, type, quantity, reason)
            _events.emit(StockEvent.ShowMessage("Stok varian \"${variant.variantLabel}\" berhasil diperbarui"))
        }
    }

    suspend fun getVariantsFor(productId: Long): List<ProductVariantEntity> =
        productRepository.getVariants(productId)
}
