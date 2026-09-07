package com.example.posapp.data.license

/**
 * Status lisensi aplikasi. Dihitung LOKAL (tanpa perlu koneksi internet setiap kali dicek) dari
 * [validUntil] yang tersimpan di device, hasil verifikasi tanda tangan server saat aktivasi/
 * revalidasi terakhir — lihat [LicenseCrypto] & [LicenseRepository].
 *
 * Filosofi: app ini offline-first, jadi lisensi TIDAK BOLEH mewajibkan internet tiap hari.
 * Sekali aktivasi online berhasil, app boleh jalan offline sampai [validUntil], lalu ada masa
 * tenggang [LicenseRepository.GRACE_PERIOD_MILLIS] sebelum benar-benar terkunci — supaya toko
 * yang kebetulan tidak online berhari-hari tidak mendadak ter-lock di tengah jam operasional.
 */
enum class LicenseStatus {
    /** Belum pernah aktivasi sama sekali di device ini. */
    NOT_ACTIVATED,
    /** Token valid, semua fitur jalan normal. */
    ACTIVE,
    /** Token sudah lewat [LicenseState.validUntil] tapi masih dalam masa tenggang — app tetap
     * jalan penuh, hanya tampil pengingat halus untuk online supaya revalidasi otomatis jalan. */
    GRACE_PERIOD,
    /** Lewat masa tenggang tanpa berhasil revalidasi — app diblokir, minta koneksi internet. */
    EXPIRED,
}

/**
 * Payload yang ditandatangani server (Cloud Function) saat aktivasi/revalidasi. Field & urutan
 * JSON harus SAMA PERSIS dengan yang dibuat backend (lihat functions/index.js) karena tanda
 * tangan dihitung dari representasi JSON string ini apa adanya.
 */
data class LicensePayload(
    val licenseKey: String,
    val deviceId: String,
    val customerName: String,
    val plan: String,
    val issuedAt: Long,
    val validUntil: Long,
)

data class LicenseState(
    val status: LicenseStatus,
    val licenseKey: String? = null,
    val customerName: String? = null,
    val plan: String? = null,
    val validUntil: Long? = null,
    val lastValidatedAt: Long? = null,
    /** Diisi kalau aktivasi/revalidasi terakhir gagal (mis. offline, key salah, dipakai device
     * lain) — dipakai UI untuk menampilkan pesan tanpa harus mengulang panggilan jaringan. */
    val lastError: String? = null,
)

sealed class LicenseActivationResult {
    data class Success(val state: LicenseState) : LicenseActivationResult()
    data class Error(val message: String) : LicenseActivationResult()
}
