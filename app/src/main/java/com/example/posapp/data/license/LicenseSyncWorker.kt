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
 * Revalidasi lisensi diam-diam setiap kali device kebetulan online, supaya masa berlaku token
 * (30 hari, lihat [LicenseRepository.VALIDITY_WINDOW_MILLIS]) terus otomatis diperpanjang TANPA
 * pengguna perlu buka layar Aktivasi Lisensi lagi. Kalau tidak ada internet, WorkManager cukup
 * menunda sampai constraint NETWORK_TYPE_CONNECTED terpenuhi — tidak pernah mengganggu app.
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

        return when (val result = licenseRepository.revalidate()) {
            is LicenseActivationResult.Success -> Result.success()
            // Retry otomatis (backoff bawaan WorkManager) — biasanya karena sedang offline
            // walau constraint NETWORK_TYPE_CONNECTED terpenuhi sesaat lalu putus lagi.
            is LicenseActivationResult.Error -> Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "license_revalidation"

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
