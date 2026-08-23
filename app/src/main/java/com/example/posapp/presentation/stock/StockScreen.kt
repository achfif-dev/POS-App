package com.example.posapp.presentation.stock

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.posapp.data.local.entity.ProductEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockScreen(
    viewModel: StockViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val products by viewModel.products.collectAsState()
    val lowStock by viewModel.lowStockProducts.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var adjustingProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var showLowStockOnly by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is StockEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    val displayedList = if (showLowStockOnly) lowStock else products

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Stok & Inventaris") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            FilterChip(
                selected = showLowStockOnly,
                onClick = { showLowStockOnly = !showLowStockOnly },
                label = { Text("Stok Tipis Saja (${lowStock.size})") }
            )
            Spacer(Modifier.height(12.dp))

            if (displayedList.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Tidak ada produk untuk ditampilkan")
                }
            } else {
                LazyColumn {
                    items(displayedList, key = { it.id }) { product ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(product.name, fontWeight = FontWeight.SemiBold)
                                val color = if (product.stock <= product.lowStockThreshold)
                                    MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                Text("Stok saat ini: ${product.stock}", color = color)
                            }
                            TextButton(onClick = { adjustingProduct = product }) { Text("Sesuaikan") }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    adjustingProduct?.let { product ->
        StockAdjustDialog(
            product = product,
            onDismiss = { adjustingProduct = null },
            onConfirm = { type, qty, reason ->
                viewModel.adjustStock(product.id, type, qty, reason)
                adjustingProduct = null
            }
        )
    }
}

@Composable
private fun StockAdjustDialog(
    product: ProductEntity,
    onDismiss: () -> Unit,
    onConfirm: (type: String, quantity: Int, reason: String?) -> Unit
) {
    var type by remember { mutableStateOf("IN") }
    var quantityText by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(20.dp).fillMaxWidth()) {
                Text("Sesuaikan Stok: ${product.name}", style = MaterialTheme.typography.titleMedium)
                Text("Stok saat ini: ${product.stock}", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = type == "IN", onClick = { type = "IN" }, label = { Text("Stok Masuk") })
                    FilterChip(selected = type == "OUT", onClick = { type = "OUT" }, label = { Text("Stok Keluar") })
                    FilterChip(selected = type == "OPNAME", onClick = { type = "OPNAME" }, label = { Text("Set Opname") })
                }

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it.filter { c -> c.isDigit() } },
                    label = { Text(if (type == "OPNAME") "Jumlah stok hasil hitung fisik" else "Jumlah") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Catatan (opsional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Batal") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        val qty = quantityText.toIntOrNull() ?: 0
                        onConfirm(type, qty, reason.ifBlank { null })
                    }) { Text("Simpan") }
                }
            }
        }
    }
}
