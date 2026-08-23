package com.example.posapp.presentation.report

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.NumberFormat
import java.util.Locale

private val rupiah: NumberFormat = NumberFormat.getCurrencyInstance(Locale("in", "ID"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    viewModel: ReportViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Laporan Penjualan") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PresetChip("Hari Ini", uiState.preset == ReportRangePreset.TODAY) { viewModel.selectPreset(ReportRangePreset.TODAY) }
                PresetChip("Minggu Ini", uiState.preset == ReportRangePreset.THIS_WEEK) { viewModel.selectPreset(ReportRangePreset.THIS_WEEK) }
                PresetChip("Bulan Ini", uiState.preset == ReportRangePreset.THIS_MONTH) { viewModel.selectPreset(ReportRangePreset.THIS_MONTH) }
            }

            Spacer(Modifier.height(16.dp))

            if (uiState.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryCard(
                    modifier = Modifier.weight(1f),
                    label = "Total Omzet",
                    value = rupiah.format(uiState.summary.totalRevenue),
                    accent = MaterialTheme.colorScheme.primary
                )
                SummaryCard(
                    modifier = Modifier.weight(1f),
                    label = "Laba Kotor",
                    value = rupiah.format(uiState.summary.totalGrossProfit),
                    accent = MaterialTheme.colorScheme.tertiary
                )
            }
            Spacer(Modifier.height(8.dp))
            SummaryCard(
                modifier = Modifier.fillMaxWidth(),
                label = "Jumlah Transaksi",
                value = "${uiState.summary.totalTransactions}",
                accent = MaterialTheme.colorScheme.secondary
            )

            Spacer(Modifier.height(24.dp))
            Text("Produk Terlaris", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            if (uiState.topItems.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    Text("Belum ada penjualan pada periode ini", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn {
                    items(uiState.topItems) { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(item.productName, fontWeight = FontWeight.Medium)
                                Text("Terjual: ${item.totalQty}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(rupiah.format(item.totalRevenue))
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun SummaryCard(modifier: Modifier = Modifier, label: String, value: String, accent: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary) {
    Card(modifier = modifier, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(16.dp)) {
            Box(
                Modifier
                    .width(28.dp)
                    .height(3.dp)
                    .background(accent, shape = MaterialTheme.shapes.extraSmall)
            )
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}
