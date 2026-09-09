package com.example.posapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.posapp.data.auth.AutoLockManager
import com.example.posapp.data.auth.LocalAutoLockManager
import com.example.posapp.data.auth.SessionManager
import com.example.posapp.domain.auth.Permission
import com.example.posapp.presentation.auth.AuthGateViewModel
import com.example.posapp.presentation.license.LicenseActivationScreen
import com.example.posapp.presentation.license.LicenseViewModel
import com.example.posapp.presentation.auth.LoginScreen
import com.example.posapp.presentation.customer.CustomerDetailScreen
import com.example.posapp.presentation.customer.CustomerScreen
import com.example.posapp.presentation.dashboard.DashboardScreen
import com.example.posapp.presentation.expense.ExpenseScreen
import com.example.posapp.presentation.onboarding.OnboardingScreen
import com.example.posapp.presentation.pos.PosScreen
import com.example.posapp.presentation.product.ProductScreen
import com.example.posapp.presentation.report.ReportScreen
import com.example.posapp.presentation.scanner.BarcodeScannerScreen
import com.example.posapp.presentation.settings.AuditLogScreen
import com.example.posapp.presentation.settings.SettingsScreen
import com.example.posapp.presentation.settings.StoreProfileScreen
import com.example.posapp.presentation.settings.StoreProfileViewModel
import com.example.posapp.presentation.settings.UserManagementScreen
import com.example.posapp.presentation.shift.ShiftRequiredPrompt
import com.example.posapp.presentation.shift.ShiftScreen
import com.example.posapp.presentation.shift.ShiftViewModel
import com.example.posapp.presentation.stock.StockScreen
import com.example.posapp.presentation.sync.CloudSyncScreen
import com.example.posapp.presentation.sync.MultiOutletDashboardScreen
import com.example.posapp.presentation.theme.PosAppTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var sessionManager: SessionManager
    @Inject lateinit var autoLockManager: AutoLockManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val storeViewModel: StoreProfileViewModel = hiltViewModel()
            val storeProfile by storeViewModel.uiState.collectAsState()
            PosAppTheme(customPrimaryHex = storeProfile.appColorHex, fontChoice = storeProfile.fontChoice) {
                CompositionLocalProvider(LocalAutoLockManager provides autoLockManager) {
                    Surface(modifier = Modifier) {
                        LicenseGate {
                            PosNavHost(sessionManager = sessionManager, autoLockManager = autoLockManager)
                        }
                    }
                }
            }
        }
    }

    // Auto-lock lapisan pertama: app di-background (Home/app-switch/layar mati) lalu dibuka
    // lagi -> minta PIN ulang. Ini dicek di onStart (bukan cuma dicatat di onStop) supaya
    // keputusan logout terjadi tepat saat app kembali terlihat, sebelum pengguna sempat
    // berinteraksi dengan layar yang seharusnya sudah terkunci.
    override fun onStop() {
        super.onStop()
        autoLockManager.onAppBackgrounded()
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch { autoLockManager.onAppForegroundedCheckLock() }
    }
}

/**
 * Lisensi TIDAK PERNAH mengganti/mengunci seluruh app lagi (lihat catatan filosofi di
 * `LicenseStatus` & `LicenseState.hasPremiumAccess` di LicenseModels.kt) — [content] (nav-graph
 * lengkap: login, Kasir, Produk, Stok, Laporan, dst.) SELALU dirender apa pun status lisensinya,
 * termasuk masa coba yang sudah habis atau lisensi berbayar yang lewat masa tenggang. Ini
 * sengaja dibiarkan sebagai wrapper tipis (bukan langsung dihapus) supaya kalau suatu saat perlu
 * menambah sesuatu yang berlaku di seluruh app terkait lisensi (mis. banner global), tempatnya
 * sudah jelas di sini — bukan supaya menggerbang [content].
 */
@Composable
private fun LicenseGate(content: @Composable () -> Unit) {
    content()
}

/**
 * Pembungkus untuk rute FITUR PRIORITAS (Sinkronisasi Cloud, Multi-Cabang, Cek Stok Lintas
 * Cabang) — satu-satunya tempat status lisensi benar-benar membatasi sesuatu. Kalau
 * [hasPremiumAccess] false (belum aktivasi & masa coba 15 hari sudah habis, ATAU lisensi
 * berbayar sudah lewat masa tenggang), tampilkan [PremiumLockedScreen] yang mengajak aktivasi,
 * alih-alih konten asli rute tersebut. Fitur inti (Kasir/Produk/Stok/Laporan) TIDAK PERNAH
 * dibungkus dengan ini.
 */
@Composable
private fun PremiumFeatureGate(
    hasPremiumAccess: Boolean,
    onActivate: () -> Unit,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (hasPremiumAccess) {
        content()
    } else {
        com.example.posapp.presentation.license.PremiumLockedScreen(onActivate = onActivate, onBack = onBack)
    }
}

@Composable
fun PosNavHost(sessionManager: SessionManager, autoLockManager: AutoLockManager) {
    val navController = rememberNavController()
    val storeViewModel: StoreProfileViewModel = hiltViewModel()
    val storeProfile by storeViewModel.uiState.collectAsState()
    val currentUser by sessionManager.currentUser.collectAsState()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    // Status lisensi tunggal untuk seluruh nav-graph — dipakai HANYA untuk menggerbang fitur
    // prioritas (lihat PremiumFeatureGate), tidak pernah untuk fitur inti.
    val licenseViewModel: LicenseViewModel = hiltViewModel()
    val licenseState by licenseViewModel.licenseState.collectAsState()

    val startDestination = "login_gate"

    // Auto-lock lapisan kedua: idle-timeout selagi app tetap di foreground. Berjalan sekali
    // untuk seumur hidup composable ini (Unit key), memeriksa berkala lewat AutoLockManager.
    LaunchedEffect(Unit) { autoLockManager.runIdleWatcher() }

    // Kalau sesi tiba-tiba jadi null (auto-lock ATAU logout manual) sementara pengguna sedang
    // berada di layar selain login/login_gate, paksa kembali ke layar login dan bersihkan
    // seluruh back stack — supaya tombol Back tidak bisa "menembus" ke layar yang tadinya
    // sudah dibuka sebelum terkunci.
    LaunchedEffect(currentUser, currentBackStackEntry) {
        val route = currentBackStackEntry?.destination?.route
        val onAuthScreen = route == "login" || route == "login_gate" || route == "onboarding" || route == null
        if (currentUser == null && storeProfile.pinLoginEnabled && !onAuthScreen) {
            navController.navigate("login") {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        // Modifier.pointerInput di sini mendeteksi SETIAP interaksi sentuh di mana pun di dalam
        // NavHost (initial pass, tidak mengganggu event konsumsi Compose lain di bawahnya) untuk
        // mereset hitung mundur idle-timeout — jadi kasir yang aktif memakai app tidak akan
        // ter-logout mendadak di tengah transaksi.
        modifier = Modifier.fillMaxSize().pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(pass = PointerEventPass.Initial)
                    autoLockManager.recordInteraction()
                }
            }
        }
    ) {
        composable("login_gate") {
            // Gate login: tampilkan Onboarding Wizard dulu kalau instalasi ini belum pernah
            // menyelesaikan/melewatinya (SATU KALI seumur instal) — baru setelah itu, layar
            // login jika belum ada user sama sekali (wajib setup admin pertama) ATAU jika fitur
            // "Wajibkan Login PIN" aktif dan belum ada sesi login.
            val authGateViewModel: AuthGateViewModel = hiltViewModel()
            val requiresLogin by authGateViewModel.requiresLogin.collectAsState()
            val currentUser by sessionManager.currentUser.collectAsState()
            androidx.compose.runtime.LaunchedEffect(requiresLogin, currentUser, storeProfile.onboardingCompleted) {
                val needsLogin = requiresLogin ?: return@LaunchedEffect // masih memuat, tunggu
                val target = when {
                    !storeProfile.onboardingCompleted -> "onboarding"
                    needsLogin && currentUser == null -> "login"
                    else -> "dashboard"
                }
                navController.navigate(target) {
                    popUpTo("login_gate") { inclusive = true }
                }
            }
        }
        composable("onboarding") {
            // Setelah wizard selesai/dilewati, lanjutkan ke tujuan yang sama seperti login_gate
            // akan tentukan (login kalau masih perlu setup admin/PIN, atau langsung dashboard).
            val authGateViewModel: AuthGateViewModel = hiltViewModel()
            val requiresLogin by authGateViewModel.requiresLogin.collectAsState()
            val currentUser by sessionManager.currentUser.collectAsState()
            OnboardingScreen(
                onFinished = {
                    val needsLogin = requiresLogin ?: true
                    val target = if (needsLogin && currentUser == null) "login" else "dashboard"
                    navController.navigate(target) {
                        popUpTo("onboarding") { inclusive = true }
                    }
                }
            )
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
                onOpenSettings = { navController.navigate("settings") },
                onOpenShift = { navController.navigate("shift") },
                onOpenCustomers = { navController.navigate("customers") },
                licenseState = licenseState,
                onOpenLicense = { navController.navigate("license_status") },
            )
        }
        composable("pos") { backStackEntry ->
            val scannedSku = backStackEntry.savedStateHandle
                .getStateFlow<String?>("scanned_sku", null)
                .collectAsState()
            // Gerbang shift: kalau "Wajibkan Login PIN" aktif (mode multi-kasir), transaksi tidak
            // boleh berjalan tanpa shift terbuka -- supaya kas tunai selalu bisa direkonsiliasi ke
            // shift & kasir yang jelas. Mode single-user (PIN nonaktif) tidak digerbang: toko kecil
            // yang tidak butuh disiplin shift tidak dipaksa memakainya.
            val shiftGateViewModel: ShiftViewModel = hiltViewModel()
            val activeShift by shiftGateViewModel.activeShift.collectAsState()
            if (storeProfile.pinLoginEnabled && activeShift == null) {
                ShiftRequiredPrompt(onOpenShift = { navController.navigate("shift") })
            } else {
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
                    onScannedSkuConsumed = { backStackEntry.savedStateHandle["scanned_sku"] = null },
                    // Fitur prioritas (QRIS Otomatis) — QRIS statis manual TETAP jalan sebagai
                    // cadangan kalau ini false, lihat pemakaiannya di PosScreen.kt.
                    hasPremiumAccess = licenseState.hasPremiumAccess,
                    onOpenLicenseActivation = { navController.navigate("license_status") },
                )
            }
        }
        composable("shift") {
            ShiftScreen(
                onBack = { navController.popBackStack() },
                onShiftOpened = { }
            )
        }
        composable("customers") {
            CustomerScreen(
                onBack = { navController.popBackStack() },
                onOpenDetail = { customerId -> navController.navigate("customer_detail/$customerId") }
            )
        }
        composable(
            route = "customer_detail/{customerId}",
            arguments = listOf(navArgument("customerId") { type = NavType.LongType })
        ) {
            CustomerDetailScreen(onBack = { navController.popBackStack() })
        }
        composable("products") {
            // Manajemen Produk (termasuk harga beli/margin) — ADMIN & MANAGER, KASIR ditolak.
            // Sebelumnya rute ini tidak digerbang sama sekali (audit 2026-09-06).
            val currentUser by sessionManager.currentUser.collectAsState()
            val allowed = Permission.canManageProducts(currentUser, storeProfile.pinLoginEnabled)
            RoleGatedRoute(allowed = allowed, navController = navController) {
                ProductScreen(onBack = { navController.popBackStack() })
            }
        }
        composable("scanner") {
            BarcodeScannerScreen(
                onBarcodeDetected = { sku ->
                    navController.previousBackStackEntry?.savedStateHandle?.set("scanned_sku", sku)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable("reports") {
            ReportScreen(
                onBack = { navController.popBackStack() },
                onOpenExpenses = { navController.navigate("expenses") }
            )
        }
        composable("expenses") {
            // Guard peran terpusat lewat Permission (fail-closed): PIN aktif + bukan Admin -> ditolak.
            val currentUser by sessionManager.currentUser.collectAsState()
            val allowed = Permission.canAccessExpenses(currentUser, storeProfile.pinLoginEnabled)
            RoleGatedRoute(allowed = allowed, navController = navController) {
                ExpenseScreen(onBack = { navController.popBackStack() })
            }
        }
        composable("stock") { StockScreen(onBack = { navController.popBackStack() }) }
        composable("settings") {
            // Guard peran terpusat lewat Permission (fail-closed): PIN aktif + bukan Admin -> ditolak,
            // menu ini juga sudah disembunyikan di UI Dashboard — ini lapisan pertahanan kedua.
            val currentUser by sessionManager.currentUser.collectAsState()
            val allowed = Permission.canAccessSettings(currentUser, storeProfile.pinLoginEnabled)
            RoleGatedRoute(allowed = allowed, navController = navController) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenStoreProfile = { navController.navigate("store_profile") },
                    onOpenUserManagement = { navController.navigate("user_management") },
                    onOpenExpenses = { navController.navigate("expenses") },
                    onOpenCloudSync = { navController.navigate("cloud_sync") },
                    onOpenMultiOutlet = { navController.navigate("multi_outlet") },
                    onOpenAuditLog = { navController.navigate("audit_log") },
                    onOpenSuppliers = { navController.navigate("suppliers") },
                    onOpenPaymentGateway = { navController.navigate("payment_gateway") },
                    onOpenLicense = { navController.navigate("license_status") },
                    onOpenOutletStockCheck = { navController.navigate("outlet_stock_check") }
                )
            }
        }
        composable("payment_gateway") {
            // Kredensial payment gateway toko sendiri -> setara sensitifnya dengan rute
            // Pengaturan lain (bisa mengubah rekening tujuan uang QRIS masuk), admin-only.
            val currentUser by sessionManager.currentUser.collectAsState()
            val allowed = Permission.canAccessSettings(currentUser, storeProfile.pinLoginEnabled)
            RoleGatedRoute(allowed = allowed, navController = navController) {
                com.example.posapp.presentation.payment.PaymentGatewaySettingsScreen(onBack = { navController.popBackStack() })
            }
        }
        composable("license_status") {
            // Info lisensi boleh dilihat siapa pun yang bisa masuk Pengaturan (bukan rahasia
            // finansial toko), tetap dibungkus guard Pengaturan yang sama untuk konsistensi.
            val currentUser by sessionManager.currentUser.collectAsState()
            val allowed = Permission.canAccessSettings(currentUser, storeProfile.pinLoginEnabled)
            RoleGatedRoute(allowed = allowed, navController = navController) {
                com.example.posapp.presentation.license.LicenseActivationScreen(onActivated = { navController.popBackStack() })
            }
        }
        composable("store_profile") {
            // Sebelumnya route ini TIDAK punya guard sama sekali — hanya "tersembunyi" karena
            // cuma dinavigasi dari dalam SettingsScreen yang sudah digerbang. Itu bukan pertahanan
            // nyata: siapa pun yang bisa memicu navigasi langsung ke "store_profile" (deep link,
            // shortcut, kode baru di masa depan) bisa melewatinya. Sekarang digerbang independen.
            val currentUser by sessionManager.currentUser.collectAsState()
            val allowed = Permission.canManageBackup(currentUser, storeProfile.pinLoginEnabled)
            RoleGatedRoute(allowed = allowed, navController = navController) {
                StoreProfileScreen(onBack = { navController.popBackStack() })
            }
        }
        composable("user_management") {
            // Sama seperti "store_profile" di atas: dulu tanpa guard independen. Manajemen
            // Pengguna & PIN adalah rute paling sensitif di app ini (bisa membuat/menghapus admin),
            // jadi wajib digerbang sendiri, bukan cuma mengandalkan UI SettingsScreen di atasnya.
            val currentUser by sessionManager.currentUser.collectAsState()
            val allowed = Permission.canAccessSettings(currentUser, storeProfile.pinLoginEnabled)
            RoleGatedRoute(allowed = allowed, navController = navController) {
                UserManagementScreen(onBack = { navController.popBackStack() })
            }
        }
        composable("suppliers") {
            // Kelola Pemasok (v13) — admin-only sama seperti rute Pengaturan lain, digerbang
            // independen (bukan cuma tersembunyi di UI SettingsScreen).
            val currentUser by sessionManager.currentUser.collectAsState()
            val allowed = Permission.canAccessSettings(currentUser, storeProfile.pinLoginEnabled)
            RoleGatedRoute(allowed = allowed, navController = navController) {
                com.example.posapp.presentation.settings.SupplierScreen(onBack = { navController.popBackStack() })
            }
        }
        composable("cloud_sync") {
            // Sinkronisasi Cloud (Fase 4) mengubah pengaturan tingkat toko/cabang -> admin-only,
            // sama seperti rute Pengaturan lain.
            val currentUser by sessionManager.currentUser.collectAsState()
            val allowed = Permission.canAccessSettings(currentUser, storeProfile.pinLoginEnabled)
            RoleGatedRoute(allowed = allowed, navController = navController) {
                PremiumFeatureGate(
                    hasPremiumAccess = licenseState.hasPremiumAccess,
                    onActivate = { navController.navigate("license_status") },
                    onBack = { navController.popBackStack() },
                ) {
                    CloudSyncScreen(onBack = { navController.popBackStack() })
                }
            }
        }
        composable("multi_outlet") {
            // Ringkasan lintas cabang ini data tingkat pemilik -> admin-only.
            val currentUser by sessionManager.currentUser.collectAsState()
            val allowed = Permission.canAccessSettings(currentUser, storeProfile.pinLoginEnabled)
            RoleGatedRoute(allowed = allowed, navController = navController) {
                PremiumFeatureGate(
                    hasPremiumAccess = licenseState.hasPremiumAccess,
                    onActivate = { navController.navigate("license_status") },
                    onBack = { navController.popBackStack() },
                ) {
                    MultiOutletDashboardScreen(onBack = { navController.popBackStack() })
                }
            }
        }
        composable("outlet_stock_check") {
            // Cek stok realtime lintas cabang (read-only) -> data tingkat pemilik, admin-only.
            val currentUser by sessionManager.currentUser.collectAsState()
            val allowed = Permission.canAccessSettings(currentUser, storeProfile.pinLoginEnabled)
            RoleGatedRoute(allowed = allowed, navController = navController) {
                PremiumFeatureGate(
                    hasPremiumAccess = licenseState.hasPremiumAccess,
                    onActivate = { navController.navigate("license_status") },
                    onBack = { navController.popBackStack() },
                ) {
                    com.example.posapp.presentation.sync.OutletStockCheckScreen(onBack = { navController.popBackStack() })
                }
            }
        }
        composable("audit_log") {
            // Log Aktivitas berisi jejak Void/Retur/Koreksi transaksi & manajemen Pengguna ->
            // sama sensitifnya dengan rute Pengaturan lain, admin-only.
            val currentUser by sessionManager.currentUser.collectAsState()
            val allowed = Permission.canAccessSettings(currentUser, storeProfile.pinLoginEnabled)
            RoleGatedRoute(allowed = allowed, navController = navController) {
                AuditLogScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

/**
 * Wrapper guard route yang konsisten untuk semua rute admin-only: kalau [allowed] false,
 * langsung mundur ke layar sebelumnya (route ditolak) alih-alih menampilkan kontennya sesaat.
 */
@Composable
private fun RoleGatedRoute(
    allowed: Boolean,
    navController: androidx.navigation.NavHostController,
    content: @Composable () -> Unit
) {
    if (allowed) {
        content()
    } else {
        androidx.compose.runtime.LaunchedEffect(Unit) { navController.popBackStack() }
    }
}
