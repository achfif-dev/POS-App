package com.example.posapp.presentation.pos

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.posapp.data.local.entity.PaymentMethod
import com.example.posapp.data.local.entity.ProductEntity
import com.example.posapp.data.local.entity.ProductVariantEntity
import com.example.posapp.data.local.entity.TransactionEntity
import com.example.posapp.data.local.entity.TransactionItemEntity
import com.example.posapp.domain.model.Cart
import java.text.NumberFormat
import java.util.Locale

private val rupiah: NumberFormat = NumberFormat.getCurrencyInstance(Locale("in", "ID"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosScreen(
    viewModel: PosViewModel = hiltViewModel(),
    onOpenScanner: () -> Unit = {},
    onOpenProducts: () -> Unit = {},
    onOpenReports: () -> Unit = {},
    onOpenStock: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onLogout: () -> Unit = {},
    scannedSku: String? = null,
    onScannedSkuConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val lastReceipt by viewModel.lastReceipt.collectAsState()
    var showPaymentSheet by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var variantPickerProduct by remember { mutableStateOf<ProductEntity?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val bluetoothPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.printReceipt() }

    fun requestPrint() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.BLUETOOTH_CONNECT
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (granted) viewModel.printReceipt() else bluetoothPermissionLauncher.launch(android.Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            viewModel.printReceipt()
        }
    }

    LaunchedEffect(scannedSku) {
        if (scannedSku != null) {
            viewModel.addToCartBySku(scannedSku)
            onScannedSkuConsumed()
        }
    }

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
                is PosEvent.PdfReady -> {
                    context.startActivity(
                        Intent.createChooser(viewModel.createShareIntent(event.file), "Bagikan Invoice PDF")
                    )
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Kasir")
                        uiState.cashierName?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                actions = {
                    TextButton(onClick = onOpenProducts) { Text("Produk") }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu lainnya")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Stok & Inventaris") }, onClick = { showMenu = false; onOpenStock() })
                        DropdownMenuItem(text = { Text("Laporan Penjualan") }, onClick = { showMenu = false; onOpenReports() })
                        DropdownMenuItem(text = { Text("Pengaturan") }, onClick = { showMenu = false; onOpenSettings() })
                        if (uiState.cashierName != null) {
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Keluar (Logout)") },
                                leadingIcon = { Icon(Icons.Default.Logout, contentDescription = null) },
                                onClick = { showMenu = false; onLogout() }
                            )
                        }
                    }
                }
            )
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
                    singleLine = true,
                    shape = MaterialTheme.shapes.large
                )
                Spacer(Modifier.height(8.dp))
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 140.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.products, key = { it.id }) { product ->
                        ProductCard(
                            product = product,
                            onClick = {
                                if (product.hasVariants) {
                                    variantPickerProduct = product
                                } else {
                                    viewModel.addToCart(product)
                                }
                            }
                        )
                    }
                }
            }

            VerticalDivider(modifier = Modifier.fillMaxHeight())

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
                        items(uiState.cart.lines, key = { it.lineKey }) { line ->
                            CartLineRow(
                                name = line.product.name + (line.variant?.let { " (${it.variantLabel})" } ?: ""),
                                price = line.unitPrice,
                                quantity = line.quantity,
                                onIncrease = { viewModel.updateQuantity(line.lineKey, line.quantity + 1) },
                                onDecrease = { viewModel.updateQuantity(line.lineKey, line.quantity - 1) }
                            )
                        }
                    }
                }

                CartSummary(cart = uiState.cart)

                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { showPaymentSheet = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    enabled = !uiState.cart.isEmpty && !uiState.isProcessing
                ) {
                    Text("Bayar (${rupiah.format(uiState.cart.total)})")
                }
            }
        }
    }

    variantPickerProduct?.let { product ->
        VariantPickerSheet(
            product = product,
            viewModel = viewModel,
            onDismiss = { variantPickerProduct = null },
            onVariantSelected = { variant ->
                viewModel.addToCart(product, variant)
                variantPickerProduct = null
            }
        )
    }

    if (showPaymentSheet) {
        PaymentModal(
            cart = uiState.cart,
            qrisImagePath = uiState.storeProfile.qrisImagePath,
            isProcessing = uiState.isProcessing,
            onDismiss = { showPaymentSheet = false },
            onConfirm = { method, amountPaid -> viewModel.checkout(method, amountPaid) }
        )
    }

    lastReceipt?.let { (transaction, items) ->
        ReceiptDialog(
            transaction = transaction,
            items = items,
            onDismiss = { viewModel.dismissReceipt() },
            onPrint = { requestPrint() },
            onExportPdf = { viewModel.exportReceiptPdf() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VariantPickerSheet(
    product: ProductEntity,
    viewModel: PosViewModel,
    onDismiss: () -> Unit,
    onVariantSelected: (ProductVariantEntity) -> Unit
) {
    var variants by remember { mutableStateOf<List<ProductVariantEntity>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(product.id) {
        variants = viewModel.getVariantsFor(product.id)
        isLoading = false
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(16.dp).fillMaxWidth()) {
            Text(product.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Pilih varian", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))

            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else if (variants.isEmpty()) {
                Text("Belum ada varian untuk produk ini.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                variants.forEach { variant ->
                    val outOfStock = variant.stock <= 0
                    Card(
                        onClick = { if (!outOfStock) onVariantSelected(variant) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(variant.variantLabel, fontWeight = FontWeight.SemiBold)
                                Text(
                                    rupiah.format(variant.priceOverride ?: product.sellPrice),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Text(
                                if (outOfStock) "Stok habis" else "Stok: ${variant.stock}",
                                color = if (outOfStock) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ReceiptDialog(
    transaction: TransactionEntity,
    items: List<TransactionItemEntity>,
    onDismiss: () -> Unit,
    onPrint: () -> Unit,
    onExportPdf: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Transaksi Berhasil") },
        text = {
            Column {
                Text("No. Invoice: ${transaction.invoiceNumber}")
                Spacer(Modifier.height(4.dp))
                Text("Total: ${rupiah.format(transaction.total)}")
                Text("Kembalian: ${rupiah.format(transaction.changeAmount)}")
                Spacer(Modifier.height(8.dp))
                Text("${items.size} item terjual", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = onPrint) {
                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Cetak Struk")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onExportPdf) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("PDF")
                }
                TextButton(onClick = onDismiss) { Text("Tutup") }
            }
        }
    )
}

@Composable
private fun ProductCard(product: ProductEntity, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(12.dp)) {
            Text(product.name, fontWeight = FontWeight.SemiBold, maxLines = 2)
            Spacer(Modifier.height(4.dp))
            Text(rupiah.format(product.sellPrice), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(2.dp))
            if (product.hasVariants) {
                Text("Pilih varian", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            } else {
                val stockColor = if (product.stock <= product.lowStockThreshold)
                    MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                Text("Stok: ${product.stock}", style = MaterialTheme.typography.bodySmall, color = stockColor)
            }
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
        HorizontalDivider()
        SummaryRow("Subtotal", rupiah.format(cart.subtotal))
        SummaryRow("Diskon", "- " + rupiah.format(cart.transactionDiscount))
        SummaryRow("Pajak (${cart.taxPercent}%)", rupiah.format(cart.taxAmount))
        HorizontalDivider()
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
    qrisImagePath: String?,
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

            if (selectedMethod == PaymentMethod.QRIS) {
                Spacer(Modifier.height(16.dp))
                if (qrisImagePath != null) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        AsyncImage(
                            model = qrisImagePath,
                            contentDescription = "Kode QRIS",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(220.dp)
                        )
                    }
                } else {
                    Text(
                        "Gambar QRIS belum diunggah. Tambahkan lewat Pengaturan > Profil Toko.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

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
                shape = MaterialTheme.shapes.large,
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
