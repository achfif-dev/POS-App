package com.example.posapp.data.license

import android.util.Base64
import org.json.JSONObject
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Verifikasi tanda tangan digital token lisensi dengan skema ASIMETRIS (RSA-2048 + SHA256).
 *
 * KENAPA ASIMETRIS, BUKAN HMAC/SECRET BIASA:
 * Kalau memakai kunci rahasia yang sama untuk menandatangani (server) dan memverifikasi (app),
 * kunci itu HARUS ikut ditanam di dalam APK supaya app bisa memverifikasi secara offline — dan
 * APK bisa di-decompile siapa pun, sehingga kunci rahasia bocor dan siapa pun bisa membuat
 * token lisensi palsu sendiri (percuma sebagai proteksi anti-bajakan).
 *
 * Dengan RSA: server (Cloud Function `activateLicense`) memegang PRIVATE KEY
 * yang TIDAK PERNAH ada di dalam app. App hanya menyimpan PUBLIC KEY (aman dibagikan/dilihat
 * siapa pun — sesuai namanya) untuk memverifikasi tanda tangan SECARA OFFLINE. Public key boleh
 * bocor tanpa risiko; yang tidak boleh bocor (private key) memang tidak pernah dikirim ke device.
 *
 * WAJIB DIGANTI SEBELUM RILIS: [LICENSE_PUBLIC_KEY_BASE64] di bawah ini masih placeholder.
 * Jalankan `node scripts/generate-license-keypair.js` sekali (lihat file itu) untuk membuat
 * pasangan kunci asli, lalu:
 *  1. Simpan PRIVATE key sebagai secret di Cloud Functions (JANGAN pernah commit ke Git/APK).
 *  2. Tempel PUBLIC key (base64, satu baris, tanpa header "-----BEGIN...") ke konstanta di bawah.
 * Selama masih placeholder ini, [verify] akan SELALU mengembalikan false (fail-closed) supaya
 * developer tidak lupa dan mengira lisensi "aktif" padahal verifikasinya belum benar-benar jalan.
 */
object LicenseCrypto {

    private const val LICENSE_PUBLIC_KEY_BASE64 = "0Wod7n3MHVb0N2xKdPqznKfoqVTs3ydIhyq45xWyMxrQZvuSg446edWp1VPOOK0diyRRbVuZMKA6ndbeZtqzI6Aatd3NShncQ12kFUM4q91eDs8JTpYXHffe2RI3flygStB0kakhoPDvFIJQnteUSjkL8b7ObH9HQToDN48uFvbwMtSjwdwIDAQAB"

    private val publicKey: PublicKey? by lazy {
        if (LICENSE_PUBLIC_KEY_BASE64.startsWith("PASTE_")) return@lazy null
        runCatching {
            val bytes = Base64.decode(LICENSE_PUBLIC_KEY_BASE64, Base64.DEFAULT)
            val spec = X509EncodedKeySpec(bytes)
            KeyFactory.getInstance("RSA").generatePublic(spec)
        }.getOrNull()
    }

    /** true kalau kunci publik asli sudah dipasang (bukan placeholder bawaan). Dipakai layar
     * Setup/Aktivasi untuk memperingatkan developer/admin kalau build ini belum siap produksi. */
    fun isConfigured(): Boolean = publicKey != null

    /**
     * @param canonicalPayloadJson string JSON payload PERSIS seperti yang ditandatangani server
     *   (lihat komentar urutan field di functions/index.js — signPayload menyusun JSON dengan
     *   urutan key yang fixed, bukan hasil serialisasi objek Kotlin di sisi app).
     * @param signatureBase64 tanda tangan RSA-SHA256 dari payload di atas, base64.
     */
    fun verify(canonicalPayloadJson: String, signatureBase64: String): Boolean {
        val key = publicKey ?: return false
        return runCatching {
            val sig = Signature.getInstance("SHA256withRSA")
            sig.initVerify(key)
            sig.update(canonicalPayloadJson.toByteArray(Charsets.UTF_8))
            sig.verify(Base64.decode(signatureBase64, Base64.DEFAULT))
        }.getOrDefault(false)
    }

    /** Parse payload JSON (setelah lolos [verify]) jadi [LicensePayload]. Tidak melakukan
     * verifikasi apa pun di sini — panggil [verify] dulu sebelum mempercayai hasil parse ini. */
    fun parsePayload(canonicalPayloadJson: String): LicensePayload {
        val obj = JSONObject(canonicalPayloadJson)
        return LicensePayload(
            licenseKey = obj.getString("licenseKey"),
            deviceId = obj.getString("deviceId"),
            customerName = obj.optString("customerName", "-"),
            plan = obj.optString("plan", "standard"),
            issuedAt = obj.getLong("issuedAt"),
        )
    }
}
