package com.example.posapp.presentation.product

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.posapp.data.local.entity.CategoryEntity
import com.example.posapp.data.local.entity.ProductEntity
import java.text.NumberFormat
import java.util.Locale

private val rupiah: NumberFormat = NumberFormat.getCurrencyInstance(Locale("in", "ID"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductScreen(
    viewModel: ProductViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var formState by remember { mutableStateOf<ProductFormState?>(null) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    var productPendingDelete by remember { mutableStateOf<ProductEntity?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ProductEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                ProductEvent.SavedSuccessfully -> {
                    formState = null
                    snackbarHostState.showSnackbar("Produk tersimpan")
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Manajemen Produk") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    TextButton(onClick = { showCategoryDialog = true }) { Text("Kategori") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { formState = ProductFormState() }) {
                Icon(Icons.Default.Add, contentDescription = "Tambah Produk")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(12.dp)) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Cari nama atau SKU...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))

            if (uiState.products.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Belum ada produk. Tap tombol + untuk menambahkan.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn {
                    items(uiState.products, key = { it.id }) { product ->
                        val categoryName = uiState.categories.find { it.id == product.categoryId }?.name
                        ProductRow(
                            product = product,
                            categoryName = categoryName,
                            onEdit = {
                                formState = ProductFormState(
                                    productId = product.id,
                                    name = product.name,
                                    sku = product.sku,
                                    categoryId = product.categoryId,
                                    purchasePrice = product.purchasePrice.toString(),
                                    sellPrice = product.sellPrice.toString(),
                                    stock = product.stock.toString(),
                                    lowStockThreshold = product.lowStockThreshold.toString(),
                                    discountPercent = product.discountPercent.toString(),
                                    variantName = product.variantName.orEmpty()
                                )
                            },
                            onDelete = { productPendingDelete = product }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    formState?.let { form ->
        ProductFormDialog(
            form = form,
            categories = uiState.categories,
            isSaving = uiState.isSaving,
            onDismiss = { formState = null },
            onSave = { viewModel.saveProduct(it) }
        )
    }

    if (showCategoryDialog) {
        CategoryManagerDialog(
            categories = uiState.categories,
            onAdd = viewModel::addCategory,
            onDelete = viewModel::deleteCategory,
            onDismiss = { showCategoryDialog = false }
        )
    }

    productPendingDelete?.let { product ->
        AlertDialog(
            onDismissRequest = { productPendingDelete = null },
            title = { Text("Hapus produk?") },
            text = { Text("\"${product.name}\" akan disembunyikan dari daftar produk aktif.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteProduct(product)
                    productPendingDelete = null
                }) { Text("Hapus") }
            },
            dismissButton = {
                TextButton(onClick = { productPendingDelete = null }) { Text("Batal") }
            }
        )
    }
}

@Composable
private fun ProductRow(
    product: ProductEntity,
    categoryName: String?,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(product.name, fontWeight = FontWeight.SemiBold)
            Text(
                "SKU: ${product.sku}" + (categoryName?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row {
                Text(rupiah.format(product.sellPrice), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(12.dp))
                val stockColor = if (product.stock <= product.lowStockThreshold)
                    MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                Text("Stok: ${product.stock}", style = MaterialTheme.typography.bodyMedium, color = stockColor)
            }
        }
        IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Hapus") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductFormDialog(
    form: ProductFormState,
    categories: List<CategoryEntity>,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (ProductFormState) -> Unit
) {
    var state by remember(form.productId) { mutableStateOf(form) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(
                Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    if (form.productId == null) "Tambah Produk" else "Edit Produk",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = state.name,
                    onValueChange = { state = state.copy(name = it) },
                    label = { Text("Nama Produk") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.sku,
                    onValueChange = { state = state.copy(sku = it) },
                    label = { Text("SKU / Barcode") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))

                ExposedDropdownMenuBox(
                    expanded = categoryMenuExpanded,
                    onExpandedChange = { categoryMenuExpanded = it }
                ) {
                    OutlinedTextField(
                        value = categories.find { it.id == state.categoryId }?.name ?: "Tanpa kategori",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Kategori") },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = categoryMenuExpanded,
                        onDismissRequest = { categoryMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Tanpa kategori") },
                            onClick = { state = state.copy(categoryId = null); categoryMenuExpanded = false }
                        )
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = { state = state.copy(categoryId = category.id); categoryMenuExpanded = false }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row {
                    OutlinedTextField(
                        value = state.purchasePrice,
                        onValueChange = { state = state.copy(purchasePrice = it.filter { c -> c.isDigit() || c == '.' }) },
                        label = { Text("Harga Beli") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = state.sellPrice,
                        onValueChange = { state = state.copy(sellPrice = it.filter { c -> c.isDigit() || c == '.' }) },
                        label = { Text("Harga Jual") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(Modifier.height(8.dp))
                Row {
                    OutlinedTextField(
                        value = state.stock,
                        onValueChange = { state = state.copy(stock = it.filter { c -> c.isDigit() }) },
                        label = { Text("Stok") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = state.lowStockThreshold,
                        onValueChange = { state = state.copy(lowStockThreshold = it.filter { c -> c.isDigit() }) },
                        label = { Text("Alert Stok Tipis") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(Modifier.height(8.dp))
                Row {
                    OutlinedTextField(
                        value = state.discountPercent,
                        onValueChange = { state = state.copy(discountPercent = it.filter { c -> c.isDigit() || c == '.' }) },
                        label = { Text("Diskon Produk (%)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = state.variantName,
                        onValueChange = { state = state.copy(variantName = it) },
                        label = { Text("Varian (opsional)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Batal") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onSave(state) }, enabled = !isSaving) {
                        Text(if (isSaving) "Menyimpan..." else "Simpan")
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryManagerDialog(
    categories: List<CategoryEntity>,
    onAdd: (String) -> Unit,
    onDelete: (CategoryEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var newCategoryName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kelola Kategori") },
        text = {
            Column {
                LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                    items(categories, key = { it.id }) { category ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(category.name, modifier = Modifier.weight(1f))
                            IconButton(onClick = { onDelete(category) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Hapus kategori")
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    label = { Text("Kategori baru") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onAdd(newCategoryName)
                newCategoryName = ""
            }) { Text("Tambah") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Tutup") }
        }
    )
}
