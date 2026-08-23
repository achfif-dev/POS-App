package com.example.posapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.posapp.data.auth.SessionManager
import com.example.posapp.data.local.entity.UserRole
import com.example.posapp.presentation.auth.AuthGateViewModel
import com.example.posapp.presentation.auth.LoginScreen
import com.example.posapp.presentation.dashboard.DashboardScreen
import com.example.posapp.presentation.pos.PosScreen
import com.example.posapp.presentation.product.ProductScreen
import com.example.posapp.presentation.report.ReportScreen
import com.example.posapp.presentation.scanner.BarcodeScannerScreen
import com.example.posapp.presentation.settings.SettingsScreen
import com.example.posapp.presentation.settings.StoreProfileScreen
import com.example.posapp.presentation.settings.StoreProfileViewModel
import com.example.posapp.presentation.settings.UserManagementScreen
import com.example.posapp.presentation.stock.StockScreen
import com.example.posapp.presentation.theme.PosAppTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PosAppTheme {
                Surface(modifier = Modifier) {
                    PosNavHost(sessionManager = sessionManager)
                }
            }
        }
    }
}

@Composable
fun PosNavHost(sessionManager: SessionManager) {
    val navController = rememberNavController()
    val storeViewModel: StoreProfileViewModel = hiltViewModel()
    val storeProfile by storeViewModel.uiState.collectAsState()

    val startDestination = "login_gate"

    NavHost(navController = navController, startDestination = startDestination) {
        composable("login_gate") {
            // Gate login: tampilkan layar login jika belum ada user sama sekali (wajib setup
            // admin pertama) ATAU jika fitur "Wajibkan Login PIN" aktif dan belum ada sesi login.
            val authGateViewModel: AuthGateViewModel = hiltViewModel()
            val requiresLogin by authGateViewModel.requiresLogin.collectAsState()
            val currentUser by sessionManager.currentUser.collectAsState()
            androidx.compose.runtime.LaunchedEffect(requiresLogin, currentUser) {
                val needsLogin = requiresLogin ?: return@LaunchedEffect // masih memuat, tunggu
                val target = if (needsLogin && currentUser == null) "login" else "dashboard"
                navController.navigate(target) {
                    popUpTo("login_gate") { inclusive = true }
                }
            }
        }
        composable("login") {
            LoginScreen(onLoginSuccess = {
                navController.navigate("dashboard") { popUpTo("login") { inclusive = true } }
            })
        }
        composable("dashboard") {
            DashboardScreen(
                onOpenPos = { navController.navigate("pos") },
                onOpenProducts = { navController.navigate("products") },
                onOpenStock = { navController.navigate("stock") },
                onOpenReports = { navController.navigate("reports") },
                onOpenSettings = { navController.navigate("settings") }
            )
        }
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
                onOpenDashboard = { navController.navigate("dashboard") { popUpTo("dashboard") { inclusive = true } } },
                onLogout = {
                    sessionManager.logout()
                    navController.navigate("login") {
                        popUpTo("pos") { inclusive = true }
                    }
                },
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
        composable("settings") {
            // Guard peran: Kasir tidak diizinkan membuka Pengaturan (tautan pribadi/deep-link
            // sekalipun) — menu ini sudah disembunyikan di UI, ini lapisan pertahanan kedua.
            val currentUser by sessionManager.currentUser.collectAsState()
            val isAdmin = currentUser == null || currentUser?.role == UserRole.ADMIN
            if (isAdmin) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenStoreProfile = { navController.navigate("store_profile") },
                    onOpenUserManagement = { navController.navigate("user_management") }
                )
            } else {
                androidx.compose.runtime.LaunchedEffect(Unit) { navController.popBackStack() }
            }
        }
        composable("store_profile") { StoreProfileScreen(onBack = { navController.popBackStack() }) }
        composable("user_management") { UserManagementScreen(onBack = { navController.popBackStack() }) }
    }
}
