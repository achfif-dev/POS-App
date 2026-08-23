package com.example.posapp.presentation.product

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Style
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
import com.example.posapp.data.local.entity.ProductVariantEntity
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
    var variantManagerProductId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ProductEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                is ProductEvent.SavedSuccessfully -> {
                    val wasNewWithVariants = formState?.productId == null && formState?.hasVariants == true
                    formState = null
                    snackbarHostState.showSnackbar("Produk tersimpan")
                    if (wasNewWithVariants) variantManagerProductId = event.productId
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
                singleLine = true,
                shape = MaterialTheme.shapes.large
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
                                    variantName = product.variantName.orEmpty(),
                                    hasVariants = product.hasVariants
                                )
                            },
                            onManageVariants = { variantManagerProductId = product.id },
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

    variantManagerProductId?.let { productId ->
        val product = uiState.products.find { it.id == productId }
        VariantManagerDialog(
            productId = productId,
            productName = product?.name ?: "",
            viewModel = viewModel,
            onDismiss = { variantManagerProductId = null }
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
    onManageVariants: () -> Unit,
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
                if (product.hasVariants) {
                    Text("Punya varian", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                } else {
                    val stockColor = if (product.stock <= product.lowStockThreshold)
                        MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    Text("Stok: ${product.stock}", style = MaterialTheme.typography.bodyMedium, color = stockColor)
                }
            }
        }
        if (product.hasVariants) {
            IconButton(onClick = onManageVariants) { Icon(Icons.Default.Style, contentDescription = "Kelola Varian") }
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
                    .verticalScroll(rememberScrollState())
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

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Produk Punya Varian", fontWeight = FontWeight.Medium)
                        Text(
                            "Stok dikelola per kombinasi (mis. Ukuran x Warna)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.hasVariants,
                        onCheckedChange = { state = state.copy(hasVariants = it) }
                    )
                }

                if (!state.hasVariants) {
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
                } else {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (form.productId == null) "Anda bisa menambahkan kombinasi varian setelah produk disimpan."
                        else "Kelola stok tiap kombinasi lewat tombol ikon varian di daftar produk.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        label = { Text("Label (opsional)") },
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

/** Dialog kelola varian (matrix Ukuran x Warna) untuk satu produk: tambah/edit/hapus kombinasi. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VariantManagerDialog(
    productId: Long,
    productName: String,
    viewModel: ProductViewModel,
    onDismiss: () -> Unit
) {
    val variants by viewModel.observeVariants(productId).collectAsState(initial = emptyList())
    var editingVariant by remember { mutableStateOf<ProductVariantEntity?>(null) }
    var showAddForm by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(20.dp).fillMaxWidth().heightIn(max = 500.dp)) {
                Text("Varian: $productName", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Setiap kombinasi punya SKU & stok sendiri",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                if (variants.isEmpty() && !showAddForm) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                        Text("Belum ada varian", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                        items(variants, key = { it.id }) { variant ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(variant.variantLabel, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "SKU: ${variant.sku} · Stok: ${variant.stock}" +
                                            (variant.priceOverride?.let { " · ${rupiah.format(it)}" } ?: ""),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { editingVariant = variant }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit varian")
                                }
                                IconButton(onClick = { viewModel.deleteVariant(variant.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Hapus varian")
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                if (showAddForm) {
                    VariantForm(
                        initial = null,
                        onCancel = { showAddForm = false },
                        onSave = { newVariant ->
                            viewModel.saveVariant(productId, newVariant)
                            showAddForm = false
                        }
                    )
                } else {
                    OutlinedButton(onClick = { showAddForm = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Tambah Kombinasi Varian")
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Tutup") }
                }
            }
        }
    }

    editingVariant?.let { variant ->
        Dialog(onDismissRequest = { editingVariant = null }) {
            Surface(shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(20.dp).fillMaxWidth()) {
                    Text("Edit Varian", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    VariantForm(
                        initial = variant,
                        onCancel = { editingVariant = null },
                        onSave = { updated ->
                            viewModel.saveVariant(productId, updated)
                            editingVariant = null
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun VariantForm(
    initial: ProductVariantEntity?,
    onCancel: () -> Unit,
    onSave: (ProductVariantEntity) -> Unit
) {
    var label by remember { mutableStateOf(initial?.variantLabel ?: "") }
    var sku by remember { mutableStateOf(initial?.sku ?: "") }
    var stock by remember { mutableStateOf(initial?.stock?.toString() ?: "") }
    var priceOverride by remember { mutableStateOf(initial?.priceOverride?.toString() ?: "") }

    Column {
        OutlinedTextField(
            value = label, onValueChange = { label = it },
            label = { Text("Label (mis. Merah / L)") }, modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = sku, onValueChange = { sku = it },
            label = { Text("SKU / Barcode") }, modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        Row {
            OutlinedTextField(
                value = stock,
                onValueChange = { stock = it.filter { c -> c.isDigit() } },
                label = { Text("Stok") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = priceOverride,
                onValueChange = { priceOverride = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Harga Khusus (opsional)") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text("Batal") }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    onSave(
                        ProductVariantEntity(
                            id = initial?.id ?: 0L,
                            productId = initial?.productId ?: 0L,
                            variantLabel = label.trim(),
                            sku = sku.trim(),
                            stock = stock.toIntOrNull() ?: 0,
                            priceOverride = priceOverride.toDoubleOrNull()
                        )
                    )
                },
                enabled = label.isNotBlank() && sku.isNotBlank()
            ) { Text("Simpan") }
        }
    }
}
