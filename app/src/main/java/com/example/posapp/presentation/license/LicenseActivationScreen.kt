package com.example.posapp.presentation.license

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.posapp.data.license.LicenseCrypto
import com.example.posapp.data.license.LicenseStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Layar ini adalah rute BIASA (`license_status`, dibuka dari Pengaturan atau dari
 * `PremiumFeatureGate` di MainActivity.kt) — BUKAN gerbang yang mengunci seluruh app. Aplikasi
 * selalu bisa dipakai untuk transaksi harian tanpa harus mampir ke sini dulu. Didesain supaya
 * PEMBELI aplikasi bisa mengaktifkan sendiri hanya dengan kode lisensi dari penjual, tanpa perlu
 * developer login/setting manual ke HP pelanggan.
 *
 * LISENSI INI SEKALI BAYAR, BUKAN LANGGANAN — begitu ACTIVE, tidak ada lagi apa pun untuk
 * "diperbarui" secara berkala (lihat LicenseModels.kt & LicenseRepository.kt). Satu-satunya
 * status pasca-aktivasi selain ACTIVE adalah REVOKED (dinonaktifkan penjual secara eksplisit).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenseActivationScreen(onActivated: () -> Unit) {
    val viewModel: LicenseViewModel = hiltViewModel()
    val licenseState by viewModel.licenseState.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    var keyInput by remember { mutableStateOf("") }

    LaunchedEffect(uiState.justActivated) {
        if (uiState.justActivated) onActivated()
    }

    val needsKeyInput = licenseState.status == LicenseStatus.NOT_ACTIVATED ||
        licenseState.status == LicenseStatus.TRIAL ||
        licenseState.status == LicenseStatus.TRIAL_EXPIRED ||
        licenseState.status == LicenseStatus.REVOKED

    Scaffold(topBar = { TopAppBar(title = { Text("Aktivasi Aplikasi") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.height(56.dp))
            Spacer(Modifier.height(16.dp))

            if (!LicenseCrypto.isConfigured()) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text(
                            "Public key lisensi belum dipasang di LicenseCrypto.kt (masih placeholder). " +
                                "Ini build development — verifikasi lisensi akan selalu ditolak sampai " +
                                "developer menjalankan scripts/generate-license-keypair.js dan menempel " +
                                "public key-nya. Lihat LICENSING_SETUP.md.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            when (licenseState.status) {
                LicenseStatus.ACTIVE -> {
                    // BUG (ditemukan saat audit ulang): sebelum ini, status ACTIVE ikut jatuh ke
                    // cabang `else` di bawah yang menampilkan ajakan "Masukkan Kode Lisensi" —
                    // membingungkan kalau admin sengaja membuka Pengaturan > Status Lisensi untuk
                    // SEKADAR MELIHAT lisensi yang sudah aktif (tidak sedang bermasalah sama
                    // sekali). Sekarang ACTIVE punya tampilan konfirmasi bersih sendiri (ikon
                    // kunci umum di atas sudah cukup, tidak perlu ikon kedua di sini).
                    Text(
                        "Lisensi Aktif ✓",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Lisensi sekali bayar — berlaku permanen di device ini, tidak perlu diperbarui atau dibayar ulang.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LicenseStatus.TRIAL -> {
                    val daysLeft = remember(licenseState.trialEndsAt) {
                        val millisLeft = (licenseState.trialEndsAt ?: 0L) - System.currentTimeMillis()
                        (millisLeft / (24 * 60 * 60 * 1000)).toInt().coerceAtLeast(0) + 1
                    }
                    Text(
                        "Masa Coba: $daysLeft hari lagi",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Semua fitur, termasuk fitur prioritas (QRIS Otomatis, Sinkronisasi Cloud, " +
                            "Cek Stok Lintas Cabang), bisa dicoba penuh selama masa ini. Aktivasi " +
                            "kapan saja lewat kode lisensi dari penjual — cukup sekali, berlaku selamanya.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                }
                LicenseStatus.TRIAL_EXPIRED -> {
                    Text(
                        "Masa Coba Sudah Berakhir",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Tenang, transaksi harian (Kasir, Produk, Stok, Laporan) tetap bisa dipakai " +
                            "seperti biasa. Yang terkunci hanya fitur prioritas (QRIS Otomatis, " +
                            "Sinkronisasi Cloud, Cek Stok Lintas Cabang) sampai lisensi diaktivasi — " +
                            "sekali bayar, berlaku selamanya, tidak ada biaya berulang.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                }
                LicenseStatus.REVOKED -> {
                    Text(
                        "Lisensi Dinonaktifkan",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Penjual/developer menonaktifkan lisensi ini (mis. refund/chargeback). " +
                            "Transaksi harian tetap bisa dipakai, hanya fitur prioritas yang terkunci. " +
                            "Kalau ini keliru, hubungi penjual — setelah diaktifkan kembali, tekan " +
                            "tombol di bawah untuk memeriksa ulang, atau masukkan kode lisensi baru.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = { viewModel.checkStatusNow() }, enabled = !uiState.isLoading) {
                        Text("Cek Status Lisensi Sekarang")
                    }
                    Spacer(Modifier.height(8.dp))
                }
                else -> {
                    Text(
                        "Masukkan Kode Lisensi",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Kode ini dikirim penjual/developer aplikasi setelah pembelian (lewat WhatsApp/" +
                            "email) — lisensi SEKALI BAYAR, bukan langganan. Tempel di bawah lalu tekan " +
                            "Aktivasi — cukup sekali, HP butuh internet hanya saat proses ini, setelahnya " +
                            "berlaku selamanya secara offline.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }

            if (needsKeyInput) {
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    label = { Text("Kode Lisensi") },
                    placeholder = { Text("Contoh: POS-XXXX-XXXX-XXXX") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.activate(keyInput) },
                    enabled = !uiState.isLoading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Aktivasi")
                    }
                }
            }

            uiState.errorMessage?.let { message ->
                Spacer(Modifier.height(12.dp))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(message, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                }
            }

            licenseState.activatedAt?.let { activatedAt ->
                Spacer(Modifier.height(24.dp))
                val formatted = remember(activatedAt) {
                    SimpleDateFormat("dd MMM yyyy", Locale("in", "ID")).format(Date(activatedAt))
                }
                Text(
                    "Terdaftar atas nama: ${licenseState.customerName ?: "-"}\nAktif sejak: $formatted (sekali bayar, berlaku permanen)",
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
