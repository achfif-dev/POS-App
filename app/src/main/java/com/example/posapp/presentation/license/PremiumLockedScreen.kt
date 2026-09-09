package com.example.posapp.presentation.license

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Ditampilkan menggantikan konten rute FITUR PRIORITAS (Sinkronisasi Cloud, Multi-Cabang, Cek
 * Stok Lintas Cabang — lihat `PremiumFeatureGate` di MainActivity.kt) saat lisensi belum
 * diaktivasi dan masa coba 15 hari sudah habis (atau lisensi berbayar lewat masa tenggang).
 *
 * SENGAJA BUKAN layar yang mengunci seluruh app — pengguna sampai di sini karena secara sadar
 * membuka salah satu fitur prioritas tadi, jadi pesannya spesifik ke fitur itu, bukan ke
 * aplikasi secara umum. Fitur inti (Kasir/Produk/Stok/Laporan) tidak pernah lewat layar ini.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumLockedScreen(onActivate: () -> Unit, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fitur Prioritas") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                modifier = Modifier.height(56.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Fitur Ini Perlu Aktivasi Lisensi",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Masa coba 15 hari untuk fitur prioritas (QRIS Otomatis, Sinkronisasi Cloud, Cek " +
                    "Stok Lintas Cabang) sudah berakhir. Transaksi harian di Kasir tetap berjalan " +
                    "normal — aktivasi lisensi untuk membuka kembali fitur ini.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onActivate, modifier = Modifier.fillMaxWidth()) {
                Text("Aktivasi Lisensi")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Kembali")
            }
        }
    }
}
