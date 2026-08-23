package com.example.posapp.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
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
    val taxPercent: Double = 11.0 // persentase pajak/PPN yang dipakai saat taxEnabled = true
)

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
            taxPercent = prefs[Keys.TAX_PERCENT] ?: 11.0
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
}
