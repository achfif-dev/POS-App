package com.example.posapp.presentation.supplier

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.posapp.data.local.entity.SupplierEntity
import com.example.posapp.presentation.theme.PosBrandedTopBar

/**
 * Kelola daftar pemasok (v13) — dipakai untuk mengelompokkan produk stok tipis saat membuat
 * draf Pesanan Pembelian (lihat StockScreen -> "Buat PO"). Admin-only, digerbang di MainActivity
 * sama seperti rute Pengaturan lain karena mengubah data master yang dipakai lintas produk.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplierScreen(
    viewModel: SupplierViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val suppliers by viewModel.suppliers.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddDialog by remember { mutableStateOf(false) }
    var supplierBeingEdited by remember { mutableStateOf<SupplierEntity?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SupplierEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            PosBrandedTopBar(
                title = { Text("Pemasok") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Tambah Pemasok")
            }
        }
    ) { padding ->
        if (suppliers.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.LocalShipping,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Belum ada pemasok. Tambahkan supaya bisa dikaitkan ke produk dan\ndipakai membuat draf Pesanan Pembelian dari stok tipis.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
                items(suppliers, key = { it.id }) { supplier ->
                    ListItem(
                        headlineContent = { Text(supplier.name, fontWeight = FontWeight.Medium) },
                        supportingContent = {
                            val details = listOfNotNull(supplier.phone, supplier.address).joinToString(" · ")
                            if (details.isNotBlank()) Text(details)
                        },
                        trailingContent = {
                            IconButton(onClick = { supplierBeingEdited = supplier }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit")
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (showAddDialog) {
        SupplierFormDialog(
            initial = null,
            onDismiss = { showAddDialog = false },
            onSave = { name, phone, address ->
                viewModel.addSupplier(name, phone, address)
                showAddDialog = false
            }
        )
    }

    supplierBeingEdited?.let { supplier ->
        SupplierFormDialog(
            initial = supplier,
            onDismiss = { supplierBeingEdited = null },
            onSave = { name, phone, address ->
                viewModel.updateSupplier(supplier, name, phone, address)
                supplierBeingEdited = null
            },
            onSetActive = { active -> viewModel.setActive(supplier, active) }
        )
    }
}

@Composable
private fun SupplierFormDialog(
    initial: SupplierEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, phone: String?, address: String?) -> Unit,
    onSetActive: ((Boolean) -> Unit)? = null
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var phone by remember { mutableStateOf(initial?.phone.orEmpty()) }
    var address by remember { mutableStateOf(initial?.address.orEmpty()) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    if (initial == null) "Tambah Pemasok" else "Edit Pemasok",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama Pemasok") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("No. WhatsApp/Telepon (opsional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Alamat (opsional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (initial != null && onSetActive != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Aktif", modifier = Modifier.weight(1f))
                        Switch(checked = initial.isActive, onCheckedChange = onSetActive)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Batal") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        onSave(name, phone.ifBlank { null }, address.ifBlank { null })
                    }) { Text("Simpan") }
                }
            }
        }
    }
}
