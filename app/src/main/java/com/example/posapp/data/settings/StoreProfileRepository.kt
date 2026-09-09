package com.example.posapp.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.storeProfileDataStore by preferencesDataStore(name = "store_profile")

/** Profil toko yang dipakai di struk, PDF invoice, dan layar pembayaran (QRIS). */
data class StoreProfile(
    val name: String = "Toko Saya",
    val address: String = "",
    val phone: String = "",
    val receiptFooter: String = "Terima kasih telah berbelanja!",
    val qrisImagePath: String? = null,
    val qrisRawContent: String? = null, // payload EMVCo mentah hasil decode gambar QRIS, dipakai untuk QRIS dinamis
    val pinLoginEnabled: Boolean = false,
    val logoImagePath: String? = null, // logo toko — dipakai di header struk cetak & PDF invoice
    val appColorHex: String? = null, // warna aksen aplikasi custom (mis. "#E8590C"); null = pakai warna default
    val fontChoice: String = "plus_jakarta_sans", // lihat PosFontOption di presentation/theme/Font.kt
    val receiptLanguage: String = "id", // bahasa struk & invoice PDF: "id" (Indonesia) atau "en" (English)
    val taxEnabled: Boolean = true, // matikan untuk toko yang tidak memungut pajak/PPN
    val taxPercent: Double = 11.0, // persentase pajak/PPN yang dipakai saat taxEnabled = true
    val quickCashAmounts: String = "20000,50000,100000,150000,200000", // nominal cepat Cash, dipisah koma; lihat quickCashAmountList()
    /** Menit tanpa aktivitas sebelum sesi kasir/admin otomatis logout (minta PIN lagi).
     * 0 = mati (tidak ada auto-lock idle). Hanya berlaku efektif saat [pinLoginEnabled] aktif —
     * lihat AutoLockManager. Terpisah dari kunci-saat-app-di-background yang SELALU aktif
     * ketika PIN login aktif (tidak bisa dimatikan, karena itu risiko keamanan fisik utama). */
    val autoLockMinutes: Int = 5,
    /** ID stabil unik per instalasi (dibuat sekali otomatis), dipakai sebagai kunci dokumen
     * cabang ini di Firestore saat Sinkronisasi Cloud aktif. Lihat CloudSyncRepository. */
    val outletId: String = "",
    /** Nama cabang yang tampil di Ringkasan Semua Cabang (mis. "Cabang Kelapa Gading"). */
    val outletName: String = "Cabang Utama",
    /** Aktifkan pengiriman ringkasan omzet harian ke Firestore untuk digabung lintas cabang.
     * Nonaktif secara default — fitur ini butuh proyek Firebase sendiri, lihat FIREBASE_SETUP.md. */
    val cloudSyncEnabled: Boolean = false,
    /** Nama printer Bluetooth (dari daftar perangkat ter-pairing) yang dipilih untuk cetak
     * struk. Null = pakai printer ter-pairing pertama yang ditemukan (perilaku lama, dipakai
     * kalau toko hanya punya satu printer atau belum pernah memilih). Lihat PrinterRepository. */
    val selectedPrinterName: String? = null,
    /** Jenis koneksi printer: "BLUETOOTH", "LAN", atau "USB" — lihat PrinterConnectionType. */
    val printerConnectionType: String = "BLUETOOTH",
    /** Alamat IP printer thermal jaringan/WiFi (hanya dipakai kalau printerConnectionType=LAN). */
    val printerLanIp: String = "",
    /** Port TCP printer LAN — standar hampir semua printer thermal jaringan adalah 9100. */
    val printerLanPort: Int = 9100,
    /** Lebar kertas struk dalam mm — 48 untuk printer 58mm (umum), 72 untuk printer 80mm. */
    val printerPaperWidthMm: Float = 48f,
    /** Tipe bisnis toko untuk menyesuaikan fitur relevan yang ditampilkan: RETAIL, FNB
     * (restoran/kafe — menambah tag nomor meja/pesanan di kasir), atau GENERAL (netral). */
    val businessType: String = "GENERAL",
    /** Aktifkan program poin loyalitas pelanggan. Nonaktif default agar tidak menambah
     * elemen UI untuk toko yang tidak butuh. */
    val loyaltyEnabled: Boolean = false,
    /** Rp dibelanjakan untuk mendapat 1 poin (mis. 10000 = tiap Rp10.000 belanja = 1 poin). */
    val loyaltyRupiahPerPoint: Long = 10000,
    /** Nilai tukar 1 poin dalam Rupiah saat dipakai sebagai potongan pembayaran. */
    val loyaltyPointValueRupiah: Long = 100,
    /** Tampilkan tombol "Kirim Struk via WhatsApp" setelah transaksi. */
    val whatsappReceiptEnabled: Boolean = true,
    /** Aktifkan input nomor meja/nama pemesan di kasir — relevan untuk mode Restoran/Kafe. */
    val tableTaggingEnabled: Boolean = false,
    /** Aktifkan modul Pemasok & draf Pesanan Pembelian dari daftar stok tipis. */
    val supplierPoEnabled: Boolean = false,
    /** Sudah menyelesaikan (atau melewati) Onboarding Wizard saat instal pertama kali —
     * dipakai MainActivity untuk menampilkan wizard itu HANYA sekali seumur instalasi.
     * Default false supaya instalasi baru (DataStore masih kosong) selalu melihatnya. */
    val onboardingCompleted: Boolean = false
)

/**
 * Parse [StoreProfile.quickCashAmounts] ("20000,50000,...") jadi list nominal siap pakai untuk
 * tombol cepat di layar Pembayaran. Nilai tidak valid/duplikat/≤0 otomatis dibuang & diurutkan naik.
 */
fun StoreProfile.quickCashAmountList(): List<Long> =
    quickCashAmounts.split(",")
        .mapNotNull { it.trim().toLongOrNull() }
        .filter { it > 0 }
        .distinct()
        .sorted()

@Singleton
class StoreProfileRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val NAME = stringPreferencesKey("store_name")
        val ADDRESS = stringPreferencesKey("store_address")
        val PHONE = stringPreferencesKey("store_phone")
        val FOOTER = stringPreferencesKey("receipt_footer")
        val QRIS_PATH = stringPreferencesKey("qris_image_path")
        val QRIS_RAW_CONTENT = stringPreferencesKey("qris_raw_content")
        val PIN_LOGIN_ENABLED = booleanPreferencesKey("pin_login_enabled")
        val LOGO_PATH = stringPreferencesKey("store_logo_path")
        val APP_COLOR_HEX = stringPreferencesKey("app_color_hex")
        val FONT_CHOICE = stringPreferencesKey("font_choice")
        val RECEIPT_LANGUAGE = stringPreferencesKey("receipt_language")
        val TAX_ENABLED = booleanPreferencesKey("tax_enabled")
        val TAX_PERCENT = doublePreferencesKey("tax_percent")
        val QUICK_CASH_AMOUNTS = stringPreferencesKey("quick_cash_amounts")
        val AUTO_LOCK_MINUTES = androidx.datastore.preferences.core.intPreferencesKey("auto_lock_minutes")
        val OUTLET_ID = stringPreferencesKey("outlet_id")
        val OUTLET_NAME = stringPreferencesKey("outlet_name")
        val CLOUD_SYNC_ENABLED = booleanPreferencesKey("cloud_sync_enabled")
        val SELECTED_PRINTER_NAME = stringPreferencesKey("selected_printer_name")
        val PRINTER_CONNECTION_TYPE = stringPreferencesKey("printer_connection_type")
        val PRINTER_LAN_IP = stringPreferencesKey("printer_lan_ip")
        val PRINTER_LAN_PORT = androidx.datastore.preferences.core.intPreferencesKey("printer_lan_port")
        val PRINTER_PAPER_WIDTH_MM = androidx.datastore.preferences.core.floatPreferencesKey("printer_paper_width_mm")
        val BUSINESS_TYPE = stringPreferencesKey("business_type")
        val LOYALTY_ENABLED = booleanPreferencesKey("loyalty_enabled")
        val LOYALTY_RUPIAH_PER_POINT = androidx.datastore.preferences.core.longPreferencesKey("loyalty_rupiah_per_point")
        val LOYALTY_POINT_VALUE_RUPIAH = androidx.datastore.preferences.core.longPreferencesKey("loyalty_point_value_rupiah")
        val WHATSAPP_RECEIPT_ENABLED = booleanPreferencesKey("whatsapp_receipt_enabled")
        val TABLE_TAGGING_ENABLED = booleanPreferencesKey("table_tagging_enabled")
        val SUPPLIER_PO_ENABLED = booleanPreferencesKey("supplier_po_enabled")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }

    val profile: Flow<StoreProfile> = context.storeProfileDataStore.data.map { prefs ->
        StoreProfile(
            name = prefs[Keys.NAME] ?: "Toko Saya",
            address = prefs[Keys.ADDRESS] ?: "",
            phone = prefs[Keys.PHONE] ?: "",
            receiptFooter = prefs[Keys.FOOTER] ?: "Terima kasih telah berbelanja!",
            qrisImagePath = prefs[Keys.QRIS_PATH],
            qrisRawContent = prefs[Keys.QRIS_RAW_CONTENT],
            pinLoginEnabled = prefs[Keys.PIN_LOGIN_ENABLED] ?: false,
            logoImagePath = prefs[Keys.LOGO_PATH],
            appColorHex = prefs[Keys.APP_COLOR_HEX],
            fontChoice = prefs[Keys.FONT_CHOICE] ?: "plus_jakarta_sans",
            receiptLanguage = prefs[Keys.RECEIPT_LANGUAGE] ?: "id",
            taxEnabled = prefs[Keys.TAX_ENABLED] ?: true,
            taxPercent = prefs[Keys.TAX_PERCENT] ?: 11.0,
            quickCashAmounts = prefs[Keys.QUICK_CASH_AMOUNTS] ?: "20000,50000,100000,150000,200000",
            autoLockMinutes = prefs[Keys.AUTO_LOCK_MINUTES] ?: 5,
            outletId = prefs[Keys.OUTLET_ID] ?: "",
            outletName = prefs[Keys.OUTLET_NAME] ?: "Cabang Utama",
            cloudSyncEnabled = prefs[Keys.CLOUD_SYNC_ENABLED] ?: false,
            selectedPrinterName = prefs[Keys.SELECTED_PRINTER_NAME],
            printerConnectionType = prefs[Keys.PRINTER_CONNECTION_TYPE] ?: "BLUETOOTH",
            printerLanIp = prefs[Keys.PRINTER_LAN_IP] ?: "",
            printerLanPort = prefs[Keys.PRINTER_LAN_PORT] ?: 9100,
            printerPaperWidthMm = prefs[Keys.PRINTER_PAPER_WIDTH_MM] ?: 48f,
            businessType = prefs[Keys.BUSINESS_TYPE] ?: "GENERAL",
            loyaltyEnabled = prefs[Keys.LOYALTY_ENABLED] ?: false,
            loyaltyRupiahPerPoint = prefs[Keys.LOYALTY_RUPIAH_PER_POINT] ?: 10000L,
            loyaltyPointValueRupiah = prefs[Keys.LOYALTY_POINT_VALUE_RUPIAH] ?: 100L,
            whatsappReceiptEnabled = prefs[Keys.WHATSAPP_RECEIPT_ENABLED] ?: true,
            tableTaggingEnabled = prefs[Keys.TABLE_TAGGING_ENABLED] ?: false,
            supplierPoEnabled = prefs[Keys.SUPPLIER_PO_ENABLED] ?: false,
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false
        )
    }

    suspend fun update(
        name: String,
        address: String,
        phone: String,
        receiptFooter: String
    ) {
        context.storeProfileDataStore.edit { prefs ->
            prefs[Keys.NAME] = name
            prefs[Keys.ADDRESS] = address
            prefs[Keys.PHONE] = phone
            prefs[Keys.FOOTER] = receiptFooter
        }
    }

    suspend fun updateQrisImagePath(path: String?) {
        context.storeProfileDataStore.edit { prefs ->
            if (path == null) prefs.remove(Keys.QRIS_PATH) else prefs[Keys.QRIS_PATH] = path
        }
    }

    suspend fun updateQrisRawContent(content: String?) {
        context.storeProfileDataStore.edit { prefs ->
            if (content == null) prefs.remove(Keys.QRIS_RAW_CONTENT) else prefs[Keys.QRIS_RAW_CONTENT] = content
        }
    }

    suspend fun setPinLoginEnabled(enabled: Boolean) {
        context.storeProfileDataStore.edit { prefs -> prefs[Keys.PIN_LOGIN_ENABLED] = enabled }
    }

    suspend fun updateLogoImagePath(path: String?) {
        context.storeProfileDataStore.edit { prefs ->
            if (path == null) prefs.remove(Keys.LOGO_PATH) else prefs[Keys.LOGO_PATH] = path
        }
    }

    /** @param hex Format "#RRGGBB", atau null untuk kembali ke warna default aplikasi. */
    suspend fun updateAppColorHex(hex: String?) {
        context.storeProfileDataStore.edit { prefs ->
            if (hex == null) prefs.remove(Keys.APP_COLOR_HEX) else prefs[Keys.APP_COLOR_HEX] = hex
        }
    }

    /** @param fontKey salah satu key dari PosFontOption.entries (mis. "plus_jakarta_sans", "inter", "poppins"). */
    suspend fun updateFontChoice(fontKey: String) {
        context.storeProfileDataStore.edit { prefs -> prefs[Keys.FONT_CHOICE] = fontKey }
    }

    /** @param languageCode "id" (Indonesia) atau "en" (English) — dipakai di struk cetak & PDF invoice. */
    suspend fun updateReceiptLanguage(languageCode: String) {
        context.storeProfileDataStore.edit { prefs -> prefs[Keys.RECEIPT_LANGUAGE] = languageCode }
    }

    /** Aktifkan/nonaktifkan pajak & atur persentasenya (mis. PPN 11%, atau 0 untuk toko non-PKP). */
    suspend fun updateTaxSettings(enabled: Boolean, percent: Double) {
        context.storeProfileDataStore.edit { prefs ->
            prefs[Keys.TAX_ENABLED] = enabled
            prefs[Keys.TAX_PERCENT] = percent
        }
    }

    /** @param amounts Nominal cepat Cash dipisah koma (mis. "20000,50000,100000"), lihat quickCashAmountList(). */
    suspend fun updateQuickCashAmounts(amounts: String) {
        context.storeProfileDataStore.edit { prefs -> prefs[Keys.QUICK_CASH_AMOUNTS] = amounts }
    }

    /** @param minutes 0 untuk mematikan auto-lock idle; nilai umum: 1, 5, 15, 30. */
    suspend fun updateAutoLockMinutes(minutes: Int) {
        context.storeProfileDataStore.edit { prefs -> prefs[Keys.AUTO_LOCK_MINUTES] = minutes.coerceAtLeast(0) }
    }

    /**
     * Pastikan device ini punya outletId stabil (dibuat sekali, dipakai selamanya sebagai kunci
     * dokumen cabang di Firestore). Aman dipanggil berkali-kali — hanya generate sekali lalu
     * dipakai ulang dari DataStore setelahnya.
     */
    suspend fun ensureOutletId(): String {
        val current = context.storeProfileDataStore.data.map { it[Keys.OUTLET_ID] }.first()
        if (!current.isNullOrBlank()) return current
        val newId = java.util.UUID.randomUUID().toString()
        context.storeProfileDataStore.edit { prefs -> prefs[Keys.OUTLET_ID] = newId }
        return newId
    }

    suspend fun updateOutletName(name: String) {
        context.storeProfileDataStore.edit { prefs -> prefs[Keys.OUTLET_NAME] = name.trim().ifBlank { "Cabang Utama" } }
    }

    suspend fun setCloudSyncEnabled(enabled: Boolean) {
        context.storeProfileDataStore.edit { prefs -> prefs[Keys.CLOUD_SYNC_ENABLED] = enabled }
    }

    /** @param name Nama printer Bluetooth persis seperti tampil di [PrinterRepository.listPairedPrinters],
     * atau null untuk kembali ke perilaku default (pakai printer ter-pairing pertama). */
    suspend fun setSelectedPrinterName(name: String?) {
        context.storeProfileDataStore.edit { prefs ->
            if (name == null) prefs.remove(Keys.SELECTED_PRINTER_NAME) else prefs[Keys.SELECTED_PRINTER_NAME] = name
        }
    }

    /** @param type "BLUETOOTH", "LAN", atau "USB" — lihat PrinterConnectionType. */
    suspend fun setPrinterConfig(type: String, lanIp: String, lanPort: Int, paperWidthMm: Float) {
        context.storeProfileDataStore.edit { prefs ->
            prefs[Keys.PRINTER_CONNECTION_TYPE] = type
            prefs[Keys.PRINTER_LAN_IP] = lanIp
            prefs[Keys.PRINTER_LAN_PORT] = lanPort
            prefs[Keys.PRINTER_PAPER_WIDTH_MM] = paperWidthMm
        }
    }

    /** @param type "RETAIL", "FNB", atau "GENERAL". */
    suspend fun updateBusinessType(type: String) {
        context.storeProfileDataStore.edit { prefs -> prefs[Keys.BUSINESS_TYPE] = type }
    }

    /** Aktifkan/nonaktifkan program poin loyalitas & atur nilai tukarnya. */
    suspend fun updateLoyaltySettings(enabled: Boolean, rupiahPerPoint: Long, pointValueRupiah: Long) {
        context.storeProfileDataStore.edit { prefs ->
            prefs[Keys.LOYALTY_ENABLED] = enabled
            prefs[Keys.LOYALTY_RUPIAH_PER_POINT] = rupiahPerPoint.coerceAtLeast(1)
            prefs[Keys.LOYALTY_POINT_VALUE_RUPIAH] = pointValueRupiah.coerceAtLeast(1)
        }
    }

    suspend fun setWhatsappReceiptEnabled(enabled: Boolean) {
        context.storeProfileDataStore.edit { prefs -> prefs[Keys.WHATSAPP_RECEIPT_ENABLED] = enabled }
    }

    suspend fun setTableTaggingEnabled(enabled: Boolean) {
        context.storeProfileDataStore.edit { prefs -> prefs[Keys.TABLE_TAGGING_ENABLED] = enabled }
    }

    suspend fun setSupplierPoEnabled(enabled: Boolean) {
        context.storeProfileDataStore.edit { prefs -> prefs[Keys.SUPPLIER_PO_ENABLED] = enabled }
    }

    /**
     * Simpan seluruh pilihan Onboarding Wizard (Data Toko, Warna Brand, Jenis Usaha, Pajak)
     * dalam SATU transaksi DataStore, lalu tandai [onboardingCompleted] = true supaya wizard
     * tidak muncul lagi di sesi berikutnya. Dipanggil dari langkah terakhir wizard ("Selesai").
     */
    suspend fun completeOnboarding(
        name: String,
        address: String,
        phone: String,
        businessType: String,
        appColorHex: String?,
        taxEnabled: Boolean,
        taxPercent: Double
    ) {
        context.storeProfileDataStore.edit { prefs ->
            prefs[Keys.NAME] = name.trim().ifBlank { "Toko Saya" }
            prefs[Keys.ADDRESS] = address
            prefs[Keys.PHONE] = phone
            prefs[Keys.BUSINESS_TYPE] = businessType
            if (appColorHex == null) prefs.remove(Keys.APP_COLOR_HEX) else prefs[Keys.APP_COLOR_HEX] = appColorHex
            prefs[Keys.TAX_ENABLED] = taxEnabled
            prefs[Keys.TAX_PERCENT] = taxPercent
            prefs[Keys.ONBOARDING_COMPLETED] = true
        }
    }

    /** Lewati wizard tanpa mengubah profil toko apa pun — tetap ditandai selesai supaya tidak
     * muncul lagi tiap kali app dibuka. */
    suspend fun skipOnboarding() {
        context.storeProfileDataStore.edit { prefs -> prefs[Keys.ONBOARDING_COMPLETED] = true }
    }
}
