package com.example.posapp.presentation.pos

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
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
import com.example.posapp.presentation.theme.PosBrandedTopBar
import com.example.posapp.presentation.theme.ProductAvatar
import com.example.posapp.presentation.theme.StoreLogo
import com.example.posapp.presentation.theme.accentColorFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

private val rupiah: NumberFormat = NumberFormat.getCurrencyInstance(Locale("in", "ID"))

/** Format persentase pajak tanpa desimal berlebih, mis. 11.0 -> "11", 8.5 -> "8.5". */
private fun formatPercent(percent: Double): String =
    if (percent == percent.toLong().toDouble()) percent.toLong().toString() else percent.toString()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosScreen(
    viewModel: PosViewModel = hiltViewModel(),
    onOpenScanner: () -> Unit = {},
    onOpenProducts: () -> Unit = {},
    onOpenReports: () -> Unit = {},
    onOpenStock: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenDashboard: () -> Unit = {},
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
            PosBrandedTopBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StoreLogo(logoPath = uiState.storeProfile.logoImagePath, size = 32.dp)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(uiState.storeProfile.name, maxLines = 1)
                            Text(
                                "Kasir" + (uiState.cashierName?.let { " · $it" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    TextButton(onClick = onOpenProducts) {
                        Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Produk")
                    }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu lainnya")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Dashboard") },
                            leadingIcon = { Icon(Icons.Default.Storefront, contentDescription = null) },
                            onClick = { showMenu = false; onOpenDashboard() }
                        )
                        DropdownMenuItem(
                            text = { Text("Stok & Inventaris") },
                            leadingIcon = { Icon(Icons.Default.Inventory2, contentDescription = null) },
                            onClick = { showMenu = false; onOpenStock() }
                        )
                        DropdownMenuItem(
                            text = { Text("Laporan Penjualan") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null) },
                            onClick = { showMenu = false; onOpenReports() }
                        )
                        if (uiState.isAdmin) {
                            DropdownMenuItem(
                                text = { Text("Pengaturan") },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                onClick = { showMenu = false; onOpenSettings() }
                            )
                        }
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
                    // Diperlebar dari 140dp: kartu produk sekarang menaruh foto/ikon di
                    // samping (bukan di atas) nama & harga, jadi butuh ruang horizontal
                    // lebih agar teksnya tidak sempit/terpotong.
                    columns = GridCells.Adaptive(minSize = 180.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.products, key = { it.id }) { product ->
                        ProductCard(
                            product = product,
                            categoryName = product.categoryId?.let { uiState.categoryNamesById[it] },
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
                                unit = line.product.unit,
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
            qrisRawContent = uiState.storeProfile.qrisRawContent,
            isProcessing = uiState.isProcessing,
            onDismiss = { showPaymentSheet = false },
            onConfirm = { payments -> viewModel.checkout(payments) }
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
private fun ProductCard(product: ProductEntity, categoryName: String? = null, onClick: () -> Unit) {
    val accent = accentColorFor(product.name)
    val isLowStock = !product.hasVariants && product.stock <= product.lowStockThreshold

    // Foto/ikon produk diletakkan di SAMPING (kiri) nama & harga dalam satu Row,
    // bukan ditumpuk di atas teks — supaya kartu lebih ringkas seperti daftar produk
    // pada umumnya dan foto langsung terlihat berdampingan dengan detail produknya.
    // ProductAvatar (foto atau ikon sesuai kategori) sama persis dengan yang dipakai
    // di layar Produk & Stok, supaya produk yang sama terlihat konsisten di mana pun.
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            ProductAvatar(photoPath = product.photoPath, name = product.name, categoryName = categoryName, size = 48.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(product.name, fontWeight = FontWeight.SemiBold, maxLines = 2, modifier = Modifier.weight(1f))
                    if (isLowStock) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Default.WarningAmber,
                            contentDescription = "Stok tipis",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(rupiah.format(product.sellPrice), style = MaterialTheme.typography.bodyMedium, color = accent, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                if (product.hasVariants) {
                    Text("Pilih varian", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                } else {
                    val stockColor = if (isLowStock) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    Text("Stok: ${product.stock} ${product.unit}", style = MaterialTheme.typography.bodySmall, color = stockColor)
                }
            }
        }
    }
}

@Composable
private fun CartLineRow(
    name: String,
    price: Double,
    quantity: Int,
    unit: String = "pcs",
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
        Text("$quantity $unit")
        IconButton(onClick = onIncrease) { Icon(Icons.Default.Add, contentDescription = "Tambah") }
    }
}

@Composable
private fun CartSummary(cart: Cart) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        HorizontalDivider()
        SummaryRow("Subtotal", rupiah.format(cart.subtotal))
        SummaryRow("Diskon", "- " + rupiah.format(cart.transactionDiscount))
        if (cart.taxPercent > 0.0) {
            SummaryRow("Pajak (${formatPercent(cart.taxPercent)}%)", rupiah.format(cart.taxAmount))
        }
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

private fun paymentMethodLabel(method: PaymentMethod): String = when (method) {
    PaymentMethod.CASH -> "Cash"
    PaymentMethod.DEBIT_CREDIT -> "Debit/Kredit"
    PaymentMethod.QRIS -> "QRIS"
    PaymentMethod.MIXED -> "Campuran"
}

/**
 * Bottom sheet pembayaran dengan dukungan split/multi-metode: kasir bisa menambahkan lebih
 * dari satu baris pembayaran (mis. sebagian Cash + sisanya QRIS) sampai totalnya menutupi
 * total belanja, lalu konfirmasi sekali untuk seluruh transaksi.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentModal(
    cart: Cart,
    qrisImagePath: String?,
    qrisRawContent: String?,
    isProcessing: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (List<com.example.posapp.domain.usecase.PaymentSplit>) -> Unit
) {
    val payments = remember { mutableStateListOf<com.example.posapp.domain.usecase.PaymentSplit>() }
    var selectedMethod by remember { mutableStateOf(PaymentMethod.CASH) }
    var amountText by remember { mutableStateOf("") }

    val paidSoFar = payments.sumOf { it.amount }
    val remaining = (cart.total - paidSoFar).coerceAtLeast(0.0)
    val isFullyPaid = paidSoFar >= cart.total

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(16.dp).fillMaxWidth()) {
            Text("Pembayaran", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text("Total belanja: ${rupiah.format(cart.total)}", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))

            if (payments.isNotEmpty()) {
                Column(Modifier.fillMaxWidth()) {
                    payments.forEachIndexed { index, split ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AssistChip(onClick = {}, label = { Text(paymentMethodLabel(split.method)) })
                            Spacer(Modifier.width(8.dp))
                            Text(rupiah.format(split.amount), modifier = Modifier.weight(1f))
                            IconButton(onClick = { payments.removeAt(index) }) {
                                Icon(Icons.Default.Remove, contentDescription = "Hapus pembayaran ini")
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
            }

            if (!isFullyPaid) {
                Text(
                    "Sisa yang harus dibayar: ${rupiah.format(remaining)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))

                SingleChoiceSegmented(
                    options = listOf("Cash" to PaymentMethod.CASH, "Debit/Kredit" to PaymentMethod.DEBIT_CREDIT, "QRIS" to PaymentMethod.QRIS),
                    selected = selectedMethod,
                    onSelect = { selectedMethod = it }
                )

                if (selectedMethod == PaymentMethod.QRIS) {
                    Spacer(Modifier.height(16.dp))
                    val dynamicAmount = remaining.toLong()
                    val dynamicBitmap by produceState<android.graphics.Bitmap?>(initialValue = null, qrisRawContent, dynamicAmount) {
                        value = if (qrisRawContent != null && dynamicAmount > 0) {
                            withContext(Dispatchers.Default) {
                                runCatching { com.example.posapp.data.qris.QrisUtil.generateDynamicQrisBitmap(qrisRawContent, dynamicAmount) }.getOrNull()
                            }
                        } else null
                    }
                    when {
                        dynamicBitmap != null -> {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Image(
                                        bitmap = dynamicBitmap!!.asImageBitmap(),
                                        contentDescription = "Kode QRIS dinamis",
                                        modifier = Modifier.size(200.dp)
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "Nominal ${rupiah.format(dynamicAmount.toDouble())} sudah otomatis terisi di QR ini",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        qrisImagePath != null -> {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    AsyncImage(
                                        model = qrisImagePath,
                                        contentDescription = "Kode QRIS",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.size(200.dp)
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "Konfirmasi nominal ${rupiah.format(dynamicAmount.toDouble())} secara manual ke pelanggan",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        else -> {
                            Text(
                                "Gambar QRIS belum diunggah. Tambahkan lewat Pengaturan > Profil Toko.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it.filter { c -> c.isDigit() } },
                        label = { Text("Jumlah") },
                        placeholder = { Text(rupiah.format(remaining)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { amountText = remaining.toLong().toString() }) {
                        Text("Pas")
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        val amount = amountText.toDoubleOrNull()?.takeIf { it > 0 } ?: remaining
                        payments.add(com.example.posapp.domain.usecase.PaymentSplit(selectedMethod, amount))
                        amountText = ""
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Tambah Metode Pembayaran")
                }
            } else {
                val change = paidSoFar - cart.total
                if (change > 0) {
                    Text(
                        "Kembalian: ${rupiah.format(change)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text("Pembayaran pas, tidak ada kembalian.", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = { payments.clear() }) { Text("Ubah rincian pembayaran") }
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onConfirm(payments.toList()) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                enabled = !isProcessing && isFullyPaid
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
