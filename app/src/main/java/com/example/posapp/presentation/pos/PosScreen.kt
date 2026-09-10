package com.example.posapp.presentation.pos

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.posapp.data.local.entity.PaymentMethod
import com.example.posapp.data.local.entity.CustomerEntity
import com.example.posapp.data.local.entity.ProductEntity
import com.example.posapp.data.local.entity.ProductVariantEntity
import com.example.posapp.data.local.entity.TransactionEntity
import com.example.posapp.data.local.entity.TransactionItemEntity
import com.example.posapp.data.settings.quickCashAmountList
import com.example.posapp.domain.model.Cart
import com.example.posapp.presentation.theme.CheckoutSuccessOverlay
import com.example.posapp.presentation.theme.CountUpText
import com.example.posapp.presentation.theme.PosBrandedTopBar
import com.example.posapp.presentation.theme.StoreLogo
import com.example.posapp.presentation.theme.accentColorFor
import com.example.posapp.presentation.theme.iconForCategory
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
    onScannedSkuConsumed: () -> Unit = {},
    // Fitur prioritas: QRIS Otomatis (Midtrans) dikunci kalau lisensi belum aktivasi & masa coba
    // 15 hari sudah habis — lihat LicenseState.hasPremiumAccess & PremiumFeatureGate di
    // MainActivity.kt. Default true supaya caller lama/tes yang belum mengisi parameter ini
    // tidak ikut terkunci tanpa sengaja. QRIS statis manual (upload gambar di Profil Toko) TIDAK
    // pernah terkunci, tetap jalan sebagai cadangan seperti biasa.
    hasPremiumAccess: Boolean = false, // fail-closed: default aman kalau ada pemanggil baru lupa passing nilai asli
    onOpenLicenseActivation: () -> Unit = {},
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val autoLockManager = com.example.posapp.data.auth.LocalAutoLockManager.current
    val uiState by viewModel.uiState.collectAsState()
    val lastReceipt by viewModel.lastReceipt.collectAsState()
    var showPaymentSheet by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var variantPickerProduct by remember { mutableStateOf<ProductEntity?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    // Overlay centang animasi setelah checkout sukses (menggantikan Snackbar teks polos yang
    // gampang terlewat) — lihat CheckoutSuccessOverlay di presentation/theme/Micro.kt.
    var checkoutSuccess by remember { mutableStateOf<PosEvent.CheckoutSuccess?>(null) }

    val bluetoothPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.printReceipt() }

    fun requestPrint() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.BLUETOOTH_CONNECT
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (granted) {
                viewModel.printReceipt()
            } else {
                autoLockManager.expectExternalActivityReturn()
                bluetoothPermissionLauncher.launch(android.Manifest.permission.BLUETOOTH_CONNECT)
            }
        } else {
            viewModel.printReceipt()
        }
    }

    LaunchedEffect(scannedSku) {
        if (scannedSku != null) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    checkoutSuccess = event
                }
                is PosEvent.PdfReady -> {
                    autoLockManager.expectExternalActivityReturn()
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
                    // Disembunyikan dari Kasir — lihat Permission.canManageProducts. Rute
                    // "products" tetap digerbang independen di MainActivity sebagai lapis kedua
                    // (audit 2026-09-06).
                    if (uiState.canManageProducts) {
                        TextButton(onClick = onOpenProducts) {
                            Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Produk")
                        }
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
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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

                if (uiState.storeProfile.tableTaggingEnabled) {
                    var tableTagInput by remember(uiState.cart.tableTag) { mutableStateOf(uiState.cart.tableTag ?: "") }
                    OutlinedTextField(
                        value = tableTagInput,
                        onValueChange = {
                            tableTagInput = it
                            viewModel.updateTableTag(it)
                        },
                        label = { Text("No. Meja / Nama Pemesan") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                }

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
                    Text("Bayar (")
                    CountUpText(
                        value = uiState.cart.total,
                        format = { rupiah.format(it) },
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(")")
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
        val customers by viewModel.customers.collectAsState()
        PaymentModal(
            cart = uiState.cart,
            qrisImagePath = uiState.storeProfile.qrisImagePath,
            qrisRawContent = uiState.storeProfile.qrisRawContent,
            quickCashAmounts = uiState.storeProfile.quickCashAmountList(),
            customers = customers,
            loyaltyEnabled = uiState.storeProfile.loyaltyEnabled,
            loyaltyPointValueRupiah = uiState.storeProfile.loyaltyPointValueRupiah,
            isProcessing = uiState.isProcessing,
            onApplyLoyaltyRedemption = viewModel::applyLoyaltyRedemption,
            onClearLoyaltyRedemption = viewModel::clearLoyaltyRedemption,
            hasPremiumAccess = hasPremiumAccess,
            onOpenLicenseActivation = onOpenLicenseActivation,
            onDismiss = { showPaymentSheet = false },
            onConfirm = { payments, customerId -> viewModel.checkout(payments, customerId) }
        )
    }

    lastReceipt?.let { (transaction, items) ->
        ReceiptDialog(
            transaction = transaction,
            items = items,
            whatsappReceiptEnabled = uiState.storeProfile.whatsappReceiptEnabled,
            storeProfile = uiState.storeProfile,
            onDismiss = { viewModel.dismissReceipt() },
            onPrint = { requestPrint() },
            onExportPdf = { viewModel.exportReceiptPdf() }
        )
    }

    // Ditaruh paling akhir (dirender paling atas — NavHost membungkus tiap rute dalam Box sejak
    // navigation-compose 2.4+, jadi urutan emisi = urutan layer) supaya menutupi seluruh layar
    // Kasir sesaat, lalu hilang sendiri. Lihat CheckoutSuccessOverlay di theme/Micro.kt.
    //
    // "Ingat nilai terakhir" dipakai di sini (bukan langsung `checkoutSuccess?.let { ... }`)
    // supaya animasi fade-out AnimatedVisibility sempat memainkan — kalau composable-nya
    // langsung hilang begitu checkoutSuccess di-null-kan, animasi keluarnya tidak pernah
    // terlihat sama sekali.
    val retainedCheckoutSuccess = remember { mutableStateOf<PosEvent.CheckoutSuccess?>(null) }
    LaunchedEffect(checkoutSuccess) {
        if (checkoutSuccess != null) retainedCheckoutSuccess.value = checkoutSuccess
    }
    retainedCheckoutSuccess.value?.let { success ->
        CheckoutSuccessOverlay(
            visible = checkoutSuccess != null,
            invoiceNumber = success.invoiceNumber,
            subtitle = if (success.change > 0) "Kembalian: ${rupiah.format(success.change)}" else "Lunas",
            onDismissRequest = { checkoutSuccess = null },
        )
    }
}

/** Susun teks struk untuk dikirim via WhatsApp (v13) — dibuat plain text (bukan gambar/PDF)
 * supaya ringan dan langsung terbaca di chat WA tanpa perlu buka lampiran. */
private fun buildWhatsappReceiptText(
    transaction: TransactionEntity,
    items: List<TransactionItemEntity>,
    storeProfile: com.example.posapp.data.settings.StoreProfile
): String {
    val dateFormat = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
    val sb = StringBuilder()
    sb.appendLine("*${storeProfile.name}*")
    if (storeProfile.address.isNotBlank()) sb.appendLine(storeProfile.address)
    sb.appendLine("No. Invoice: ${transaction.invoiceNumber}")
    transaction.note?.takeIf { it.isNotBlank() }?.let { sb.appendLine("Meja/Pesanan: $it") }
    sb.appendLine(dateFormat.format(java.util.Date(transaction.createdAt)))
    sb.appendLine("------------------------------")
    items.forEach { item ->
        sb.appendLine("${item.productNameSnapshot} x${item.quantity}")
        sb.appendLine("  ${rupiah.format(item.priceSnapshot * item.quantity - item.itemDiscount)}")
    }
    sb.appendLine("------------------------------")
    sb.appendLine("Subtotal: ${rupiah.format(transaction.subtotal)}")
    if (transaction.discountAmount > 0) sb.appendLine("Diskon: -${rupiah.format(transaction.discountAmount)}")
    if (transaction.taxAmount > 0) sb.appendLine("Pajak: ${rupiah.format(transaction.taxAmount)}")
    sb.appendLine("*Total: ${rupiah.format(transaction.total)}*")
    sb.appendLine("Kembalian: ${rupiah.format(transaction.changeAmount)}")
    sb.appendLine()
    sb.appendLine(storeProfile.receiptFooter)
    return sb.toString()
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
    whatsappReceiptEnabled: Boolean = false,
    storeProfile: com.example.posapp.data.settings.StoreProfile? = null,
    onDismiss: () -> Unit,
    onPrint: () -> Unit,
    onExportPdf: () -> Unit
) {
    val context = LocalContext.current
    val autoLockManager = com.example.posapp.data.auth.LocalAutoLockManager.current
    var showWhatsappInput by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Transaksi Berhasil") },
        text = {
            Column {
                Text("No. Invoice: ${transaction.invoiceNumber}")
                transaction.note?.takeIf { it.isNotBlank() }?.let {
                    Text("Meja/Pesanan: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
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
                if (whatsappReceiptEnabled && storeProfile != null) {
                    TextButton(onClick = { showWhatsappInput = true }) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("WA")
                    }
                }
                TextButton(onClick = onExportPdf) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("PDF")
                }
                TextButton(onClick = onDismiss) { Text("Tutup") }
            }
        }
    )

    if (showWhatsappInput && storeProfile != null) {
        var phoneInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showWhatsappInput = false },
            title = { Text("Kirim Struk via WhatsApp") },
            text = {
                OutlinedTextField(
                    value = phoneInput,
                    onValueChange = { phoneInput = it.filter { c -> c.isDigit() || c == '+' } },
                    label = { Text("No. WhatsApp pelanggan") },
                    placeholder = { Text("mis. 628123456789") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // Normalisasi kasar: buang "0" di depan -> ganti "62" (kode negara ID),
                        // dan buang karakter "+" (wa.me tidak pakai tanda plus di path-nya).
                        val normalized = phoneInput.trim().removePrefix("+").let {
                            if (it.startsWith("0")) "62" + it.removePrefix("0") else it
                        }
                        val text = java.net.URLEncoder.encode(
                            buildWhatsappReceiptText(transaction, items, storeProfile), "UTF-8"
                        )
                        autoLockManager.expectExternalActivityReturn()
                        context.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://wa.me/$normalized?text=$text")
                            )
                        )
                        showWhatsappInput = false
                    },
                    enabled = phoneInput.trim().length >= 8
                ) { Text("Kirim") }
            },
            dismissButton = {
                TextButton(onClick = { showWhatsappInput = false }) { Text("Batal") }
            }
        )
    }
}

// Foto produk dijadikan DOMINAN di bagian atas kartu (gaya e-commerce: Shopee/Tokopedia),
// bukan avatar kecil di samping teks — jauh lebih cepat dikenali mata sekilas saat kasir
// men-scroll grid berisi banyak SKU, terutama untuk toko F&B/retail dengan variasi visual
// produk yang tinggi. Fallback tanpa foto: latar warna aksen + ikon kategori besar (bukan
// AsyncImage kosong), supaya tetap terasa "dirancang" walau produk belum difoto.
@Composable
private fun ProductCard(product: ProductEntity, categoryName: String? = null, onClick: () -> Unit) {
    val accent = accentColorFor(product.name)
    val isLowStock = !product.hasVariants && product.stock <= product.lowStockThreshold

    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.2f)
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                if (product.photoPath != null) {
                    AsyncImage(
                        model = product.photoPath,
                        contentDescription = product.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        iconForCategory(categoryName),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(40.dp)
                    )
                }
                if (isLowStock) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.WarningAmber,
                                contentDescription = "Stok tipis",
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
            Column(Modifier.padding(10.dp)) {
                Text(product.name, fontWeight = FontWeight.SemiBold, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Text(rupiah.format(product.sellPrice), style = MaterialTheme.typography.bodyMedium, color = accent, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                if (product.hasVariants) {
                    Text("Pilih varian", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                } else {
                    val stockColor = if (isLowStock) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    Text("Stok: ${product.stock} ${product.unit}", style = MaterialTheme.typography.bodySmall, color = stockColor, maxLines = 1)
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
        if (cart.loyaltyDiscount > 0) {
            SummaryRow("Poin Loyalitas (${cart.loyaltyPointsRedeemed} poin)", "- " + rupiah.format(cart.loyaltyDiscount))
        }
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
    PaymentMethod.BON -> "Bon"
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
    quickCashAmounts: List<Long> = emptyList(),
    customers: List<CustomerEntity> = emptyList(),
    loyaltyEnabled: Boolean = false,
    loyaltyPointValueRupiah: Long = 100,
    isProcessing: Boolean,
    onApplyLoyaltyRedemption: (CustomerEntity, Long) -> Unit = { _, _ -> },
    onClearLoyaltyRedemption: () -> Unit = {},
    // Fitur prioritas QRIS Otomatis (Midtrans) dikunci kalau lisensi belum aktivasi & masa coba
    // habis — lihat parameter sama di PosScreen di atas. Default true supaya caller lama/tes
    // yang belum mengisi parameter ini tidak ikut terkunci tanpa sengaja.
    hasPremiumAccess: Boolean = false, // fail-closed: default aman kalau ada pemanggil baru lupa passing nilai asli
    onOpenLicenseActivation: () -> Unit = {},
    onDismiss: () -> Unit,
    onConfirm: (List<com.example.posapp.domain.usecase.PaymentSplit>, customerId: Long?) -> Unit
) {
    val payments = remember { mutableStateListOf<com.example.posapp.domain.usecase.PaymentSplit>() }
    var selectedMethod by remember { mutableStateOf(PaymentMethod.CASH) }
    var amountText by remember { mutableStateOf("") }
    var selectedCustomer by remember { mutableStateOf<CustomerEntity?>(null) }
    var showCustomerPicker by remember { mutableStateOf(false) }
    var loyaltyPointsInput by remember { mutableStateOf("") }

    // BUG PENTING: `selectedCustomer` di atas ada di state LOKAL dialog ini — reset ke null
    // setiap dialog ditutup lalu dibuka lagi. Tapi cart.loyaltyPointsRedeemed/loyaltyDiscount
    // ada di ViewModel (Cart), jadi BERTAHAN lintas sesi dialog. Tanpa reset ini, kasir bisa
    // menerapkan penukaran poin untuk pelanggan A, menutup dialog TANPA checkout, membuka lagi,
    // lalu checkout tanpa memilih pelanggan sama sekali (customerId = null) — potongan Rupiah
    // tetap dipakai di nota tapi TIDAK ADA poin yang benar-benar terpotong dari siapa pun
    // (kebocoran diskon), atau dipotong dari pelanggan yang salah kalau pelanggan lain dipilih.
    // Setiap dialog pembayaran dibuka segar, redemption WAJIB diterapkan ulang secara sengaja.
    LaunchedEffect(Unit) {
        if (cart.loyaltyPointsRedeemed > 0) {
            onClearLoyaltyRedemption()
        }
    }

    val paidSoFar = payments.sumOf { it.amount }
    val remaining = (cart.total - paidSoFar).coerceAtLeast(0.0)
    val isFullyPaid = paidSoFar >= cart.total
    val hasBonPayment = payments.any { it.method == PaymentMethod.BON }
    val canConfirm = isFullyPaid && (!hasBonPayment || selectedCustomer != null)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(16.dp).fillMaxWidth()) {
            Text("Pembayaran", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            if (cart.loyaltyDiscount > 0) {
                Text(
                    "Subtotal: ${rupiah.format(cart.subtotal)}  ·  Potongan poin: -${rupiah.format(cart.loyaltyDiscount)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text("Total belanja: ${rupiah.format(cart.total)}", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))

            if (loyaltyEnabled) {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { showCustomerPicker = true },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Redeem, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                selectedCustomer?.let { "${it.name} · Saldo ${it.loyaltyPoints} poin" }
                                    ?: "Pilih pelanggan untuk pakai/kumpulkan poin (opsional)",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        selectedCustomer?.let { customer ->
                            if (customer.loyaltyPoints > 0) {
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = loyaltyPointsInput,
                                        onValueChange = { loyaltyPointsInput = it.filter { c -> c.isDigit() } },
                                        label = { Text("Tukar poin (maks ${customer.loyaltyPoints})") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Button(onClick = {
                                        onApplyLoyaltyRedemption(customer, loyaltyPointsInput.toLongOrNull() ?: 0L)
                                    }) { Text("Pakai") }
                                }
                                if (cart.loyaltyPointsRedeemed > 0) {
                                    Spacer(Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "${cart.loyaltyPointsRedeemed} poin dipakai (-${rupiah.format(cart.loyaltyDiscount)})",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        TextButton(onClick = {
                                            loyaltyPointsInput = ""
                                            onClearLoyaltyRedemption()
                                        }) { Text("Batalkan") }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

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
                    options = listOf(
                        "Cash" to PaymentMethod.CASH,
                        "Debit/Kredit" to PaymentMethod.DEBIT_CREDIT,
                        "QRIS" to PaymentMethod.QRIS,
                        "Bon" to PaymentMethod.BON
                    ),
                    selected = selectedMethod,
                    onSelect = { selectedMethod = it }
                )

                if (selectedMethod == PaymentMethod.BON) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedCard(onClick = { showCustomerPicker = true }, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                selectedCustomer?.name ?: "Pilih pelanggan (wajib untuk Bon)",
                                modifier = Modifier.weight(1f),
                                color = if (selectedCustomer == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

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

                    // QRIS Otomatis (Midtrans) — opsional, hanya muncul kalau toko sudah
                    // menghubungkan payment gateway sendiri di Pengaturan > Payment Gateway.
                    // Statis di atas TETAP jalan sebagai cadangan kalau internet mati.
                    val qrisAutoViewModel: com.example.posapp.presentation.pos.QrisAutoPaymentViewModel = hiltViewModel()
                    val gatewayConfigured by qrisAutoViewModel.isConfigured.collectAsState()
                    val autoState by qrisAutoViewModel.uiState.collectAsState()
                    val autoOrderId = remember { "TEMP-${System.currentTimeMillis()}" }

                    // BUG PENTING (ditemukan saat audit ulang): qrisAutoViewModel didapat lewat
                    // hiltViewModel() yang di-scope ke NavBackStackEntry rute "pos" — instance-nya
                    // BERTAHAN selama kasir tidak pindah layar, bukan dibuat baru setiap dialog
                    // pembayaran ini dibuka/ditutup. Tanpa reset eksplisit ini, status "SETTLED"
                    // dari transaksi QRIS SEBELUMNYA bisa "bocor" ke transaksi berikutnya (kasir
                    // buka dialog bayar baru, langsung terlihat tercentang Lunas padahal belum
                    // ada pembayaran sama sekali untuk transaksi ini). LaunchedEffect(Unit) di sini
                    // jalan setiap blok QRIS ini MASUK ke komposisi (dialog baru dibuka, atau
                    // metode bayar baru saja dipindah ke QRIS) — memastikan selalu mulai bersih.
                    LaunchedEffect(Unit) { qrisAutoViewModel.reset() }

                    if (gatewayConfigured && !hasPremiumAccess) {
                        // Gateway sudah dikonfigurasi toko, tapi fitur prioritas ini terkunci
                        // (masa coba habis / lisensi belum aktivasi) — QRIS statis manual di atas
                        // TETAP jalan penuh sebagai cadangan, kasir tinggal konfirmasi manual.
                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "QRIS Otomatis terkunci — aktivasi lisensi untuk verifikasi realtime. " +
                                "QRIS manual di atas tetap bisa dipakai seperti biasa.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = onOpenLicenseActivation) { Text("Aktivasi Lisensi") }
                    } else if (gatewayConfigured) {
                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(12.dp))
                        Text("QRIS Otomatis (Verifikasi Real-time)", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(8.dp))
                        when {
                            autoState.charge == null && !autoState.isCreating -> {
                                Button(
                                    onClick = { qrisAutoViewModel.createCharge(autoOrderId, dynamicAmount) },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Buat QRIS Otomatis ${rupiah.format(dynamicAmount.toDouble())}") }
                            }
                            autoState.isCreating -> {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                            autoState.status == com.example.posapp.data.payment.QrisChargeStatus.SETTLED -> {
                                Text(
                                    "✓ Pembayaran terkonfirmasi otomatis oleh Midtrans",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                // BUG PENTING (ditemukan saat audit ulang): sebelumnya baris di
                                // bawah memakai `dynamicAmount` (dihitung ULANG dari `remaining`
                                // setiap recomposition) alih-alih nominal yang BENAR-BENAR dikirim
                                // ke Midtrans saat charge dibuat (`autoState.charge.amount`, beku
                                // sejak createCharge dipanggil). Kalau kasir sempat menambah/ubah
                                // metode bayar lain SEBELUM pelanggan selesai scan QR, `remaining`
                                // berubah dan `dynamicAmount` ikut berubah — sehingga jumlah yang
                                // otomatis tercatat "Lunas" bisa BEDA dari nominal yang sungguh
                                // dibayar pelanggan lewat QR tersebut (nota bisa salah catat).
                                val settledAmount = autoState.charge?.amount ?: dynamicAmount
                                LaunchedEffect(autoState.status) {
                                    payments.add(com.example.posapp.domain.usecase.PaymentSplit(PaymentMethod.QRIS, settledAmount.toDouble()))
                                }
                            }
                            else -> {
                                autoState.charge?.qrisImageUrl?.let { url ->
                                    AsyncImage(model = url, contentDescription = "QRIS Midtrans", modifier = Modifier.size(200.dp))
                                }
                                Text(
                                    "Menunggu pembayaran... (otomatis terdeteksi, tidak perlu refresh)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        autoState.error?.let { err ->
                            Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                if (selectedMethod == PaymentMethod.CASH && quickCashAmounts.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Nominal Cepat",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Nominal >= sisa tagihan ditampilkan lebih dulu (paling relevan untuk uang yang
                        // kemungkinan diberikan pelanggan), diikuti nominal lain agar tetap bisa dipilih
                        // untuk pembayaran sebagian (split).
                        quickCashAmounts.sortedBy { it < remaining.toLong() }.forEach { amount ->
                            FilterChip(
                                selected = amountText == amount.toString(),
                                onClick = { amountText = amount.toString() },
                                label = { Text(rupiah.format(amount.toDouble())) }
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
            if (hasBonPayment && selectedCustomer == null) {
                Text(
                    "Pilih pelanggan terlebih dahulu untuk menyelesaikan pembayaran Bon",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(8.dp))
            }
            Button(
                onClick = { onConfirm(payments.toList(), selectedCustomer?.id) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                enabled = !isProcessing && canConfirm
            ) {
                Text(if (isProcessing) "Memproses..." else "Konfirmasi Pembayaran")
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showCustomerPicker) {
        CustomerPickerDialog(
            customers = customers,
            onDismiss = { showCustomerPicker = false },
            onSelect = { customer ->
                // BUG: jika kasir sudah menerapkan penukaran poin untuk pelanggan A lalu
                // ganti ke pelanggan B (tanpa "Batalkan" dulu), cart.loyaltyPointsRedeemed
                // tetap berisi angka milik A tapi customerId yang dikirim ke checkout jadi B —
                // poin akan salah dipotong dari saldo B. Reset penukaran setiap ganti pelanggan.
                if (selectedCustomer?.id != customer.id && cart.loyaltyPointsRedeemed > 0) {
                    onClearLoyaltyRedemption()
                    loyaltyPointsInput = ""
                }
                selectedCustomer = customer
                showCustomerPicker = false
            }
        )
    }
}

@Composable
private fun CustomerPickerDialog(
    customers: List<CustomerEntity>,
    onDismiss: () -> Unit,
    onSelect: (CustomerEntity) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pilih Pelanggan") },
        text = {
            if (customers.isEmpty()) {
                Text("Belum ada pelanggan. Tambahkan dulu lewat menu Pelanggan di Dashboard.")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(customers, key = { it.id }) { customer ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(customer) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(customer.name, fontWeight = FontWeight.SemiBold)
                                customer.phone?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
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
