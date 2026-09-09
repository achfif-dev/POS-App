package com.example.posapp.data.license

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Lisensi ini SEKALI BAYAR, bukan langganan — tidak ada apa pun yang perlu "diperpanjang". Worker
 * ini murni OPORTUNISTIK: setiap kali device kebetulan online, cek diam-diam lewat
 * [LicenseRepository.checkStatus] apakah penjual/developer sempat menonaktifkan lisensi yang
 * sudah aktif di device ini (refund/chargeback/bajakan) — TIDAK PERNAH menandatangani ulang atau
 * memperbarui masa berlaku apa pun, karena tidak ada masa berlaku yang perlu diperbarui.
 *
 * SENGAJA `Result.success()` untuk kegagalan (offline, dsb.), BUKAN `Result.retry()` — device
 * yang jarang/tidak pernah online lagi setelah aktivasi TETAP dianggap berlisensi sah selamanya
 * (lihat filosofi fail-open di [LicenseRepository.checkStatus]), jadi tidak ada gunanya membuat
 * WorkManager retry berulang-ulang untuk sesuatu yang bukan syarat app tetap berjalan.
 */
@HiltWorker
class LicenseSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val licenseRepository: LicenseRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val hasLicense = licenseRepository.state.first().licenseKey != null
        if (!hasLicense) return Result.success()

        // Hasil (sukses ATAU error/offline) sama-sama Result.success() di sini — lihat catatan
        // fail-open di dokumentasi kelas ini. checkStatus() sendiri yang menjamin status
        // tersimpan tidak pernah berubah kalau panggilan ini gagal.
        licenseRepository.checkStatus()
        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "license_status_check"

        /** Panggil sekali dari Application.onCreate — aman dipanggil berkali-kali (idempotent). */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<LicenseSyncWorker>(12, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
