package com.example.posapp.presentation.report

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.posapp.data.local.entity.TransactionEntity
import com.example.posapp.data.local.entity.TransactionItemEntity
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

private val rupiah: NumberFormat = NumberFormat.getCurrencyInstance(Locale("in", "ID"))
private val dateTimeFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("in", "ID"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    viewModel: ReportViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val detailState by viewModel.detailState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ReportEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Laporan Penjualan") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PresetChip("Hari Ini", uiState.preset == ReportRangePreset.TODAY) { viewModel.selectPreset(ReportRangePreset.TODAY) }
                PresetChip("Minggu Ini", uiState.preset == ReportRangePreset.THIS_WEEK) { viewModel.selectPreset(ReportRangePreset.THIS_WEEK) }
                PresetChip("Bulan Ini", uiState.preset == ReportRangePreset.THIS_MONTH) { viewModel.selectPreset(ReportRangePreset.THIS_MONTH) }
            }

            Spacer(Modifier.height(16.dp))

            if (uiState.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryCard(
                    modifier = Modifier.weight(1f),
                    label = "Total Omzet",
                    value = rupiah.format(uiState.summary.totalRevenue),
                    accent = MaterialTheme.colorScheme.primary
                )
                SummaryCard(
                    modifier = Modifier.weight(1f),
                    label = "Laba Kotor",
                    value = rupiah.format(uiState.summary.totalGrossProfit),
                    accent = MaterialTheme.colorScheme.tertiary
                )
            }
            Spacer(Modifier.height(8.dp))
            SummaryCard(
                modifier = Modifier.fillMaxWidth(),
                label = "Jumlah Transaksi",
                value = "${uiState.summary.totalTransactions}",
                accent = MaterialTheme.colorScheme.secondary
            )

            Spacer(Modifier.height(24.dp))
            Text("Produk Terlaris", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            if (uiState.topItems.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    Text("Belum ada penjualan pada periode ini", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Column {
                    uiState.topItems.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(item.productName, fontWeight = FontWeight.Medium)
                                Text("Terjual: ${item.totalQty}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(rupiah.format(item.totalRevenue))
                        }
                        HorizontalDivider()
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Riwayat Penjualan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                if (!uiState.isAdmin) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = "Hanya lihat — hanya Admin yang bisa mengoreksi",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (uiState.isAdmin) "Tap transaksi untuk mengoreksi kesalahan input kasir"
                else "Tap transaksi untuk melihat detail (hanya Admin yang bisa mengedit)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            if (uiState.transactions.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    Text("Belum ada transaksi pada periode ini", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(uiState.transactions, key = { it.id }) { tx ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.openTransactionDetail(tx.id) }
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(tx.invoiceNumber, fontWeight = FontWeight.Medium)
                                Text(
                                    dateTimeFormat.format(java.util.Date(tx.createdAt)) +
                                        (tx.cashierName?.let { " · $it" } ?: "") +
                                        (if (tx.editedByName != null) " · Diedit ${tx.editedByName}" else ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(rupiah.format(tx.total), fontWeight = FontWeight.Medium)
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    detailState?.let { detail ->
        TransactionDetailDialog(
            detail = detail,
            isAdmin = uiState.isAdmin,
            onDismiss = { viewModel.closeTransactionDetail() },
            onSave = { updatedTransaction, updatedItems, deletedItemIds ->
                viewModel.saveTransactionCorrection(updatedTransaction, updatedItems, deletedItemIds)
            }
        )
    }
}

@Composable
private fun PresetChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

/** State baris item yang bisa diedit di dalam dialog koreksi transaksi (hanya untuk Admin). */
private class EditableItemState(
    val itemId: Long,
    val label: String,
    initialQuantity: Int,
    initialPrice: Double,
    initialDiscount: Double
) {
    var quantity by mutableStateOf(initialQuantity.toString())
    var price by mutableStateOf(initialPrice.toString())
    var discount by mutableStateOf(initialDiscount.toString())
    var deleted by mutableStateOf(false)

    val lineTotal: Double
        get() = (price.toDoubleOrNull() ?: 0.0) * (quantity.toIntOrNull() ?: 0) - (discount.toDoubleOrNull() ?: 0.0)
}

/**
 * Dialog detail transaksi dari Riwayat Penjualan. Kasir hanya melihat (read-only); Admin bisa
 * mengoreksi quantity/harga/diskon per item, menghapus item yang salah input, dan mengubah
 * catatan — lalu total transaksi dihitung ulang otomatis mengikuti rumus checkout (Cart.kt):
 * subtotal -> dikurangi diskon transaksi -> ditambah pajak.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionDetailDialog(
    detail: TransactionDetailUiState,
    isAdmin: Boolean,
    onDismiss: () -> Unit,
    onSave: (TransactionEntity, List<TransactionItemEntity>, List<Long>) -> Unit
) {
    val transaction = detail.transaction
    var note by remember(transaction.id) { mutableStateOf(transaction.note.orEmpty()) }
    val editableItems = remember(transaction.id) {
        detail.items.map { item ->
            EditableItemState(
                itemId = item.id,
                label = item.productNameSnapshot + (item.variantLabelSnapshot?.let { " ($it)" } ?: ""),
                initialQuantity = item.quantity,
                initialPrice = item.priceSnapshot,
                initialDiscount = item.itemDiscount
            )
        }.toMutableStateList()
    }

    val subtotal = editableItems.filter { !it.deleted }.sumOf { it.lineTotal }
    val taxAmount = ((subtotal - transaction.discountAmount).coerceAtLeast(0.0)) * (transaction.taxPercent / 100.0)
    val total = (subtotal - transaction.discountAmount + taxAmount).coerceAtLeast(0.0)
    val remainingCount = editableItems.count { !it.deleted }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(
                Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(transaction.invoiceNumber, style = MaterialTheme.typography.titleLarge)
                Text(
                    dateTimeFormat.format(java.util.Date(transaction.createdAt)) +
                        (transaction.cashierName?.let { " · Kasir: $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (transaction.editedByName != null && transaction.editedAt != null) {
                    Text(
                        "Dikoreksi oleh ${transaction.editedByName} · ${dateTimeFormat.format(java.util.Date(transaction.editedAt))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(12.dp))

                editableItems.forEach { item ->
                    if (!item.deleted) {
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(item.label, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                if (isAdmin) {
                                    IconButton(onClick = { item.deleted = true }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Hapus item")
                                    }
                                }
                            }
                            if (isAdmin) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = item.quantity,
                                        onValueChange = { item.quantity = it.filter { c -> c.isDigit() } },
                                        label = { Text("Qty") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = item.price,
                                        onValueChange = { item.price = it.filter { c -> c.isDigit() || c == '.' } },
                                        label = { Text("Harga Satuan") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = item.discount,
                                        onValueChange = { item.discount = it.filter { c -> c.isDigit() || c == '.' } },
                                        label = { Text("Diskon") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                }
                            } else {
                                Text(
                                    "${item.quantity} x ${rupiah.format(item.price.toDoubleOrNull() ?: 0.0)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                }

                Spacer(Modifier.height(12.dp))
                if (isAdmin) {
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Catatan") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                } else if (transaction.note != null) {
                    Text("Catatan: ${transaction.note}", style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total", fontWeight = FontWeight.Bold)
                    Text(rupiah.format(total), fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(if (isAdmin) "Batal" else "Tutup") }
                    if (isAdmin) {
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val updatedItems = detail.items.mapNotNull { original ->
                                    val edited = editableItems.find { it.itemId == original.id } ?: return@mapNotNull null
                                    if (edited.deleted) null
                                    else original.copy(
                                        quantity = (edited.quantity.toIntOrNull() ?: 1).coerceAtLeast(1),
                                        priceSnapshot = edited.price.toDoubleOrNull() ?: original.priceSnapshot,
                                        itemDiscount = edited.discount.toDoubleOrNull() ?: 0.0
                                    )
                                }
                                val deletedIds = editableItems.filter { it.deleted }.map { it.itemId }
                                val updatedTransaction = transaction.copy(
                                    subtotal = subtotal,
                                    taxAmount = taxAmount,
                                    total = total,
                                    note = note.ifBlank { null }
                                )
                                onSave(updatedTransaction, updatedItems, deletedIds)
                            },
                            enabled = !detail.isSaving && remainingCount > 0
                        ) {
                            Text(if (detail.isSaving) "Menyimpan..." else "Simpan Koreksi")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(modifier: Modifier = Modifier, label: String, value: String, accent: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary) {
    Card(modifier = modifier, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(16.dp)) {
            Box(
                Modifier
                    .width(28.dp)
                    .height(3.dp)
                    .background(accent, shape = MaterialTheme.shapes.extraSmall)
            )
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}
