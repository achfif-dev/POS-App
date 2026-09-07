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
 * Layar ini menggantikan SELURUH isi app (bukan salah satu rute biasa) selama status lisensi
 * bukan ACTIVE/GRACE_PERIOD — lihat pemanggilannya di MainActivity (`LicenseGate`). Didesain
 * supaya PEMBELI aplikasi bisa mengaktifkan sendiri hanya dengan kode lisensi dari penjual,
 * tanpa perlu developer login/setting manual ke HP pelanggan.
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

    val isExpiredNeedsInput = licenseState.status == LicenseStatus.EXPIRED || licenseState.status == LicenseStatus.NOT_ACTIVATED

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
                }
                LicenseStatus.GRACE_PERIOD -> {
                    Text(
                        "Lisensi akan segera perlu diperbarui otomatis. Sambungkan HP ke internet " +
                            "sebentar (WiFi/data) — aplikasi akan memperbarui sendiri di latar belakang.",
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onActivated) { Text("Lanjutkan pakai aplikasi") }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { viewModel.retryRevalidate() }, enabled = !uiState.isLoading) {
                        Text("Coba perbarui sekarang")
                    }
                }
                LicenseStatus.EXPIRED -> {
                    Text(
                        "Masa aktif lisensi habis dan belum berhasil diperpanjang otomatis.",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Sambungkan HP ke internet lalu tekan tombol di bawah. Kalau kode lisensi " +
                            "berubah (mis. berlangganan ulang), masukkan kode barunya.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { viewModel.retryRevalidate() }, enabled = !uiState.isLoading, modifier = Modifier.fillMaxWidth()) {
                        Text("Perbarui Lisensi Sekarang")
                    }
                }
                else -> {
                    Text(
                        "Masukkan Kode Lisensi",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Kode ini dikirim penjual/developer aplikasi setelah pembelian (lewat WhatsApp/" +
                            "email). Tempel di bawah lalu tekan Aktivasi — cukup sekali, HP butuh internet " +
                            "hanya saat proses ini.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }

            if (isExpiredNeedsInput) {
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

            licenseState.validUntil?.let { validUntil ->
                Spacer(Modifier.height(24.dp))
                val formatted = remember(validUntil) {
                    SimpleDateFormat("dd MMM yyyy", Locale("in", "ID")).format(Date(validUntil))
                }
                Text(
                    "Terdaftar atas nama: ${licenseState.customerName ?: "-"}\nBerlaku otomatis diperpanjang, tenggat saat ini: $formatted",
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
