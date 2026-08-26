package com.example.posapp.data.sync

import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Ringkasan omzet SATU cabang untuk SATU tanggal — satu-satunya data yang dikirim ke cloud
 * (bukan detail transaksi/produk/pelanggan), supaya tetap ringan & aman secara privasi. */
data class OutletSalesSummary(
    val outletId: String,
    val outletName: String,
    val dateKey: String, // format "yyyy-MM-dd"
    val totalRevenue: Double,
    val totalTransactions: Int,
    val updatedAt: Long
)

sealed class CloudSyncStatus {
    object Idle : CloudSyncStatus()
    /** google-services.json belum ada / Firebase belum disiapkan — lihat FIREBASE_SETUP.md. */
    object NotConfigured : CloudSyncStatus()
    object Syncing : CloudSyncStatus()
    data class Success(val at: Long) : CloudSyncStatus()
    data class Error(val message: String) : CloudSyncStatus()
}

private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
        val exception = task.exception
        if (task.isSuccessful) {
            cont.resume(task.result)
        } else {
            cont.resumeWithException(exception ?: RuntimeException("Task Firebase gagal tanpa detail"))
        }
    }
}

/**
 * Mengirim & mengambil ringkasan omzet lintas cabang lewat Firestore. SELURUH fungsi di sini
 * fail-soft: kalau Firebase belum dikonfigurasi (tidak ada google-services.json) atau device
 * offline, status berubah jadi [CloudSyncStatus.NotConfigured]/[CloudSyncStatus.Error] — TIDAK
 * PERNAH melempar exception ke pemanggil, supaya fitur ini murni opsional dan tidak pernah
 * mengganggu alur kasir/checkout normal yang sepenuhnya offline-first.
 */
@Singleton
class CloudSyncRepository @Inject constructor() {

    private val _status = MutableStateFlow<CloudSyncStatus>(CloudSyncStatus.Idle)
    val status: StateFlow<CloudSyncStatus> = _status.asStateFlow()

    private fun firestoreOrNull(): FirebaseFirestore? = runCatching { FirebaseFirestore.getInstance() }.getOrNull()
    private fun authOrNull(): FirebaseAuth? = runCatching { FirebaseAuth.getInstance() }.getOrNull()

    /** Sign-in anonim (tanpa data pribadi) — cukup untuk Firestore Security Rules mensyaratkan
     * `request.auth != null`, mencegah tulis/baca oleh siapa pun yang sekadar tahu config publik. */
    private suspend fun ensureSignedIn(auth: FirebaseAuth): Boolean {
        if (auth.currentUser != null) return true
        return try {
            auth.signInAnonymously().awaitTask()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun pushDailySummary(summary: OutletSalesSummary) {
        val db = firestoreOrNull()
        val auth = authOrNull()
        if (db == null || auth == null) {
            _status.value = CloudSyncStatus.NotConfigured
            return
        }
        _status.value = CloudSyncStatus.Syncing
        try {
            if (!ensureSignedIn(auth)) {
                _status.value = CloudSyncStatus.Error("Gagal autentikasi cloud (cek koneksi internet)")
                return
            }
            val docId = "${summary.outletId}_${summary.dateKey}"
            val data = hashMapOf(
                "outletId" to summary.outletId,
                "outletName" to summary.outletName,
                "dateKey" to summary.dateKey,
                "totalRevenue" to summary.totalRevenue,
                "totalTransactions" to summary.totalTransactions,
                "updatedAt" to summary.updatedAt
            )
            db.collection("outlet_summaries").document(docId).set(data).awaitTask()
            _status.value = CloudSyncStatus.Success(System.currentTimeMillis())
        } catch (e: Exception) {
            _status.value = CloudSyncStatus.Error(e.message ?: "Gagal sinkronisasi ke cloud")
        }
    }

    /** Ambil ringkasan SEMUA cabang untuk satu tanggal tertentu — dipakai Ringkasan Semua Cabang. */
    suspend fun fetchSummariesForDate(dateKey: String): List<OutletSalesSummary> {
        val db = firestoreOrNull() ?: return emptyList()
        val auth = authOrNull() ?: return emptyList()
        if (!ensureSignedIn(auth)) return emptyList()
        return try {
            val snapshot = db.collection("outlet_summaries")
                .whereEqualTo("dateKey", dateKey)
                .get()
                .awaitTask()
            snapshot.documents.mapNotNull { doc ->
                val outletId = doc.getString("outletId") ?: return@mapNotNull null
                OutletSalesSummary(
                    outletId = outletId,
                    outletName = doc.getString("outletName") ?: outletId,
                    dateKey = dateKey,
                    totalRevenue = doc.getDouble("totalRevenue") ?: 0.0,
                    totalTransactions = (doc.getLong("totalTransactions") ?: 0L).toInt(),
                    updatedAt = doc.getLong("updatedAt") ?: 0L
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** true kalau Firebase terlihat terkonfigurasi (bukan jaminan kredensial valid, hanya bahwa
     * FirebaseApp berhasil di-inisialisasi dari google-services.json yang ada). */
    fun isConfigured(): Boolean = firestoreOrNull() != null && authOrNull() != null
}
