package com.example.posapp.presentation.pos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.posapp.data.auth.SessionManager
import com.example.posapp.data.export.FileShareHelper
import com.example.posapp.data.export.PdfInvoiceGenerator
import com.example.posapp.data.local.entity.PaymentMethod
import com.example.posapp.data.local.entity.ProductEntity
import com.example.posapp.data.local.entity.ProductVariantEntity
import com.example.posapp.data.local.entity.TransactionEntity
import com.example.posapp.data.local.entity.TransactionItemEntity
import com.example.posapp.data.local.entity.UserRole
import com.example.posapp.data.printer.PrintResult
import com.example.posapp.data.printer.PrinterRepository
import com.example.posapp.data.repository.ProductRepository
import com.example.posapp.data.repository.TransactionRepository
import com.example.posapp.data.settings.StoreProfile
import com.example.posapp.data.settings.StoreProfileRepository
import com.example.posapp.domain.model.Cart
import com.example.posapp.domain.model.CartLine
import com.example.posapp.domain.usecase.CheckoutResult
import com.example.posapp.domain.usecase.CheckoutUseCase
import com.example.posapp.domain.usecase.PaymentSplit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class PosUiState(
    val products: List<ProductEntity> = emptyList(),
    val searchQuery: String = "",
    val selectedCategoryId: Long? = null,
    val cart: Cart = Cart(),
    val isProcessing: Boolean = false,
    val storeProfile: StoreProfile = StoreProfile(),
    val cashierName: String? = null,
    val isAdmin: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PosViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val checkoutUseCase: CheckoutUseCase,
    private val transactionRepository: TransactionRepository,
    private val printerRepository: PrinterRepository,
    private val pdfInvoiceGenerator: PdfInvoiceGenerator,
    private val fileShareHelper: FileShareHelper,
    private val storeProfileRepository: StoreProfileRepository,
    private val sessionManager: SessionManager
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
        _isProcessing,
        storeProfileRepository.profile,
        sessionManager.currentUser
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val products = values[0] as List<ProductEntity>
        val query = values[1] as String
        val categoryId = values[2] as Long?
        val cart = values[3] as Cart
        val processing = values[4] as Boolean
        val profile = values[5] as StoreProfile
        val user = values[6] as com.example.posapp.data.local.entity.UserEntity?
        PosUiState(
            products = products,
            searchQuery = query,
            selectedCategoryId = categoryId,
            cart = cart,
            isProcessing = processing,
            storeProfile = profile,
            cashierName = user?.name,
            isAdmin = user == null || user.role == UserRole.ADMIN
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PosUiState())

    private val _lastReceipt = MutableStateFlow<Pair<TransactionEntity, List<TransactionItemEntity>>?>(null)
    val lastReceipt: StateFlow<Pair<TransactionEntity, List<TransactionItemEntity>>?> = _lastReceipt.asStateFlow()

    fun dismissReceipt() {
        _lastReceipt.value = null
    }

    fun printReceipt() {
        val receipt = _lastReceipt.value ?: return
        val profile = uiState.value.storeProfile
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                printerRepository.printReceipt(
                    storeName = profile.name,
                    transaction = receipt.first,
                    items = receipt.second,
                    storeAddress = profile.address,
                    receiptFooter = profile.receiptFooter
                )
            }
            when (result) {
                is PrintResult.Success -> _events.emit(PosEvent.ShowMessage("Struk berhasil dicetak"))
                is PrintResult.Error -> _events.emit(PosEvent.ShowMessage(result.message))
            }
        }
    }

    fun exportReceiptPdf() {
        val receipt = _lastReceipt.value ?: return
        val profile = uiState.value.storeProfile
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                pdfInvoiceGenerator.generate(
                    storeName = profile.name,
                    transaction = receipt.first,
                    items = receipt.second,
                    storeAddress = profile.address,
                    receiptFooter = profile.receiptFooter
                )
            }
            _events.emit(PosEvent.PdfReady(file))
        }
    }

    fun createShareIntent(file: File) = fileShareHelper.createShareIntent(file, "application/pdf")

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onCategorySelected(categoryId: Long?) {
        _selectedCategoryId.value = categoryId
    }

    fun addToCartBySku(sku: String) {
        viewModelScope.launch {
            val product = productRepository.findBySku(sku)
            if (product != null) {
                addToCart(product)
                return@launch
            }
            val variant = productRepository.findVariantBySku(sku)
            if (variant != null) {
                val allProducts = uiState.value.products
                val parent = allProducts.find { it.id == variant.productId }
                if (parent != null) {
                    addToCart(parent, variant)
                    return@launch
                }
            }
            _events.emit(PosEvent.ShowMessage("Produk dengan barcode \"$sku\" tidak ditemukan"))
        }
    }

    /** Ambil daftar varian aktif suatu produk (dipakai bottom sheet pemilih varian di POS). */
    suspend fun getVariantsFor(productId: Long): List<ProductVariantEntity> =
        productRepository.getVariants(productId)

    fun addToCart(product: ProductEntity, variant: ProductVariantEntity? = null) {
        val availableStock = variant?.stock ?: product.stock
        if (availableStock <= 0) {
            viewModelScope.launch { _events.emit(PosEvent.ShowMessage("Stok ${product.name} habis")) }
            return
        }
        val current = _cart.value
        val key = "${product.id}:${variant?.id ?: 0}"
        val existingIndex = current.lines.indexOfFirst { it.lineKey == key }
        val newLines = if (existingIndex >= 0) {
            current.lines.toMutableList().apply {
                val line = this[existingIndex]
                if (line.quantity + 1 > availableStock) {
                    return@apply
                }
                this[existingIndex] = line.copy(quantity = line.quantity + 1)
            }
        } else {
            current.lines + CartLine(product = product, variant = variant, quantity = 1)
        }
        _cart.value = current.copy(lines = newLines)
    }

    fun updateQuantity(lineKey: String, quantity: Int) {
        val current = _cart.value
        val newLines = if (quantity <= 0) {
            current.lines.filterNot { it.lineKey == lineKey }
        } else {
            current.lines.map { if (it.lineKey == lineKey) it.copy(quantity = quantity) else it }
        }
        _cart.value = current.copy(lines = newLines)
    }

    fun updateLineDiscount(lineKey: String, discount: Double) {
        val current = _cart.value
        _cart.value = current.copy(
            lines = current.lines.map {
                if (it.lineKey == lineKey) it.copy(discount = discount) else it
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

    fun checkout(payments: List<PaymentSplit>) {
        viewModelScope.launch {
            _isProcessing.value = true
            val cashierName = sessionManager.currentUser.value?.name
            when (val result = checkoutUseCase(_cart.value, payments, cashierName = cashierName)) {
                is CheckoutResult.Success -> {
                    _lastReceipt.value = transactionRepository.getTransactionWithItems(result.transactionId)
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

    fun logout() = sessionManager.logout()
}

sealed class PosEvent {
    data class ShowMessage(val message: String) : PosEvent()
    data class CheckoutSuccess(val transactionId: Long, val invoiceNumber: String, val change: Double) : PosEvent()
    data class PdfReady(val file: File) : PosEvent()
}
