package com.example.posapp.presentation.pos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.posapp.data.local.entity.PaymentMethod
import com.example.posapp.data.local.entity.ProductEntity
import com.example.posapp.data.repository.ProductRepository
import com.example.posapp.domain.model.Cart
import com.example.posapp.domain.model.CartLine
import com.example.posapp.domain.usecase.CheckoutResult
import com.example.posapp.domain.usecase.CheckoutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PosUiState(
    val products: List<ProductEntity> = emptyList(),
    val searchQuery: String = "",
    val selectedCategoryId: Long? = null,
    val cart: Cart = Cart(),
    val isProcessing: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PosViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val checkoutUseCase: CheckoutUseCase
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedCategoryId = MutableStateFlow<Long?>(null)
    private val _cart = MutableStateFlow(Cart())
    private val _isProcessing = MutableStateFlow(false)

    private val _events = MutableSharedFlow<PosEvent>()
    val events: SharedFlow<PosEvent> = _events

    // Flow produk mengikuti perubahan query pencarian & kategori terpilih secara reaktif
    private val productsFlow = combine(_searchQuery, _selectedCategoryId) { q, c -> q to c }
        .flatMapLatest { (query, categoryId) -> productRepository.search(query, categoryId) }

    val uiState: StateFlow<PosUiState> = combine(
        productsFlow,
        _searchQuery,
        _selectedCategoryId,
        _cart,
        _isProcessing
    ) { products, query, categoryId, cart, processing ->
        PosUiState(
            products = products,
            searchQuery = query,
            selectedCategoryId = categoryId,
            cart = cart,
            isProcessing = processing
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PosUiState())

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onCategorySelected(categoryId: Long?) {
        _selectedCategoryId.value = categoryId
    }

    fun addToCart(product: ProductEntity) {
        if (product.stock <= 0) {
            viewModelScope.launch { _events.emit(PosEvent.ShowMessage("Stok ${product.name} habis")) }
            return
        }
        val current = _cart.value
        val existingIndex = current.lines.indexOfFirst { it.product.id == product.id }
        val newLines = if (existingIndex >= 0) {
            current.lines.toMutableList().apply {
                val line = this[existingIndex]
                this[existingIndex] = line.copy(quantity = line.quantity + 1)
            }
        } else {
            current.lines + CartLine(product = product, quantity = 1)
        }
        _cart.value = current.copy(lines = newLines)
    }

    fun updateQuantity(productId: Long, quantity: Int) {
        val current = _cart.value
        val newLines = if (quantity <= 0) {
            current.lines.filterNot { it.product.id == productId }
        } else {
            current.lines.map { if (it.product.id == productId) it.copy(quantity = quantity) else it }
        }
        _cart.value = current.copy(lines = newLines)
    }

    fun updateLineDiscount(productId: Long, discount: Double) {
        val current = _cart.value
        _cart.value = current.copy(
            lines = current.lines.map {
                if (it.product.id == productId) it.copy(discount = discount) else it
            }
        )
    }

    fun updateTransactionDiscount(discount: Double) {
        _cart.value = _cart.value.copy(transactionDiscount = discount)
    }

    fun updateTaxPercent(percent: Double) {
        _cart.value = _cart.value.copy(taxPercent = percent)
    }

    fun clearCart() {
        _cart.value = Cart()
    }

    fun checkout(paymentMethod: PaymentMethod, amountPaid: Double) {
        viewModelScope.launch {
            _isProcessing.value = true
            when (val result = checkoutUseCase(_cart.value, paymentMethod, amountPaid)) {
                is CheckoutResult.Success -> {
                    _events.emit(PosEvent.CheckoutSuccess(result.transactionId, result.invoiceNumber, result.change))
                    clearCart()
                }
                is CheckoutResult.Error -> {
                    _events.emit(PosEvent.ShowMessage(result.message))
                }
            }
            _isProcessing.value = false
        }
    }
}

sealed class PosEvent {
    data class ShowMessage(val message: String) : PosEvent()
    data class CheckoutSuccess(val transactionId: Long, val invoiceNumber: String, val change: Double) : PosEvent()
}
