package com.example.posapp.presentation.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.posapp.data.export.FileShareHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.posapp.presentation.theme.PosBrandedTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onOpenStoreProfile: () -> Unit = {},
    onOpenUserManagement: () -> Unit = {},
    onOpenExpenses: () -> Unit = {}
) {
    val context = LocalContext.current
    val fileShareHelper = remember { FileShareHelper(context) }
    val snackbarHostState = remember { SnackbarHostState() }
    val backups by viewModel.backups.collectAsState()
    var showRestorePicker by remember { mutableStateOf(false) }
    var backupPendingRestore by remember { mutableStateOf<File?>(null) }
    var backupPendingDelete by remember { mutableStateOf<File?>(null) }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val tempFile = File(context.cacheDir, "restore_temp.db")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
            viewModel.restoreFrom(tempFile)
        }
    }

    // File yang lagi menunggu dipilihkan lokasi simpan oleh pengguna via SAF (Storage Access
    // Framework). Ini yang menyediakan opsi "simpan ke perangkat", terpisah dari "bagikan".
    var filePendingSave by remember { mutableStateOf<File?>(null) }
    val saveToDeviceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val file = filePendingSave
        if (uri != null && file != null) {
            viewModel.saveExportToUri(file, uri)
        }
        filePendingSave = null
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                is SettingsEvent.ExportReady -> {
                    val result = snackbarHostState.showSnackbar(
                        message = "File siap: ${event.file.name}",
                        actionLabel = "Simpan ke Perangkat"
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        filePendingSave = event.file
                        saveToDeviceLauncher.launch(event.file.name)
                    } else {
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
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            PosBrandedTopBar(
                title = { Text("Pengaturan") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {

            SettingsSection(title = "Toko & Pengguna") {
                SettingsNavRow(
                    icon = Icons.Default.Storefront,
                    label = "Profil Toko",
                    description = "Nama, alamat, QRIS, logo toko & warna aplikasi",
                    onClick = onOpenStoreProfile
                )
                HorizontalDivider()
                SettingsNavRow(
                    icon = Icons.Default.Group,
                    label = "Pengguna & Login PIN",
                    description = "Kelola kasir/admin dan aktifkan login PIN",
                    onClick = onOpenUserManagement
                )
            }

            Spacer(Modifier.height(24.dp))

            SettingsSection(title = "Keuangan") {
                SettingsNavRow(
                    icon = Icons.Default.Receipt,
                    label = "Beban Usaha",
                    description = "Atur Sewa, Gaji, Listrik, dll untuk hitung Laba Bersih (khusus Admin)",
                    onClick = onOpenExpenses
                )
            }

            Spacer(Modifier.height(24.dp))

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

            if (backups.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Riwayat Backup Lokal",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(4.dp)) {
                        backups.forEach { file ->
                            BackupHistoryRow(
                                file = file,
                                onRestore = { backupPendingRestore = file },
                                onShare = {
                                    context.startActivity(
                                        android.content.Intent.createChooser(
                                            fileShareHelper.createShareIntent(file, "application/octet-stream"),
                                            "Bagikan file backup"
                                        )
                                    )
                                },
                                onDelete = { backupPendingDelete = file }
                            )
                        }
                    }
                }
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

            Spacer(Modifier.height(24.dp))
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

    backupPendingRestore?.let { file ->
        AlertDialog(
            onDismissRequest = { backupPendingRestore = null },
            title = { Text("Restore dari backup ini?") },
            text = { Text("Data saat ini akan digantikan oleh isi \"${file.name}\". Aplikasi perlu dibuka ulang setelah restore.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.restoreFrom(file)
                    backupPendingRestore = null
                }) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { backupPendingRestore = null }) { Text("Batal") }
            }
        )
    }

    backupPendingDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { backupPendingDelete = null },
            title = { Text("Hapus backup ini?") },
            text = { Text("File backup \"${file.name}\" akan dihapus dari perangkat. Data aplikasi yang sedang berjalan tidak terpengaruh.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteBackup(file)
                    backupPendingDelete = null
                }) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { backupPendingDelete = null }) { Text("Batal") }
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
private fun SettingsNavRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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

@Composable
private fun BackupHistoryRow(file: File, onRestore: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Backup, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(dateFormat.format(Date(file.lastModified())), style = MaterialTheme.typography.bodyMedium)
            Text(
                "${file.length() / 1024} KB",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // Sebelumnya ikon di sini adalah "RestoreFromTrash" (bergambar tong sampah) tapi
        // dipasang ke aksi RESTORE, bukan hapus — makanya terlihat seperti tombol hapus
        // padahal tidak menghapus apa-apa, dan tidak ada aksi hapus sungguhan sama sekali.
        // Sekarang dipisah jelas: ikon restore & ikon hapus (dengan konfirmasi) masing-masing.
        IconButton(onClick = onRestore) {
            Icon(Icons.Default.SettingsBackupRestore, contentDescription = "Restore dari file ini")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Hapus backup ini", tint = MaterialTheme.colorScheme.error)
        }
        TextButton(onClick = onShare) { Text("Bagikan") }
    }
}
