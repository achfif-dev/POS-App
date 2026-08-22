package com.example.posapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.posapp.presentation.pos.PosScreen
import com.example.posapp.presentation.product.ProductScreen
import com.example.posapp.presentation.report.ReportScreen
import com.example.posapp.presentation.scanner.BarcodeScannerScreen
import com.example.posapp.presentation.settings.SettingsScreen
import com.example.posapp.presentation.stock.StockScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PosAppTheme {
                Surface(modifier = Modifier) {
                    PosNavHost()
                }
            }
        }
    }
}

@Composable
fun PosNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "pos") {
        composable("pos") { backStackEntry ->
            val scannedSku = backStackEntry.savedStateHandle
                .getStateFlow<String?>("scanned_sku", null)
                .collectAsState()
            PosScreen(
                onOpenProducts = { navController.navigate("products") },
                onOpenScanner = { navController.navigate("scanner") },
                onOpenReports = { navController.navigate("reports") },
                onOpenStock = { navController.navigate("stock") },
                onOpenSettings = { navController.navigate("settings") },
                scannedSku = scannedSku.value,
                onScannedSkuConsumed = { backStackEntry.savedStateHandle["scanned_sku"] = null }
            )
        }
        composable("products") { ProductScreen(onBack = { navController.popBackStack() }) }
        composable("scanner") {
            BarcodeScannerScreen(
                onBarcodeDetected = { sku ->
                    navController.previousBackStackEntry?.savedStateHandle?.set("scanned_sku", sku)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable("reports") { ReportScreen(onBack = { navController.popBackStack() }) }
        composable("stock") { StockScreen(onBack = { navController.popBackStack() }) }
        composable("settings") { SettingsScreen(onBack = { navController.popBackStack() }) }
    }
}

@Composable
fun PosAppTheme(content: @Composable () -> Unit) {
    val colorScheme = if (isSystemInDarkTheme()) {
        darkColorScheme()
    } else {
        lightColorScheme()
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
