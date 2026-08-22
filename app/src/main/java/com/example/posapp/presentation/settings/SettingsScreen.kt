package com.example.posapp.presentation.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.posapp.data.export.FileShareHelper
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val fileShareHelper = remember { FileShareHelper(context) }
    val snackbarHostState = remember { SnackbarHostState() }
    var showRestorePicker by remember { mutableStateOf(false) }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            // Copy konten yang dipilih user ke file sementara agar bisa dibaca sebagai File biasa.
            val tempFile = File(context.cacheDir, "restore_temp.db")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
            viewModel.restoreFrom(tempFile)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                is SettingsEvent.ExportReady -> {
                    snackbarHostState.showSnackbar("File siap: ${event.file.name}")
                    context.startActivity(
                        android.content.Intent.createChooser(
                            fileShareHelper.createShareIntent(event.file, event.mimeType),
                            "Bagikan file"
                        )
                    )
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Pengaturan") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Kembali") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {

            SettingsSection(title = "Backup & Restore") {
                SettingsActionRow(
                    label = "Backup Database Sekarang",
                    description = "Menyimpan salinan database ke penyimpanan lokal & bisa dibagikan",
                    onClick = { viewModel.backupNow() }
                )
                SettingsActionRow(
                    label = "Restore dari File Backup",
                    description = "Pilih file .db backup untuk mengembalikan data",
                    onClick = { showRestorePicker = true }
                )
            }

            Spacer(Modifier.height(24.dp))

            SettingsSection(title = "Export Laporan & Data") {
                SettingsActionRow(
                    label = "Export Produk (Excel)",
                    description = "Semua produk beserta harga & stok ke .xlsx",
                    onClick = { viewModel.exportProductsExcel() }
                )
                SettingsActionRow(
                    label = "Export Produk (CSV)",
                    description = "Format ringan, kompatibel dengan semua spreadsheet",
                    onClick = { viewModel.exportProductsCsv() }
                )
                SettingsActionRow(
                    label = "Export Riwayat Transaksi (Excel)",
                    description = "Seluruh riwayat transaksi ke .xlsx",
                    onClick = { viewModel.exportTransactionsExcel() }
                )
            }
        }
    }

    if (showRestorePicker) {
        AlertDialog(
            onDismissRequest = { showRestorePicker = false },
            title = { Text("Restore Database") },
            text = { Text("Data saat ini akan digantikan oleh isi file backup. Lanjutkan?") },
            confirmButton = {
                TextButton(onClick = {
                    showRestorePicker = false
                    restoreLauncher.launch("*/*")
                }) { Text("Pilih File") }
            },
            dismissButton = {
                TextButton(onClick = { showRestorePicker = false }) { Text("Batal") }
            }
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(4.dp), content = content)
    }
}

@Composable
private fun SettingsActionRow(label: String, description: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
