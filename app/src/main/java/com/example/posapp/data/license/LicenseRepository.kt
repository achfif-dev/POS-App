package com.example.posapp.data.license

import android.content.Context
import android.provider.Settings
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.licenseDataStore by preferencesDataStore(name = "license")

/**
 * Mengelola aktivasi & status lisensi aplikasi PER DEVICE, sepenuhnya self-service dari dalam
 * app (lihat LicenseActivationScreen) — pelanggan cukup menempelkan kode lisensi yang dikirim
 * developer setelah pembelian, TANPA perlu developer login/setting manual ke device pelanggan.
 *
 * Alur:
 * 1. Aktivasi PERTAMA KALI wajib online: [activate] memanggil Cloud Function `activateLicense`,
 *    yang mengikat licenseKey ke [deviceId] device ini (fail kalau key sudah dipakai device lain
 *    dan melebihi maxDevices — lihat functions/index.js) dan mengembalikan token bertanda tangan
 *    RSA (lihat [LicenseCrypto]) yang valid untuk [VALIDITY_WINDOW_MILLIS] ke depan.
 * 2. Setelah itu app 100% bisa jalan OFFLINE — status dihitung lokal dari token tersimpan.
 * 3. [LicenseSyncWorker] mencoba [revalidate] secara oportunistik (kalau ada internet) untuk
 *    memperpanjang masa berlaku token secara diam-diam, sebelum [LicenseState.validUntil] habis.
 * 4. Kalau device offline lebih lama dari [VALIDITY_WINDOW_MILLIS] + [GRACE_PERIOD_MILLIS],
 *    status jadi EXPIRED dan app minta koneksi internet untuk revalidasi ulang.
 */
@Singleton
class LicenseRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        /** Lama token dianggap ACTIVE sejak diterbitkan server tanpa perlu online lagi. */
        const val VALIDITY_WINDOW_MILLIS = 30L * 24 * 60 * 60 * 1000 // 30 hari
        /** Tambahan waktu setelah [VALIDITY_WINDOW_MILLIS] habis di mana app TETAP jalan penuh
         * (hanya menampilkan pengingat) — mengakomodasi toko yang jarang online. */
        const val GRACE_PERIOD_MILLIS = 14L * 24 * 60 * 60 * 1000 // 14 hari tambahan

        private val KEY_LICENSE_KEY = stringPreferencesKey("license_key")
        private val KEY_DEVICE_ID = stringPreferencesKey("device_id")
        private val KEY_CUSTOMER_NAME = stringPreferencesKey("customer_name")
        private val KEY_PLAN = stringPreferencesKey("plan")
        private val KEY_PAYLOAD_JSON = stringPreferencesKey("payload_json")
        private val KEY_SIGNATURE = stringPreferencesKey("signature")
        private val KEY_VALID_UNTIL = longPreferencesKey("valid_until")
        private val KEY_LAST_VALIDATED_AT = longPreferencesKey("last_validated_at")
    }

    private val functions: FirebaseFunctions by lazy { FirebaseFunctions.getInstance("asia-southeast2") }

    /** ID stabil unik per instalasi (bukan IMEI/data pribadi) — dibuat sekali, dipakai server
     * untuk mengikat satu licenseKey ke sejumlah device tertentu (lihat maxDevices di backend). */
    suspend fun deviceId(): String {
        val prefs = context.licenseDataStore.data.first()
        prefs[KEY_DEVICE_ID]?.let { return it }
        val androidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()
        val generated = if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") {
            "and_$androidId"
        } else {
            "uuid_${UUID.randomUUID()}"
        }
        context.licenseDataStore.edit { it[KEY_DEVICE_ID] = generated }
        return generated
    }

    val state: Flow<LicenseState> = context.licenseDataStore.data.map { prefs ->
        computeState(
            licenseKey = prefs[KEY_LICENSE_KEY],
            customerName = prefs[KEY_CUSTOMER_NAME],
            plan = prefs[KEY_PLAN],
            payloadJson = prefs[KEY_PAYLOAD_JSON],
            signature = prefs[KEY_SIGNATURE],
            validUntil = prefs[KEY_VALID_UNTIL],
            lastValidatedAt = prefs[KEY_LAST_VALIDATED_AT],
            lastError = null,
        )
    }

    private fun computeState(
        licenseKey: String?,
        customerName: String?,
        plan: String?,
        payloadJson: String?,
        signature: String?,
        validUntil: Long?,
        lastValidatedAt: Long?,
        lastError: String?,
    ): LicenseState {
        if (licenseKey == null || payloadJson == null || signature == null || validUntil == null) {
            return LicenseState(status = LicenseStatus.NOT_ACTIVATED, lastError = lastError)
        }
        // Verifikasi ulang tanda tangan SETIAP kali dibaca (bukan cuma saat aktivasi) — mencegah
        // seseorang mengedit nilai validUntil di file DataStore secara manual (root/backup restore)
        // untuk memperpanjang masa aktif tanpa token yang sah dari server.
        if (!LicenseCrypto.verify(payloadJson, signature)) {
            return LicenseState(status = LicenseStatus.NOT_ACTIVATED, lastError = "Token lisensi tidak valid, aktivasi ulang diperlukan.")
        }
        val now = System.currentTimeMillis()
        val status = when {
            now <= validUntil -> LicenseStatus.ACTIVE
            now <= validUntil + GRACE_PERIOD_MILLIS -> LicenseStatus.GRACE_PERIOD
            else -> LicenseStatus.EXPIRED
        }
        return LicenseState(
            status = status,
            licenseKey = licenseKey,
            customerName = customerName,
            plan = plan,
            validUntil = validUntil,
            lastValidatedAt = lastValidatedAt,
            lastError = lastError,
        )
    }

    suspend fun activate(licenseKey: String): LicenseActivationResult =
        callActivationFunction("activateLicense", licenseKey)

    /** Dipanggil diam-diam oleh [LicenseSyncWorker] atau saat app dibuka dan online, memakai
     * licenseKey yang sudah tersimpan — tidak minta pengguna mengetik ulang apa pun. */
    suspend fun revalidate(): LicenseActivationResult {
        val current = context.licenseDataStore.data.first()[KEY_LICENSE_KEY]
            ?: return LicenseActivationResult.Error("Belum ada lisensi yang teraktivasi di device ini.")
        return callActivationFunction("revalidateLicense", current)
    }

    private suspend fun callActivationFunction(functionName: String, licenseKey: String): LicenseActivationResult {
        val trimmedKey = licenseKey.trim()
        if (trimmedKey.isBlank()) return LicenseActivationResult.Error("Kode lisensi tidak boleh kosong.")
        val devId = deviceId()
        return try {
            val data = hashMapOf(
                "licenseKey" to trimmedKey,
                "deviceId" to devId,
                "deviceModel" to (android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL),
            )
            val result = functions.getHttpsCallable(functionName).call(data).await()
            @Suppress("UNCHECKED_CAST")
            val map = result.data as? Map<String, Any?>
                ?: return LicenseActivationResult.Error("Respons server tidak valid.")
            val payloadJson = map["payload"] as? String
            val signature = map["signature"] as? String
            if (payloadJson == null || signature == null) {
                return LicenseActivationResult.Error("Respons server tidak lengkap.")
            }
            if (!LicenseCrypto.verify(payloadJson, signature)) {
                return LicenseActivationResult.Error(
                    "Tanda tangan token dari server tidak valid. Pastikan public key di " +
                        "LicenseCrypto.kt sudah sesuai dengan private key yang dipakai backend."
                )
            }
            val payload = LicenseCrypto.parsePayload(payloadJson)
            context.licenseDataStore.edit { prefs ->
                prefs[KEY_LICENSE_KEY] = trimmedKey
                prefs[KEY_DEVICE_ID] = devId
                prefs[KEY_CUSTOMER_NAME] = payload.customerName
                prefs[KEY_PLAN] = payload.plan
                prefs[KEY_PAYLOAD_JSON] = payloadJson
                prefs[KEY_SIGNATURE] = signature
                prefs[KEY_VALID_UNTIL] = payload.validUntil
                prefs[KEY_LAST_VALIDATED_AT] = System.currentTimeMillis()
            }
            LicenseActivationResult.Success(
                computeState(
                    trimmedKey, payload.customerName, payload.plan, payloadJson, signature,
                    payload.validUntil, System.currentTimeMillis(), null,
                )
            )
        } catch (e: FirebaseFunctionsException) {
            LicenseActivationResult.Error(mapFunctionsError(e))
        } catch (e: Exception) {
            LicenseActivationResult.Error("Gagal terhubung ke server lisensi. Pastikan internet aktif. (${e.message ?: "unknown"})")
        }
    }

    private fun mapFunctionsError(e: FirebaseFunctionsException): String = when (e.code) {
        FirebaseFunctionsException.Code.NOT_FOUND -> "Kode lisensi tidak ditemukan. Cek kembali kode yang diberikan penjual."
        FirebaseFunctionsException.Code.PERMISSION_DENIED -> e.message
            ?: "Lisensi ini sudah dipakai di device lain dan sudah mencapai batas maksimal perangkat."
        FirebaseFunctionsException.Code.FAILED_PRECONDITION -> e.message ?: "Lisensi ini sudah tidak aktif (nonaktif/kedaluwarsa dari sisi penjual)."
        FirebaseFunctionsException.Code.UNAVAILABLE -> "Tidak bisa terhubung ke server. Cek koneksi internet."
        else -> e.message ?: "Gagal aktivasi lisensi (${e.code})."
    }

    /** Hapus aktivasi dari device ini (mis. sebelum pindah HP) — TIDAK membebaskan slot di server
     * secara otomatis; developer/penjual perlu membebaskan device lama lewat `scripts/issue-license.js
     * --release` atau Firebase Console kalau memakai batas maxDevices ketat. */
    suspend fun deactivateLocally() {
        context.licenseDataStore.edit { it.clear() }
    }
}
