package com.example.posapp.presentation.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.example.posapp.R

/**
 * Font kustom aplikasi — "Plus Jakarta Sans" (kesan modern/profesional, bukan Roboto
 * bawaan sistem Android yang langsung terlihat generik) — diambil via Downloadable Fonts
 * API Google Play Services, jadi TIDAK perlu membundel file .ttf di dalam APK.
 *
 * Catatan penting: karena diunduh saat runtime, font ini butuh koneksi internet +
 * Google Play Services minimal SEKALI (setelah itu di-cache di perangkat & jalan
 * offline seperti biasa). Selama font belum berhasil diunduh (mis. HP tanpa Play
 * Services / belum pernah online), teks otomatis kembali memakai font sistem default
 * — aplikasi tetap jalan normal, tidak crash.
 */
private val googleFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

private val plusJakartaSans = GoogleFont("Plus Jakarta Sans")

val PosFontFamily = FontFamily(
    Font(googleFont = plusJakartaSans, fontProvider = googleFontProvider, weight = FontWeight.Normal),
    Font(googleFont = plusJakartaSans, fontProvider = googleFontProvider, weight = FontWeight.Medium),
    Font(googleFont = plusJakartaSans, fontProvider = googleFontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = plusJakartaSans, fontProvider = googleFontProvider, weight = FontWeight.Bold)
)
