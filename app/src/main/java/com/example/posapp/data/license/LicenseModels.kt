package com.example.posapp.data.license

/**
 * Status lisensi aplikasi. Dihitung LOKAL (tanpa perlu koneksi internet setiap kali dicek) dari
 * token/sertifikat tersimpan di device, hasil verifikasi tanda tangan server saat aktivasi —
 * lihat [LicenseCrypto] & [LicenseRepository].
 *
 * LISENSI INI SEKALI BAYAR, BUKAN LANGGANAN: begitu [ACTIVE], statusnya TIDAK PERNAH kedaluwarsa
 * karena waktu — tidak ada tanggal "berlaku sampai" sama sekali (lihat [LicensePayload], tidak
 * ada field validUntil). Device boleh offline selamanya sejak aktivasi dan lisensinya tetap
 * berlaku. Satu-satunya jalan status berubah dari ACTIVE adalah developer/penjual menonaktifkan
 * lisensinya secara eksplisit (refund, chargeback, terbukti bajakan, dst.) — dan itu pun baru
 * diketahui device saat KEBETULAN online (lihat [LicenseRepository.checkStatus] &
 * [LicenseSyncWorker]), tidak pernah dipaksakan.
 *
 * PENTING — lisensi TIDAK PERNAH memblokir seluruh aplikasi. Transaksi harian (Kasir, Produk,
 * Stok, Laporan, dst.) selalu bisa dipakai berapa pun status di bawah ini, supaya toko baru bisa
 * merasakan dulu fiturnya sebelum memutuskan aktivasi. Yang digerbang HANYA fitur prioritas
 * (QRIS Otomatis, Sinkronisasi Cloud, Cek Stok Lintas Cabang) — lihat [LicenseState.hasPremiumAccess]
 * dan pemakaiannya di `MainActivity.kt` (`PremiumFeatureGate`) & `PosScreen.kt`.
 */
enum class LicenseStatus {
    /** Belum pernah dibuka sama sekali / status awal transien sebelum DataStore sempat terbaca. */
    NOT_ACTIVATED,
    /** Belum aktivasi, tapi masih dalam [LicenseRepository.TRIAL_PERIOD_MILLIS] sejak pertama
     * kali app dibuka di device ini — SEMUA fitur (termasuk fitur prioritas) jalan penuh supaya
     * pengguna bisa mencoba dulu sebelum membeli. */
    TRIAL,
    /** Belum aktivasi DAN masa coba sudah lewat — aplikasi tetap jalan normal untuk transaksi
     * harian, hanya fitur prioritas yang terkunci sampai lisensi diaktivasi. */
    TRIAL_EXPIRED,
    /** Sertifikat aktivasi valid — lisensi PERMANEN untuk device ini (sekali bayar, tanpa masa
     * berlaku), semua fitur (termasuk fitur prioritas) jalan normal SELAMANYA sampai/kecuali
     * dinonaktifkan penjual (lihat [REVOKED]). */
    ACTIVE,
    /** Lisensi yang tadinya aktif sudah dinonaktifkan penjual secara eksplisit (baru diketahui
     * saat device kebetulan online, lihat [LicenseRepository.checkStatus]) — mis. refund,
     * chargeback, atau terbukti dipakai di luar ketentuan. Sama seperti [TRIAL_EXPIRED]:
     * transaksi harian tetap jalan, hanya fitur prioritas yang terkunci sampai aktivasi ulang
     * dengan lisensi yang sah. */
    REVOKED,
}

/**
 * Payload yang ditandatangani server (Cloud Function `activateLicense`) saat aktivasi PERTAMA.
 * Field & urutan JSON harus SAMA PERSIS dengan yang dibuat backend (lihat functions/index.js)
 * karena tanda tangan dihitung dari representasi JSON string ini apa adanya.
 *
 * SENGAJA TIDAK ADA field masa berlaku (validUntil/expiry) — lisensi ini sekali bayar, sertifikat
 * yang ditandatangani di sini berlaku selamanya untuk kombinasi licenseKey+deviceId tersebut.
 */
data class LicensePayload(
    val licenseKey: String,
    val deviceId: String,
    val customerName: String,
    val plan: String,
    val issuedAt: Long,
)

data class LicenseState(
    val status: LicenseStatus,
    val licenseKey: String? = null,
    val customerName: String? = null,
    val plan: String? = null,
    /** Kapan sertifikat lisensi ini pertama kali diterbitkan server (saat aktivasi) — HANYA
     * untuk ditampilkan ke pengguna ("Aktif sejak ..."), BUKAN tanggal kedaluwarsa (tidak ada). */
    val activatedAt: Long? = null,
    /** Kapan terakhir kali [LicenseRepository.checkStatus] berhasil menghubungi server (murni
     * informasi debug/UI) — tidak mempengaruhi [status] kalau gagal/tidak pernah terjadi. */
    val lastCheckedAt: Long? = null,
    /** Hanya terisi saat [status] TRIAL/TRIAL_EXPIRED — kapan masa coba 15 hari berakhir,
     * dihitung dari pertama kali app dibuka di device ini. Dipakai UI untuk menampilkan hitung
     * mundur ("N hari lagi") tanpa perlu menghitung ulang. */
    val trialEndsAt: Long? = null,
    /** Diisi kalau aktivasi/pengecekan status terakhir gagal (mis. offline, key salah, dipakai
     * device lain, dinonaktifkan penjual) — dipakai UI untuk menampilkan pesan tanpa harus
     * mengulang panggilan jaringan. */
    val lastError: String? = null,
) {
    /** Sumber kebenaran tunggal untuk gerbang fitur prioritas (QRIS Otomatis, Sinkronisasi
     * Cloud, Cek Stok Lintas Cabang) — lihat `PremiumFeatureGate` di MainActivity.kt. TIDAK
     * PERNAH dipakai untuk mengunci fitur inti (Kasir/Produk/Stok/Laporan); itu selalu jalan. */
    val hasPremiumAccess: Boolean
        get() = status == LicenseStatus.ACTIVE || status == LicenseStatus.TRIAL
}

sealed class LicenseActivationResult {
    data class Success(val state: LicenseState) : LicenseActivationResult()
    data class Error(val message: String) : LicenseActivationResult()
}
