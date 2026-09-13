package com.example.posapp.data.license

import android.content.Context
import android.provider.Settings
import androidx.datastore.preferences.core.booleanPreferencesKey
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
 * LISENSI INI SEKALI BAYAR, BUKAN LANGGANAN — tidak ada tanggal kedaluwarsa sama sekali:
 * 1. Aktivasi PERTAMA KALI wajib online: [activate] memanggil Cloud Function `activateLicense`,
 *    yang mengikat licenseKey ke [deviceId] device ini (fail kalau key sudah dipakai device lain
 *    dan melebihi maxDevices — lihat functions/index.js) dan mengembalikan sertifikat bertanda
 *    tangan RSA (lihat [LicenseCrypto]) yang berlaku SELAMANYA untuk device ini.
 * 2. Setelah itu app 100% bisa jalan OFFLINE SELAMANYA — status dihitung lokal dari sertifikat
 *    tersimpan, tidak ada jam pasir yang berjalan sama sekali.
 * 3. [LicenseSyncWorker] sesekali memanggil [checkStatus] SECARA OPORTUNISTIK (hanya kalau ada
 *    internet) — BUKAN untuk memperpanjang apa pun (tidak ada yang perlu diperpanjang), murni
 *    untuk mendeteksi kalau penjual menonaktifkan lisensi ini (refund/chargeback/bajakan). Kalau
 *    device tidak pernah online lagi setelah aktivasi, lisensi TETAP AKTIF selamanya — trade-off
 *    yang sengaja diambil demi filosofi offline-first app ini.
 */
@Singleton
class LicenseRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        /** Masa coba gratis SEBELUM aktivasi apa pun diwajibkan, dihitung sejak app pertama kali
         * dibuka di device ini ([KEY_FIRST_LAUNCH_AT]). Selama masa ini SEMUA fitur (termasuk
         * fitur prioritas) jalan penuh tanpa lisensi — supaya pengguna baru bisa merasakan
         * fiturnya dulu. Setelah lewat, aplikasi tidak diblokir; hanya fitur prioritas yang
         * dikunci sampai aktivasi (lihat [LicenseState.hasPremiumAccess]). Ini SATU-SATUNYA
         * konsep berbasis waktu di seluruh sistem lisensi — begitu aktivasi berhasil, tidak ada
         * jam pasir lain yang berjalan (lisensi sekali bayar, bukan langganan).
         */
        const val TRIAL_PERIOD_MILLIS = 15L * 24 * 60 * 60 * 1000 // 15 hari

        private val KEY_LICENSE_KEY = stringPreferencesKey("license_key")
        private val KEY_DEVICE_ID = stringPreferencesKey("device_id")
        private val KEY_CUSTOMER_NAME = stringPreferencesKey("customer_name")
        private val KEY_PLAN = stringPreferencesKey("plan")
        private val KEY_PAYLOAD_JSON = stringPreferencesKey("payload_json")
        private val KEY_SIGNATURE = stringPreferencesKey("signature")
        private val KEY_ACTIVATED_AT = longPreferencesKey("activated_at")
        private val KEY_LAST_CHECKED_AT = longPreferencesKey("last_checked_at")
        private val KEY_REVOKED = booleanPreferencesKey("revoked")
        private val KEY_FIRST_LAUNCH_AT = longPreferencesKey("first_launch_at")
        // TEMUAN KEAMANAN (audit ulang): trial SEBELUMNYA murni membandingkan
        // System.currentTimeMillis() sekarang vs firstLaunchAt + TRIAL_PERIOD_MILLIS -- kalau
        // pengguna mundurkan jam/tanggal HP ke sebelum firstLaunchAt, trial jadi "belum mulai"
        // lagi dan bisa dipakai gratis selamanya. KEY_MAX_OBSERVED_TIME mencatat waktu-sekarang
        // TERBESAR yang pernah dilihat app ini (naik terus, tidak pernah turun walau jam diubah
        // mundur) -- dipakai sebagai pengganti "sekarang" saat mengecek masa trial, lihat
        // ensureFirstLaunchRecorded() & trialState().
        private val KEY_MAX_OBSERVED_TIME = longPreferencesKey("max_observed_time")
    }

    private val functions: FirebaseFunctions by lazy { FirebaseFunctions.getInstance("asia-southeast2") }

    /** ID stabil unik per instalasi (bukan IMEI/data pribadi) — dipakai server untuk mengikat
     * satu licenseKey ke sejumlah device tertentu (lihat maxDevices di backend).
     *
     * TEMUAN KEAMANAN (audit ulang): sebelumnya nilai ini dibaca dari cache DataStore LEBIH
     * DULU sebelum dihitung dari ANDROID_ID. Kalau seluruh folder data app (termasuk file
     * DataStore lisensi ini) disalin ke device lain — mis. lewat `adb backup`/restore, root
     * file manager, atau clone data app — device baru itu ikut membawa nilai device_id milik
     * device asal dan lolos diverifikasi sebagai device yang sama persis (lihat [computeState]),
     * padahal fisiknya berbeda: lisensi sekali-device jadi bisa "dipindah" tanpa aktivasi ulang.
     * Sekarang dihitung ULANG dari Settings.Secure.ANDROID_ID setiap kali dipanggil — nilai ini
     * terikat ke kombinasi OS + signing key APK sehingga TIDAK ikut tersalin sekadar dengan
     * menyalin folder data app. Hasil hitungan tetap disimpan ke DataStore sebagai cache (dibaca
     * balik oleh [computeState] lewat currentDeviceId, bukan lagi sumber kebenaran utama).
     *
     * Fallback UUID acak (device tanpa ANDROID_ID valid, jarang terjadi) TETAP dibaca dari cache
     * kalau sudah pernah dibuat sebelumnya — untuk kasus itu memang tidak ada sumber identitas
     * lain yang lebih baik, keterbatasan yang sudah diketahui dan bukan celah baru.
     */
    suspend fun deviceId(): String {
        val androidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()
        if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") {
            val computed = "and_$androidId"
            context.licenseDataStore.edit { it[KEY_DEVICE_ID] = computed }
            return computed
        }
        val prefs = context.licenseDataStore.data.first()
        prefs[KEY_DEVICE_ID]?.takeIf { it.startsWith("uuid_") }?.let { return it }
        val generated = "uuid_${UUID.randomUUID()}"
        context.licenseDataStore.edit { it[KEY_DEVICE_ID] = generated }
        return generated
    }

    /** Dipanggil sekali dari `PosApplication.onCreate` — mencatat kapan app ini PERTAMA KALI
     * dibuka di device ini, jadi jam pasir masa coba [TRIAL_PERIOD_MILLIS] mulai berjalan sejak
     * device benar-benar mulai dipakai (bukan sejak APK di-build). Idempotent untuk firstLaunchAt
     * (hanya menulis kalau belum pernah tercatat sebelumnya), TAPI KEY_MAX_OBSERVED_TIME di-bump
     * setiap kali app dibuka (bukan cuma sekali) -- lihat komentar di deklarasi key-nya. */
    suspend fun ensureFirstLaunchRecorded() {
        val prefs = context.licenseDataStore.data.first()
        context.licenseDataStore.edit { p ->
            if (prefs[KEY_FIRST_LAUNCH_AT] == null) {
                p[KEY_FIRST_LAUNCH_AT] = System.currentTimeMillis()
            }
            val previousMax = prefs[KEY_MAX_OBSERVED_TIME] ?: 0L
            p[KEY_MAX_OBSERVED_TIME] = maxOf(previousMax, System.currentTimeMillis())
        }
    }

    // Dihitung ULANG dari ANDROID_ID (lihat komentar deviceId()), bukan dibaca dari cache prefs
    // yang sama yang mungkin ikut tersalin kalau seluruh data app dipindahkan ke device lain.
    private val currentDeviceIdFlow: Flow<String> = kotlinx.coroutines.flow.flow { emit(deviceId()) }

    val state: Flow<LicenseState> = kotlinx.coroutines.flow.combine(
        context.licenseDataStore.data,
        currentDeviceIdFlow
    ) { prefs, currentDeviceId ->
        computeState(
            licenseKey = prefs[KEY_LICENSE_KEY],
            customerName = prefs[KEY_CUSTOMER_NAME],
            plan = prefs[KEY_PLAN],
            payloadJson = prefs[KEY_PAYLOAD_JSON],
            signature = prefs[KEY_SIGNATURE],
            activatedAt = prefs[KEY_ACTIVATED_AT],
            lastCheckedAt = prefs[KEY_LAST_CHECKED_AT],
            revoked = prefs[KEY_REVOKED] ?: false,
            firstLaunchAt = prefs[KEY_FIRST_LAUNCH_AT],
            maxObservedTime = prefs[KEY_MAX_OBSERVED_TIME],
            currentDeviceId = currentDeviceId,
            lastError = null,
        )
    }

    private fun trialState(firstLaunchAt: Long?, maxObservedTime: Long?, lastError: String?): LicenseState {
        // Belum pernah aktivasi (atau sertifikat tersimpan rusak/kosong sebagian) — bukan berarti
        // langsung terkunci: beri masa coba TRIAL_PERIOD_MILLIS dulu sejak pertama kali app ini
        // dibuka. `firstLaunchAt` seharusnya selalu sudah ada (diisi `ensureFirstLaunchRecorded`
        // di Application.onCreate sebelum UI mana pun sempat terbaca), tapi kalau karena race
        // tipis ternyata belum, anggap trial baru saja mulai (fail-open ke arah menguntungkan
        // pengguna, bukan fail-closed) alih-alih menganggapnya sudah kedaluwarsa.
        val trialEndsAt = (firstLaunchAt ?: System.currentTimeMillis()) + TRIAL_PERIOD_MILLIS
        // effectiveNow TIDAK PERNAH lebih kecil dari waktu-sekarang terbesar yang pernah dilihat
        // app ini (lihat KEY_MAX_OBSERVED_TIME) -- kalau jam device dimundurkan setelah trial
        // sempat berjalan lewat batas waktunya, effectiveNow tetap mencerminkan itu, jadi trial
        // tidak bisa "diputar ulang" cuma dengan mengubah tanggal HP.
        val effectiveNow = maxOf(System.currentTimeMillis(), maxObservedTime ?: 0L)
        val status = if (effectiveNow <= trialEndsAt) LicenseStatus.TRIAL else LicenseStatus.TRIAL_EXPIRED
        return LicenseState(status = status, trialEndsAt = trialEndsAt, lastError = lastError)
    }

    private fun computeState(
        licenseKey: String?,
        customerName: String?,
        plan: String?,
        payloadJson: String?,
        signature: String?,
        activatedAt: Long?,
        lastCheckedAt: Long?,
        revoked: Boolean,
        firstLaunchAt: Long?,
        maxObservedTime: Long?,
        currentDeviceId: String?,
        lastError: String?,
    ): LicenseState {
        if (licenseKey == null || payloadJson == null || signature == null) {
            return trialState(firstLaunchAt, maxObservedTime, lastError)
        }
        // Verifikasi ulang tanda tangan SETIAP kali dibaca (bukan cuma saat aktivasi) — mencegah
        // seseorang mengedit nilai di file DataStore secara manual (root/backup restore) untuk
        // memalsukan aktivasi tanpa sertifikat yang sah dari server.
        if (!LicenseCrypto.verify(payloadJson, signature)) {
            // Sertifikat rusak/dipalsukan -> perlakukan seperti belum pernah aktivasi (jatuh ke
            // TRIAL/TRIAL_EXPIRED sesuai firstLaunchAt), bukan status tersendiri, supaya toko
            // yang sah tapi kebetulan trial-nya masih jalan tidak ikut ter-lock oleh error ini.
            return trialState(firstLaunchAt, maxObservedTime, "Sertifikat lisensi tidak valid, aktivasi ulang diperlukan.")
        }
        // TEMUAN KEAMANAN (audit ulang): tanda tangan valid saja TIDAK CUKUP -- sertifikat yang
        // sah tetap mengikat licenseKey+deviceId TERTENTU (lihat LicensePayload). Sebelumnya
        // deviceId dari payload ini tidak pernah dicocokkan ke device yang sedang menjalankan
        // app, jadi menyalin seluruh data app (termasuk sertifikat & cache device_id-nya) ke
        // device lain membuat device itu ikut lolos sebagai ACTIVE selamanya tanpa pernah
        // aktivasi ulang. currentDeviceId sekarang dihitung ULANG dari ANDROID_ID (lihat
        // deviceId()), bukan dibaca dari cache yang sama yang ikut tersalin, sehingga device
        // hasil salinan akan menghasilkan currentDeviceId yang beda dan ketahuan di sini.
        val payload = LicenseCrypto.parsePayload(payloadJson)
        if (currentDeviceId != null && payload.deviceId != currentDeviceId) {
            return trialState(
                firstLaunchAt,
                maxObservedTime,
                "Sertifikat lisensi ini terdaftar untuk perangkat lain. Aktivasi ulang diperlukan di perangkat ini."
            )
        }
        // TIDAK ADA pengecekan tanggal di sini — sertifikat yang lolos verifikasi tanda tangan
        // berlaku SELAMANYA (lisensi sekali bayar). Satu-satunya jalan keluar dari ACTIVE adalah
        // flag `revoked` lokal, yang HANYA bisa diisi true oleh [checkStatus] saat online (lihat
        // di bawah) — tidak pernah oleh berlalunya waktu.
        val status = if (revoked) LicenseStatus.REVOKED else LicenseStatus.ACTIVE
        return LicenseState(
            status = status,
            licenseKey = licenseKey,
            customerName = customerName,
            plan = plan,
            activatedAt = activatedAt,
            lastCheckedAt = lastCheckedAt,
            lastError = lastError,
        )
    }

    suspend fun activate(licenseKey: String): LicenseActivationResult {
        val trimmedKey = licenseKey.trim()
        if (trimmedKey.isBlank()) return LicenseActivationResult.Error("Kode lisensi tidak boleh kosong.")
        val devId = deviceId()
        return try {
            val data = hashMapOf(
                "licenseKey" to trimmedKey,
                "deviceId" to devId,
                "deviceModel" to (android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL),
            )
            val result = functions.getHttpsCallable("activateLicense").call(data).await()
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
                    "Tanda tangan sertifikat dari server tidak valid. Pastikan public key di " +
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
                prefs[KEY_ACTIVATED_AT] = payload.issuedAt
                prefs[KEY_LAST_CHECKED_AT] = System.currentTimeMillis()
                prefs[KEY_REVOKED] = false
            }
            LicenseActivationResult.Success(
                computeState(
                    licenseKey = trimmedKey, customerName = payload.customerName, plan = payload.plan,
                    payloadJson = payloadJson, signature = signature, activatedAt = payload.issuedAt,
                    lastCheckedAt = System.currentTimeMillis(), revoked = false, firstLaunchAt = null,
                    maxObservedTime = null,
                    currentDeviceId = devId, lastError = null,
                )
            )
        } catch (e: FirebaseFunctionsException) {
            LicenseActivationResult.Error(mapFunctionsError(e))
        } catch (e: Exception) {
            LicenseActivationResult.Error("Gagal terhubung ke server lisensi. Pastikan internet aktif. (${e.message ?: "unknown"})")
        }
    }

    /** Dipanggil OPORTUNISTIK oleh [LicenseSyncWorker] (atau tombol "Cek Status Lisensi" manual)
     * — BUKAN untuk memperpanjang apa pun (tidak ada yang kedaluwarsa di lisensi sekali bayar
     * ini), HANYA untuk mendeteksi kalau penjual menonaktifkan lisensi yang sudah aktif di device
     * ini. Gagal (offline, dsb.) TIDAK PERNAH mengubah status tersimpan — fail-open, konsisten
     * dengan filosofi lisensi ini tidak boleh mewajibkan internet berkala. */
    suspend fun checkStatus(): LicenseActivationResult {
        val prefs = context.licenseDataStore.data.first()
        val currentKey = prefs[KEY_LICENSE_KEY]
            ?: return LicenseActivationResult.Error("Belum ada lisensi yang teraktivasi di device ini.")
        val devId = deviceId()
        return try {
            val data = hashMapOf("licenseKey" to currentKey, "deviceId" to devId)
            val result = functions.getHttpsCallable("checkLicenseStatus").call(data).await()
            @Suppress("UNCHECKED_CAST")
            val map = result.data as? Map<String, Any?>
                ?: return LicenseActivationResult.Error("Respons server tidak valid.")
            val isActive = map["isActive"] as? Boolean ?: true
            context.licenseDataStore.edit { p ->
                p[KEY_REVOKED] = !isActive
                p[KEY_LAST_CHECKED_AT] = System.currentTimeMillis()
            }
            val updated = context.licenseDataStore.data.first()
            LicenseActivationResult.Success(
                computeState(
                    licenseKey = updated[KEY_LICENSE_KEY],
                    customerName = updated[KEY_CUSTOMER_NAME],
                    plan = updated[KEY_PLAN],
                    payloadJson = updated[KEY_PAYLOAD_JSON],
                    signature = updated[KEY_SIGNATURE],
                    activatedAt = updated[KEY_ACTIVATED_AT],
                    lastCheckedAt = updated[KEY_LAST_CHECKED_AT],
                    revoked = updated[KEY_REVOKED] ?: false,
                    firstLaunchAt = updated[KEY_FIRST_LAUNCH_AT],
                    maxObservedTime = updated[KEY_MAX_OBSERVED_TIME],
                    currentDeviceId = devId,
                    lastError = if (!isActive) "Lisensi ini sudah dinonaktifkan penjual." else null,
                )
            )
        } catch (e: FirebaseFunctionsException) {
            // Gagal menghubungi server (termasuk offline) TIDAK mengubah status tersimpan sama
            // sekali — lisensi yang sudah ACTIVE tetap ACTIVE. Hanya melaporkan error ke UI.
            LicenseActivationResult.Error(mapFunctionsError(e))
        } catch (e: Exception) {
            LicenseActivationResult.Error("Gagal terhubung ke server lisensi. Pastikan internet aktif. (${e.message ?: "unknown"})")
        }
    }

    private fun mapFunctionsError(e: FirebaseFunctionsException): String = when (e.code) {
        FirebaseFunctionsException.Code.NOT_FOUND -> "Kode lisensi tidak ditemukan. Cek kembali kode yang diberikan penjual."
        FirebaseFunctionsException.Code.PERMISSION_DENIED -> e.message
            ?: "Lisensi ini sudah dipakai di device lain dan sudah mencapai batas maksimal perangkat."
        FirebaseFunctionsException.Code.FAILED_PRECONDITION -> e.message ?: "Lisensi ini sudah tidak aktif (dinonaktifkan penjual)."
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
