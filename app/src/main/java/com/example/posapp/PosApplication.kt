package com.example.posapp

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.posapp.data.license.LicenseRepository
import com.example.posapp.data.license.LicenseSyncWorker
import com.example.posapp.data.sync.OutletCatalogSyncWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class PosApplication : Application(), Configuration.Provider {

    // Dipakai WorkManager (lihat androidx.startup di AndroidManifest.xml yang dimatikan untuk
    // initializer bawaan) supaya Worker ber-anotasi @HiltWorker (LicenseSyncWorker,
    // OutletCatalogSyncWorker) bisa menerima dependency lewat constructor injection biasa.
    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var licenseRepository: LicenseRepository

    override fun onCreate() {
        super.onCreate()
        // Dipasang PALING AWAL (sebelum apapun lain di onCreate) supaya menangkap crash yang
        // terjadi sedini mungkin, termasuk saat inisialisasi dependency Hilt/Room/DataStore.
        CrashHandler.install(this)

        // Catat "pertama kali dibuka" SEDINI MUNGKIN (sebelum UI mana pun sempat mengecek status
        // lisensi) — ini yang jadi titik awal hitung mundur masa coba 15 hari
        // (LicenseRepository.TRIAL_PERIOD_MILLIS). Idempotent, aman dipanggil tiap start.
        CoroutineScope(Dispatchers.IO).launch { licenseRepository.ensureFirstLaunchRecorded() }

        // Idempotent (KEEP policy) — aman dipanggil setiap kali app start tanpa membuat job dobel.
        LicenseSyncWorker.schedule(this)
        OutletCatalogSyncWorker.schedule(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
