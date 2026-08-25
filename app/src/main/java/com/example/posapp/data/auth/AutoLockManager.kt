package com.example.posapp.data.auth

import com.example.posapp.data.settings.StoreProfileRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kebijakan auto-lock: menutup sesi login (SessionManager.logout()) tanpa menunggu pengguna
 * menekan tombol Logout, supaya HP kasir yang diletakkan terbuka di meja/laci tidak jadi celah
 * masuk siapa saja ke Pengaturan atau riwayat transaksi.
 *
 * Ada 2 mekanisme independen, keduanya HANYA aktif saat [pinLoginEnabled] menyala (mode
 * single-user tanpa PIN tidak relevan untuk auto-lock):
 *
 * 1. Lock-saat-background (SELALU aktif, tidak bisa dimatikan lewat Pengaturan): begitu app
 *    ditinggal (Home/app-switch/layar mati) lalu dibuka lagi, sesi otomatis diminta login ulang.
 *    Ini risiko keamanan fisik paling nyata untuk device kasir bersama, jadi tidak dibuat opsional.
 * 2. Lock-saat-idle (bisa diatur menitnya di Pengaturan > Pengguna & Login PIN, atau dimatikan
 *    dengan set ke 0): kalau app tetap di depan tapi tidak disentuh sekian menit (mis. kasir
 *    sedang layani pembeli lain tanpa mengunci HP secara manual), sesi otomatis logout juga.
 */
@Singleton
class AutoLockManager @Inject constructor(
    private val sessionManager: SessionManager,
    private val storeProfileRepository: StoreProfileRepository
) {
    @Volatile private var lastInteractionAt: Long = System.currentTimeMillis()
    @Volatile private var backgroundedAt: Long? = null

    /** Dipanggil dari mana pun ada sinyal bahwa pengguna sedang aktif memakai app (tap layar,
     * berpindah layar, dsb.) — mereset hitung mundur idle-timeout. */
    fun recordInteraction() {
        lastInteractionAt = System.currentTimeMillis()
    }

    /** Dipanggil dari MainActivity.onStop(): app baru saja tidak terlihat pengguna lagi. */
    fun onAppBackgrounded() {
        backgroundedAt = System.currentTimeMillis()
    }

    /** Dipanggil dari MainActivity.onStart(): app kembali terlihat. Mengunci sesi kalau app
     * memang sempat di-background dan PIN login aktif — lock ini TIDAK bersyarat durasi,
     * sekali di-background langsung minta PIN lagi begitu kembali. */
    suspend fun onAppForegroundedCheckLock() {
        val wasBackgrounded = backgroundedAt != null
        backgroundedAt = null
        lastInteractionAt = System.currentTimeMillis()
        if (!wasBackgrounded) return

        val profile = storeProfileRepository.profile.first()
        if (profile.pinLoginEnabled && sessionManager.currentUser.value != null) {
            sessionManager.logout()
        }
    }

    /** Loop yang dipanggil dari sebuah LaunchedEffect selama app di foreground; mengecek berkala
     * apakah sudah melewati batas idle-timeout yang dikonfigurasi, lalu logout kalau iya. */
    suspend fun runIdleWatcher() {
        while (true) {
            delay(IDLE_CHECK_INTERVAL_MS)
            val profile = storeProfileRepository.profile.first()
            if (!profile.pinLoginEnabled) continue
            if (sessionManager.currentUser.value == null) continue
            val timeoutMinutes = profile.autoLockMinutes
            if (timeoutMinutes <= 0) continue // idle-timeout dimatikan pengguna

            val idleMillis = System.currentTimeMillis() - lastInteractionAt
            if (idleMillis >= timeoutMinutes * 60_000L) {
                sessionManager.logout()
            }
        }
    }

    private companion object {
        const val IDLE_CHECK_INTERVAL_MS = 15_000L
    }
}
