package com.example.posapp.presentation.pos

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.posapp.data.local.entity.PaymentMethod
import com.example.posapp.data.local.entity.ProductEntity
import com.example.posapp.domain.model.Cart
import java.text.NumberFormat
import java.util.Locale

private val rupiah: NumberFormat = NumberFormat.getCurrencyInstance(Locale("in", "ID"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosScreen(
    viewModel: PosViewModel = hiltViewModel(),
    onOpenScanner: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    var showPaymentSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is PosEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                is PosEvent.CheckoutSuccess -> {
                    showPaymentSheet = false
                    snackbarHostState.showSnackbar(
                        "Transaksi ${event.invoiceNumber} berhasil. Kembalian: ${rupiah.format(event.change)}"
                    )
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(title = { Text("Kasir") })
        }
    ) { padding ->
        Row(modifier = Modifier.padding(padding).fillMaxSize()) {
            // --- Panel Kiri: Grid Produk ---
            Column(modifier = Modifier.weight(1.4f).fillMaxHeight().padding(8.dp)) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = viewModel::onSearchQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Cari produk atau scan barcode...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = onOpenScanner) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan")
                        }
                    },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 140.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.products, key = { it.id }) { product ->
                        ProductCard(product = product, onClick = { viewModel.addToCart(product) })
                    }
                }
            }

            Divider(modifier = Modifier.fillMaxHeight().width(1.dp))

            // --- Panel Kanan: Keranjang ---
            Column(modifier = Modifier.weight(1f).fillMaxHeight().padding(8.dp)) {
                Text("Keranjang", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))

                if (uiState.cart.isEmpty) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("Belum ada item", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(uiState.cart.lines, key = { it.product.id }) { line ->
                            CartLineRow(
                                name = line.product.name,
                                price = line.product.sellPrice,
                                quantity = line.quantity,
                                onIncrease = { viewModel.updateQuantity(line.product.id, line.quantity + 1) },
                                onDecrease = { viewModel.updateQuantity(line.product.id, line.quantity - 1) }
                            )
                        }
                    }
                }

                CartSummary(cart = uiState.cart)

                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { showPaymentSheet = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.cart.isEmpty && !uiState.isProcessing
                ) {
                    Text("Bayar (${rupiah.format(uiState.cart.total)})")
                }
            }
        }
    }

    if (showPaymentSheet) {
        PaymentModal(
            cart = uiState.cart,
            isProcessing = uiState.isProcessing,
            onDismiss = { showPaymentSheet = false },
            onConfirm = { method, amountPaid -> viewModel.checkout(method, amountPaid) }
        )
    }
}

@Composable
private fun ProductCard(product: ProductEntity, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(product.name, fontWeight = FontWeight.SemiBold, maxLines = 2)
            Spacer(Modifier.height(4.dp))
            Text(rupiah.format(product.sellPrice), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(2.dp))
            val stockColor = if (product.stock <= product.lowStockThreshold)
                MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            Text("Stok: ${product.stock}", style = MaterialTheme.typography.bodySmall, color = stockColor)
        }
    }
}

@Composable
private fun CartLineRow(
    name: String,
    price: Double,
    quantity: Int,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, maxLines = 1)
            Text(rupiah.format(price), style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onClick = onDecrease) { Icon(Icons.Default.Remove, contentDescription = "Kurangi") }
        Text("$quantity")
        IconButton(onClick = onIncrease) { Icon(Icons.Default.Add, contentDescription = "Tambah") }
    }
}

@Composable
private fun CartSummary(cart: Cart) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Divider()
        SummaryRow("Subtotal", rupiah.format(cart.subtotal))
        SummaryRow("Diskon", "- " + rupiah.format(cart.transactionDiscount))
        SummaryRow("Pajak (${cart.taxPercent}%)", rupiah.format(cart.taxAmount))
        Divider()
        SummaryRow("Total", rupiah.format(cart.total), emphasize = true)
    }
}

@Composable
private fun SummaryRow(label: String, value: String, emphasize: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Normal)
        Text(value, fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Normal)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentModal(
    cart: Cart,
    isProcessing: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (PaymentMethod, Double) -> Unit
) {
    var selectedMethod by remember { mutableStateOf(PaymentMethod.CASH) }
    var amountPaidText by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(16.dp)) {
            Text("Pembayaran", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Text("Total: ${rupiah.format(cart.total)}", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))

            SingleChoiceSegmented(
                options = listOf("Cash" to PaymentMethod.CASH, "Debit/Kredit" to PaymentMethod.DEBIT_CREDIT, "QRIS" to PaymentMethod.QRIS),
                selected = selectedMethod,
                onSelect = { selectedMethod = it }
            )

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = amountPaidText,
                onValueChange = { amountPaidText = it.filter { c -> c.isDigit() } },
                label = { Text("Jumlah dibayar") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    val amount = amountPaidText.toDoubleOrNull() ?: cart.total
                    onConfirm(selectedMethod, amount)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isProcessing
            ) {
                Text(if (isProcessing) "Memproses..." else "Konfirmasi Pembayaran")
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SingleChoiceSegmented(
    options: List<Pair<String, PaymentMethod>>,
    selected: PaymentMethod,
    onSelect: (PaymentMethod) -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (label, method) ->
            FilterChip(
                selected = selected == method,
                onClick = { onSelect(method) },
                label = { Text(label) }
            )
        }
    }
}
