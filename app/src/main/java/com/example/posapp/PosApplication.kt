package com.example.posapp

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PosApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Dipasang PALING AWAL (sebelum apapun lain di onCreate) supaya menangkap crash yang
        // terjadi sedini mungkin, termasuk saat inisialisasi dependency Hilt/Room/DataStore.
        CrashHandler.install(this)
    }
}
