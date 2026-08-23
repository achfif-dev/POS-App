package com.example.posapp.data.backup

import android.content.Context
import com.example.posapp.data.local.AppDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

sealed class BackupResult {
    data class Success(val file: File) : BackupResult()
    data class Error(val message: String) : BackupResult()
}

/**
 * Backup & restore dengan cara paling andal untuk Room: copy file database SQLite secara utuh
 * (termasuk file -wal dan -shm bila ada) alih-alih export ke JSON manual. Ini memastikan semua
 * relasi & integritas data tetap terjaga persis seperti kondisi asli.
 *
 * PENTING: Restore mengharuskan AppDatabase dalam keadaan tertutup (db.close()) sebelum file
 * ditimpa, lalu proses harus di-restart agar Room membuka kembali file yang baru. Cara paling
 * aman di Compose: setelah restore sukses, tampilkan pesan lalu minta pengguna membuka ulang app.
 */
@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appDatabase: AppDatabase
) {
    private val dbFile: File get() = context.getDatabasePath(AppDatabase.DATABASE_NAME)

    fun backup(): BackupResult {
        return try {
            if (!dbFile.exists()) return BackupResult.Error("Database belum memiliki data untuk di-backup")

            val backupDir = File(context.getExternalFilesDir(null), "backups").apply { mkdirs() }
            val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
            val backupFile = File(backupDir, "pos_backup_$timestamp.db")

            // Checkpoint WAL dulu supaya semua perubahan sudah tertulis ke file utama sebelum di-copy.
            appDatabase.query("PRAGMA wal_checkpoint(FULL)", null).use { /* no-op, cursor hanya untuk trigger checkpoint */ }

            dbFile.copyTo(backupFile, overwrite = true)
            BackupResult.Success(backupFile)
        } catch (e: Exception) {
            BackupResult.Error(e.message ?: "Gagal membuat backup")
        }
    }

    fun restore(sourceFile: File): BackupResult {
        return try {
            if (!sourceFile.exists()) return BackupResult.Error("File backup tidak ditemukan")

            appDatabase.close()

            // Hapus file -wal dan -shm lama agar tidak konflik dengan database hasil restore.
            File(dbFile.path + "-wal").delete()
            File(dbFile.path + "-shm").delete()

            sourceFile.copyTo(dbFile, overwrite = true)
            BackupResult.Success(dbFile)
        } catch (e: Exception) {
            BackupResult.Error(e.message ?: "Gagal melakukan restore. Pastikan file backup valid.")
        }
    }

    fun listLocalBackups(): List<File> {
        val backupDir = File(context.getExternalFilesDir(null), "backups")
        return backupDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    /** Menghapus satu file backup lokal dari Riwayat Backup Lokal. */
    fun deleteBackup(file: File): Boolean {
        return try {
            file.exists() && file.delete()
        } catch (e: Exception) {
            false
        }
    }
}
