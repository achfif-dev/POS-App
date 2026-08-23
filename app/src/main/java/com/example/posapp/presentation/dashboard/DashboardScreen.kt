package com.example.posapp.presentation.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.NumberFormat
import java.util.Locale

private val rupiah: NumberFormat = NumberFormat.getCurrencyInstance(Locale("in", "ID"))

/**
 * Dashboard ringkasan — halaman pertama setelah login. Menampilkan omzet & jumlah transaksi
 * hari ini, laba kotor, produk terlaris, dan peringatan stok tipis, plus jalan pintas ke
 * modul-modul utama (Kasir, Produk, Stok, Laporan, Pengaturan).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onOpenPos: () -> Unit = {},
    onOpenProducts: () -> Unit = {},
    onOpenStock: () -> Unit = {},
    onOpenReports: () -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Storefront, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Dashboard")
                            uiState.cashierName?.let {
                                Text("Halo, $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onOpenPos,
                icon = { Icon(Icons.Default.PointOfSale, contentDescription = null) },
                text = { Text("Buka Kasir") },
                modifier = Modifier.navigationBarsPadding()
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Ringkasan Hari Ini", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    SummaryCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.AutoMirrored.Filled.TrendingUp,
                        accent = MaterialTheme.colorScheme.primary,
                        label = "Omzet",
                        value = rupiah.format(uiState.todayRevenue)
                    )
                    SummaryCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Receipt,
                        accent = MaterialTheme.colorScheme.tertiary,
                        label = "Transaksi",
                        value = "${uiState.todayTransactions}"
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    SummaryCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.ShoppingCart,
                        accent = MaterialTheme.colorScheme.secondary,
                        label = "Laba Kotor",
                        value = rupiah.format(uiState.todayGrossProfit)
                    )
                    SummaryCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.WarningAmber,
                        accent = MaterialTheme.colorScheme.error,
                        label = "Stok Tipis",
                        value = "${uiState.lowStockCount} produk",
                        onClick = onOpenStock
                    )
                }
            }

            if (uiState.revenueTrend.isNotEmpty()) {
                item {
                    Text("Tren Omzet 7 Hari Terakhir", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                item {
                    RevenueTrendChart(uiState.revenueTrend)
                }
            }

            item {
                Text("Produk Terlaris Hari Ini", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            if (uiState.topProducts.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Box(Modifier.padding(20.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("Belum ada transaksi hari ini", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            uiState.topProducts.forEachIndexed { index, item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${index + 1}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(item.productName, fontWeight = FontWeight.SemiBold)
                                        Text("${item.totalQty} terjual", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(rupiah.format(item.totalRevenue), style = MaterialTheme.typography.bodyMedium)
                                }
                                if (index != uiState.topProducts.lastIndex) HorizontalDivider()
                            }
                        }
                    }
                }
            }

            item {
                Text("Menu Cepat", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    QuickMenuButton(Modifier.weight(1f), Icons.Default.Inventory2, "Produk", onOpenProducts)
                    QuickMenuButton(Modifier.weight(1f), Icons.AutoMirrored.Filled.TrendingUp, "Laporan", onOpenReports)
                    if (uiState.isAdmin) {
                        QuickMenuButton(Modifier.weight(1f), Icons.Default.Settings, "Pengaturan", onOpenSettings)
                    }
                }
            }
            item { Spacer(Modifier.height(72.dp)) } // ruang untuk FAB
        }
    }
}

@Composable
private fun SummaryCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    accent: androidx.compose.ui.graphics.Color,
    label: String,
    value: String,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier,
        onClick = onClick ?: {},
        shape = MaterialTheme.shapes.medium
    ) {
        Column(Modifier.padding(14.dp)) {
            Box(
                modifier = Modifier.size(32.dp).clip(CircleShape).background(accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

/** Grafik batang mini omzet 7 hari terakhir, digambar langsung dengan Canvas (tanpa library chart). */
@Composable
private fun RevenueTrendChart(trend: List<DayRevenue>) {
    val barColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val maxRevenue = (trend.maxOfOrNull { it.revenue } ?: 0.0).coerceAtLeast(1.0)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().height(120.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                trend.forEach { day ->
                    val fraction = (day.revenue / maxRevenue).toFloat().coerceIn(0f, 1f)
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .width(18.dp)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Canvas(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                                val barHeight = size.height * fraction
                                // trek belakang (menunjukkan tinggi maksimum area grafik)
                                drawRoundRect(
                                    color = trackColor,
                                    topLeft = Offset(0f, 0f),
                                    size = androidx.compose.ui.geometry.Size(size.width, size.height),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                                )
                                drawRoundRect(
                                    color = barColor,
                                    topLeft = Offset(0f, size.height - barHeight),
                                    size = androidx.compose.ui.geometry.Size(size.width, barHeight.coerceAtLeast(4f)),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(day.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Tertinggi: ${rupiah.format(maxRevenue)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun QuickMenuButton(modifier: Modifier = Modifier, icon: ImageVector, label: String, onClick: () -> Unit) {
    OutlinedCard(modifier = modifier, onClick = onClick, shape = MaterialTheme.shapes.medium) {
        Column(
            Modifier.padding(vertical = 16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}
