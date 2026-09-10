package com.example.posapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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

/**
 * ID acak yang dibuat SEKALI per PROSES (bukan per Activity) -- `object` di Kotlin/JVM baru
 * di-load ulang saat proses baru benar-benar dibuat, jadi `id` ini otomatis berbeda setiap kali
 * OS mematikan proses lalu membuat proses baru (umum di HP dengan app-killer agresif seperti
 * Vivo/FuntouchOS, Xiaomi/MIUI, Oppo/ColorOS -- TIDAK harus karena RAM penuh), tapi TETAP SAMA
 * selama proses masih hidup (termasuk saat Activity di-destroy-lalu-recreate akibat rotasi
 * layar/perubahan konfigurasi biasa).
 *
 * PERBAIKAN BUG: layar putih ("blank") yang muncul sesaat setelah auto-lock melempar ke halaman
 * Login, lalu macet total sampai app ditutup paksa. Akar masalahnya BUKAN di AutoLockManager,
 * tapi di NavController (Navigation-Compose): `rememberNavController()` otomatis MENYIMPAN &
 * MEMULIHKAN seluruh back stack navigasi lewat savedInstanceState Activity. Saat OS membunuh
 * proses app di background (kasus PALING SERING di Vivo dkk, bahkan dengan RAM masih banyak
 * kosong -- ini kebijakan battery-saver pabrikan, bukan soal memori), lalu pengguna kembali:
 * Activity dibuat ulang, tapi back stack navigasi ikut dipulihkan APA ADANYA ke layar SEBELUM
 * di-kill (mis. "pos"/"dashboard") -- padahal SessionManager & data profil toko yang baru mulai
 * dari nol (kosong/default) karena Hilt & seluruh proses memang baru. Layar lama itu sempat
 * ter-render dulu (dengan ViewModel-nya masing-masing ikut jalan dari nol) sebelum sempat
 * dikoreksi ke Login oleh redirect effect di [PosNavHost] -- proses render+inisialisasi ulang
 * yang tumpang tindih inilah yang bikin macet.
 *
 * Dengan membungkus [PosNavHost] di dalam `key(ProcessSession.id)`, setiap kali proses BENAR-
 * BENAR baru, Compose memperlakukan seluruh subtree navigasi sebagai instance BARU (slot
 * composition berbeda) -- back stack lama dari savedInstanceState TIDAK IKUT dipulihkan, NavHost
 * selalu mulai bersih dari `login_gate` seperti cold-start murni. Rotasi layar/perubahan
 * konfigurasi biasa (proses TETAP hidup) tidak terpengaruh sama sekali -- back stack & state
 * layar pengguna (mis. sedang mengisi form Produk) tetap dipulihkan seperti biasa.
 */
private object ProcessSession {
    val id: String = java.util.UUID.randomUUID().toString()
}

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
                            // key(ProcessSession.id) -- lihat dokumentasi ProcessSession di atas:
                            // ini yang mencegah back stack "basi" dari proses sebelumnya ikut
                            // dipulihkan saat proses app baru dibuat ulang oleh OS.
                            key(ProcessSession.id) {
                                PosNavHost(sessionManager = sessionManager, autoLockManager = autoLockManager)
                            }
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
    //
    // PERBAIKAN BUG: onStop() JUGA terpicu saat Activity di-destroy-lalu-recreate akibat
    // perubahan KONFIGURASI (rotasi layar landscape<->portrait, resize multi-window/foldable,
    // perubahan bahasa sistem, dsb) -- bukan cuma saat pengguna sungguh-sungguh meninggalkan
    // app. Tanpa pengecekan ini, terlihat identik dengan Home/app-switch di mata
    // AutoLockManager: kasir yang cuma memutar HP-nya langsung ke-logout paksa dan lempar ke
    // layar Login, walau tidak pernah pindah ke mana-mana. isChangingConfigurations() true
    // berarti onStop ini bagian dari siklus rotasi (Activity yang SAMA akan langsung
    // di-recreate), jadi dilewati -- tidak dianggap "app di-background".
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            autoLockManager.onAppBackgrounded()
        }
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
    val isProfileLoaded by storeViewModel.isLoaded.collectAsState()
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
    //
    // PERBAIKAN BUG: storeProfile WAJIB jadi key di sini, bukan cuma dibaca di dalam body.
    // Skenario yang sebelumnya lolos: proses app dibunuh OS saat di-background (umum di HP
    // RAM kecil) lalu dibuka lagi -> SessionManager baru (currentUser = null) dan
    // StoreProfileViewModel.uiState mulai dari nilai default StoreProfile() (pinLoginEnabled
    // = false) SEBELUM nilai asli dari DataStore selesai dimuat -- sementara back stack
    // Navigation-Compose (mis. masih di layar Kasir/Produk) ikut ter-restore dari Bundle, jadi
    // BUKAN mulai dari login_gate. Kalau storeProfile bukan key, LaunchedEffect ini sempat
    // jalan sekali saat pinLoginEnabled masih default false (tidak redirect), lalu TIDAK
    // PERNAH jalan ulang ketika nilai asli (true) selesai dimuat -- karena currentUser &
    // currentBackStackEntry tidak ikut berubah. Hasilnya: layar lama tetap tampil dengan sesi
    // null tanpa pernah diarahkan ke Login, ViewModel-nya pun jalan tanpa data sesi -> layar
    // kosong/putih. Dengan storeProfile sebagai key, begitu nilai asli pinLoginEnabled masuk,
    // effect ini otomatis jalan ulang dan langsung redirect.
    LaunchedEffect(currentUser, currentBackStackEntry, storeProfile) {
        val route = currentBackStackEntry?.destination?.route
        val onAuthScreen = route == "login" || route == "login_gate" || route == "onboarding" || route == null
        if (currentUser == null && storeProfile.pinLoginEnabled && !onAuthScreen) {
            navController.navigate("login") {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
    }

    // PERBAIKAN BUG (layar putih macet & tidak responsif setelah auto-lock -- paling sering di
    // HP yang agresif membunuh proses aplikasi latar belakang seperti Vivo/FuntouchOS, Xiaomi/
    // MIUI, Oppo/ColorOS, dll, walau RAM masih longgar): saat OS membunuh proses lalu app dibuka
    // lagi, Navigation-Compose me-restore back stack LAMA (mis. masih di "dashboard"/"pos")
    // sebelum SessionManager (baru, currentUser = null) dan StoreProfileViewModel (baru mulai
    // baca DataStore) sempat dapat nilai aslinya. Tanpa gerbang ini, konten ASLI rute yang
    // ter-restore itu -- termasuk ViewModel beratnya (query Room, listener Firestore Cloud Sync,
    // decode gambar, dsb, semuanya lewat hiltViewModel() di dalam body composable-nya) -- sempat
    // mulai jalan di frame pertama, TEPAT bersamaan dengan LaunchedEffect di atas yang mencoba
    // redirect ke Login. Login sempat kelihatan sekilas, lalu macet putih.
    //
    // [AuthGatedRoute] mencegah ini: konten asli rute terproteksi (dan ViewModel-nya) SAMA
    // SEKALI TIDAK dibuat selama sesi belum dipastikan valid -- cukup dikosongkan sesaat,
    // menunggu LaunchedEffect di atas selesai redirect ke Login. Fail-closed: selama profil toko
    // BELUM selesai dimuat ([isProfileLoaded] masih false), anggap dulu perlu login (lebih aman
    // daripada asumsi "tidak perlu login" yang bisa salah).
    fun isAuthGateBlocking(): Boolean =
        !isProfileLoaded || (storeProfile.pinLoginEnabled && currentUser == null)

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
            AuthGatedRoute(blocking = isAuthGateBlocking()) {
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
        }
        composable("pos") { backStackEntry ->
            val scannedSku = backStackEntry.savedStateHandle
                .getStateFlow<String?>("scanned_sku", null)
                .collectAsState()
            AuthGatedRoute(blocking = isAuthGateBlocking()) {
                // Gerbang shift: kalau "Wajibkan Login PIN" aktif (mode multi-kasir), transaksi
                // tidak boleh berjalan tanpa shift terbuka -- supaya kas tunai selalu bisa
                // direkonsiliasi ke shift & kasir yang jelas. Mode single-user (PIN nonaktif)
                // tidak digerbang: toko kecil yang tidak butuh disiplin shift tidak dipaksa
                // memakainya.
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
        }
        composable("shift") {
            AuthGatedRoute(blocking = isAuthGateBlocking()) {
                ShiftScreen(
                    onBack = { navController.popBackStack() },
                    onShiftOpened = { }
                )
            }
        }
        composable("customers") {
            AuthGatedRoute(blocking = isAuthGateBlocking()) {
                CustomerScreen(
                    onBack = { navController.popBackStack() },
                    onOpenDetail = { customerId -> navController.navigate("customer_detail/$customerId") }
                )
            }
        }
        composable(
            route = "customer_detail/{customerId}",
            arguments = listOf(navArgument("customerId") { type = NavType.LongType })
        ) {
            AuthGatedRoute(blocking = isAuthGateBlocking()) {
                CustomerDetailScreen(onBack = { navController.popBackStack() })
            }
        }
        composable("products") {
            AuthGatedRoute(blocking = isAuthGateBlocking()) {
                // Manajemen Produk (termasuk harga beli/margin) — ADMIN & MANAGER, KASIR ditolak.
                // Sebelumnya rute ini tidak digerbang sama sekali (audit 2026-09-06).
                val currentUser by sessionManager.currentUser.collectAsState()
                val allowed = Permission.canManageProducts(currentUser, storeProfile.pinLoginEnabled)
                RoleGatedRoute(allowed = allowed, navController = navController) {
                    ProductScreen(onBack = { navController.popBackStack() })
                }
            }
        }
        composable("scanner") {
            AuthGatedRoute(blocking = isAuthGateBlocking()) {
                BarcodeScannerScreen(
                    onBarcodeDetected = { sku ->
                        navController.previousBackStackEntry?.savedStateHandle?.set("scanned_sku", sku)
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable("reports") {
            AuthGatedRoute(blocking = isAuthGateBlocking()) {
                ReportScreen(
                    onBack = { navController.popBackStack() },
                    onOpenExpenses = { navController.navigate("expenses") }
                )
            }
        }
        composable("expenses") {
            AuthGatedRoute(blocking = isAuthGateBlocking()) {
                // Guard peran terpusat lewat Permission (fail-closed): PIN aktif + bukan Admin -> ditolak.
                val currentUser by sessionManager.currentUser.collectAsState()
                val allowed = Permission.canAccessExpenses(currentUser, storeProfile.pinLoginEnabled)
                RoleGatedRoute(allowed = allowed, navController = navController) {
                    ExpenseScreen(onBack = { navController.popBackStack() })
                }
            }
        }
        composable("stock") {
            AuthGatedRoute(blocking = isAuthGateBlocking()) {
                StockScreen(onBack = { navController.popBackStack() })
            }
        }
        composable("settings") {
            AuthGatedRoute(blocking = isAuthGateBlocking()) {
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
        }
        composable("payment_gateway") {
            AuthGatedRoute(blocking = isAuthGateBlocking()) {
                // Kredensial payment gateway toko sendiri -> setara sensitifnya dengan rute
                // Pengaturan lain (bisa mengubah rekening tujuan uang QRIS masuk), admin-only.
                val currentUser by sessionManager.currentUser.collectAsState()
                val allowed = Permission.canAccessSettings(currentUser, storeProfile.pinLoginEnabled)
                RoleGatedRoute(allowed = allowed, navController = navController) {
                    com.example.posapp.presentation.payment.PaymentGatewaySettingsScreen(onBack = { navController.popBackStack() })
                }
            }
        }
        composable("license_status") {
            AuthGatedRoute(blocking = isAuthGateBlocking()) {
                // Info lisensi boleh dilihat siapa pun yang bisa masuk Pengaturan (bukan rahasia
                // finansial toko), tetap dibungkus guard Pengaturan yang sama untuk konsistensi.
                val currentUser by sessionManager.currentUser.collectAsState()
                val allowed = Permission.canAccessSettings(currentUser, storeProfile.pinLoginEnabled)
                RoleGatedRoute(allowed = allowed, navController = navController) {
                    com.example.posapp.presentation.license.LicenseActivationScreen(onActivated = { navController.popBackStack() })
                }
            }
        }
        composable("store_profile") {
            AuthGatedRoute(blocking = isAuthGateBlocking()) {
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
        }
        composable("user_management") {
            AuthGatedRoute(blocking = isAuthGateBlocking()) {
                // Sama seperti "store_profile" di atas: dulu tanpa guard independen. Manajemen
                // Pengguna & PIN adalah rute paling sensitif di app ini (bisa membuat/menghapus admin),
                // jadi wajib digerbang sendiri, bukan cuma mengandalkan UI SettingsScreen di atasnya.
                val currentUser by sessionManager.currentUser.collectAsState()
                val allowed = Permission.canAccessSettings(currentUser, storeProfile.pinLoginEnabled)
                RoleGatedRoute(allowed = allowed, navController = navController) {
                    UserManagementScreen(onBack = { navController.popBackStack() })
                }
            }
        }
        composable("suppliers") {
            AuthGatedRoute(blocking = isAuthGateBlocking()) {
                // Kelola Pemasok (v13) — admin-only sama seperti rute Pengaturan lain, digerbang
                // independen (bukan cuma tersembunyi di UI SettingsScreen).
                val currentUser by sessionManager.currentUser.collectAsState()
                val allowed = Permission.canAccessSettings(currentUser, storeProfile.pinLoginEnabled)
                RoleGatedRoute(allowed = allowed, navController = navController) {
                    com.example.posapp.presentation.settings.SupplierScreen(onBack = { navController.popBackStack() })
                }
            }
        }
        composable("cloud_sync") {
            AuthGatedRoute(blocking = isAuthGateBlocking()) {
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
        }
        composable("multi_outlet") {
            AuthGatedRoute(blocking = isAuthGateBlocking()) {
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
        }
        composable("outlet_stock_check") {
            AuthGatedRoute(blocking = isAuthGateBlocking()) {
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
        }
        composable("audit_log") {
            AuthGatedRoute(blocking = isAuthGateBlocking()) {
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
}

/**
 * Wrapper guard route yang konsisten untuk semua rute admin-only: kalau [allowed] false,
 * langsung mundur ke layar sebelumnya (route ditolak) alih-alih menampilkan kontennya sesaat.
 */
/**
 * Menggerbang SEMUA rute yang butuh sesi valid (semua rute selain "login_gate"/"login"/
 * "onboarding"). Selama [blocking] true, konten ASLI rute -- dan seluruh ViewModel beratnya yang
 * dibuat lewat hiltViewModel() di dalam [content] (query Room, listener Firestore Cloud Sync,
 * dll) -- SAMA SEKALI TIDAK dibuat. Sengaja dikosongkan total (bukan spinner) sesaat, sambil
 * menunggu redirect ke Login selesai lewat LaunchedEffect(currentUser, currentBackStackEntry,
 * storeProfile) di [PosNavHost].
 *
 * PERBAIKAN BUG: ini bagian [AuthGatedRoute] yang sebelumnya sudah DISEBUT di komentar &
 * fungsi [isAuthGateBlocking] sudah dibuat, tapi keduanya TIDAK PERNAH benar-benar dipasang di
 * rute manapun -- proteksinya tidak pernah aktif. Tanpa ini, saat proses aplikasi dibuat ulang
 * OS (auto-lock/app-switch di HP dengan battery-saver agresif seperti Vivo/FuntouchOS -- BUKAN
 * soal RAM penuh) dan Navigation-Compose memulihkan back stack lama (mis. masih di "pos"),
 * konten asli rute itu sempat mulai jalan dengan sesi/profil yang belum tentu valid TEPAT saat
 * redirect ke Login berjalan -- dua proses render yang tumpang tindih itulah yang membuat
 * layar macet putih. Dengan gerbang ini, konten asli rute baru dibuat SETELAH dipastikan aman.
 */
@Composable
private fun AuthGatedRoute(blocking: Boolean, content: @Composable () -> Unit) {
    if (!blocking) {
        content()
    }
}

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
