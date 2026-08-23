package com.example.posapp.presentation.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.posapp.presentation.theme.PosAccentPresets
import com.example.posapp.presentation.theme.PosFontOption
import com.example.posapp.presentation.theme.parseHexColorOrNull
import java.io.File
import com.example.posapp.presentation.theme.PosBrandedTopBar

/** Format persentase pajak tanpa desimal berlebih, mis. 11.0 -> "11", 8.5 -> "8.5". */
private fun formatTaxPercent(percent: Double): String =
    if (percent == percent.toLong().toDouble()) percent.toLong().toString() else percent.toString()

/** Layar Profil Toko — nama, alamat, telepon, catatan struk, dan gambar QRIS statis. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreProfileScreen(
    viewModel: StoreProfileViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val profile by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var name by remember(profile.name) { mutableStateOf(profile.name) }
    var address by remember(profile.address) { mutableStateOf(profile.address) }
    var phone by remember(profile.phone) { mutableStateOf(profile.phone) }
    var footer by remember(profile.receiptFooter) { mutableStateOf(profile.receiptFooter) }

    val qrisPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val qrisDir = File(context.filesDir, "qris").apply { mkdirs() }
            val destFile = File(qrisDir, "qris_image.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
            viewModel.setQrisImagePath(destFile.absolutePath)
        }
    }

    val logoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val logoDir = File(context.filesDir, "logo").apply { mkdirs() }
            val destFile = File(logoDir, "store_logo.png")
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
            viewModel.setLogoImagePath(destFile.absolutePath)
        }
    }

    var customHexInput by remember(profile.appColorHex) { mutableStateOf(profile.appColorHex ?: "") }
    var taxPercentInput by remember(profile.taxPercent) { mutableStateOf(formatTaxPercent(profile.taxPercent)) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is StoreProfileEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            PosBrandedTopBar(
                title = { Text("Profil Toko") },
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
            Text("Informasi Toko", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Nama Toko") }, modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = address, onValueChange = { address = it },
                label = { Text("Alamat") }, modifier = Modifier.fillMaxWidth(), minLines = 2
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = phone, onValueChange = { phone = it },
                label = { Text("Nomor Telepon") }, modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = footer, onValueChange = { footer = it },
                label = { Text("Catatan Kaki Struk") }, modifier = Modifier.fillMaxWidth(), minLines = 2
            )

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { viewModel.save(name, address, phone, footer) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Simpan Profil Toko") }

            Spacer(Modifier.height(28.dp))
            Text("Pajak (PPN)", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Nonaktifkan bila toko tidak memungut pajak ke pelanggan",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Pungut Pajak", fontWeight = FontWeight.Medium)
                            Text(
                                if (profile.taxEnabled) "Pajak ${formatTaxPercent(profile.taxPercent)}% ditambahkan ke setiap transaksi"
                                else "Transaksi tidak dikenakan pajak",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = profile.taxEnabled,
                            onCheckedChange = { checked ->
                                viewModel.setTaxSettings(checked, taxPercentInput.toDoubleOrNull() ?: profile.taxPercent)
                            }
                        )
                    }
                    if (profile.taxEnabled) {
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = taxPercentInput,
                                onValueChange = { taxPercentInput = it.filter { c -> c.isDigit() || c == '.' } },
                                label = { Text("Persentase Pajak (%)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(12.dp))
                            Button(
                                onClick = {
                                    viewModel.setTaxSettings(true, taxPercentInput.toDoubleOrNull() ?: 11.0)
                                },
                                enabled = taxPercentInput.toDoubleOrNull() != null
                            ) { Text("Simpan") }
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            Text("Gambar QRIS", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Ditampilkan otomatis di layar pembayaran saat pelanggan memilih QRIS",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (profile.qrisImagePath != null) {
                        AsyncImage(
                            model = profile.qrisImagePath,
                            contentDescription = "Gambar QRIS",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(200.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        val dynamicActive = profile.qrisRawContent != null
                        AssistChip(
                            onClick = {},
                            label = { Text(if (dynamicActive) "QRIS Dinamis aktif (nominal otomatis)" else "Nominal manual (kode QR tidak terbaca)") },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (dynamicActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                            )
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { qrisPickerLauncher.launch("image/*") }) {
                                Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Ganti Gambar")
                            }
                            OutlinedButton(onClick = { viewModel.setQrisImagePath(null) }) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Hapus")
                            }
                        }
                    } else {
                        Icon(
                            Icons.Default.QrCode2,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Belum ada gambar QRIS", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { qrisPickerLauncher.launch("image/*") }) {
                            Text("Upload Gambar QRIS")
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            Text("Logo Toko", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Dicetak di bagian atas struk Bluetooth dan invoice PDF",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (profile.logoImagePath != null) {
                        AsyncImage(
                            model = profile.logoImagePath,
                            contentDescription = "Logo toko",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(120.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { logoPickerLauncher.launch("image/*") }) {
                                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Ganti Logo")
                            }
                            OutlinedButton(onClick = { viewModel.setLogoImagePath(null) }) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Hapus")
                            }
                        }
                    } else {
                        Icon(
                            Icons.Default.Image,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Belum ada logo toko", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { logoPickerLauncher.launch("image/*") }) {
                            Text("Upload Logo Toko")
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            Text("Tampilan Aplikasi", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Ubah warna aksen aplikasi (tombol, highlight, ikon aktif)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(6),
                        modifier = Modifier.height(140.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(PosAccentPresets) { (label, color) ->
                            val hex = String.format("#%06X", 0xFFFFFF and color.toArgb())
                            val isSelected = profile.appColorHex?.equals(hex, ignoreCase = true) == true
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        customHexInput = hex
                                        viewModel.setAppColor(hex)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = label,
                                        tint = if (color.luminance() > 0.55f) Color.Black else Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Text("Atau masukkan kode warna sendiri", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(parseHexColorOrNull(customHexInput) ?: MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                        )
                        Spacer(Modifier.width(12.dp))
                        OutlinedTextField(
                            value = customHexInput,
                            onValueChange = { customHexInput = it },
                            label = { Text("Kode Hex (mis. #E8590C)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val parsed = parseHexColorOrNull(customHexInput)
                                if (parsed != null) {
                                    val normalized = String.format("#%06X", 0xFFFFFF and parsed.toArgb())
                                    customHexInput = normalized
                                    viewModel.setAppColor(normalized)
                                }
                            },
                            enabled = parseHexColorOrNull(customHexInput) != null
                        ) {
                            Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Terapkan Warna")
                        }
                        OutlinedButton(onClick = {
                            customHexInput = ""
                            viewModel.setAppColor(null)
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Reset ke Default")
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(16.dp))
                    Text("Font Aplikasi", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Butuh koneksi internet sekali untuk mengunduh font baru, setelah itu tersimpan di HP",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        PosFontOption.entries.forEach { option ->
                            val isSelected = profile.fontChoice == option.key
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { viewModel.setFontChoice(option.key) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = isSelected, onClick = { viewModel.setFontChoice(option.key) })
                                Spacer(Modifier.width(4.dp))
                                Text(option.label, fontFamily = option.family)
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(16.dp))
                    Text("Bahasa Struk & Invoice", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Bahasa yang dipakai di label struk cetak & PDF invoice (mis. Subtotal/Total)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = profile.receiptLanguage == "id",
                            onClick = { viewModel.setReceiptLanguage("id") },
                            label = { Text("Indonesia") }
                        )
                        FilterChip(
                            selected = profile.receiptLanguage == "en",
                            onClick = { viewModel.setReceiptLanguage("en") },
                            label = { Text("English") }
                        )
                    }
                }
            }
        }
    }
}
