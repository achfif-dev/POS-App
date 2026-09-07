package com.example.posapp.data.sync

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Satu baris katalog produk milik SATU cabang, dipakai untuk fitur "Cek Stok Semua Cabang". */
data class OutletProductRow(
    val outletId: String,
    val outletName: String,
    val sku: String,
    val name: String,
    val stock: Int,
    val sellPrice: Double,
    val updatedAt: Long,
)

/**
 * LINGKUP FITUR (baca dulu sebelum menambah): ini SATU ARAH (push-only) dan READ-ONLY di sisi
 * baca — setiap cabang mengirim snapshot katalognya sendiri ke Firestore, lalu admin bisa
 * MELIHAT (bukan mengedit) katalog+stok seluruh cabang dari satu HP untuk kebutuhan seperti
 * "cabang lain masih ada stok produk ini?" sebelum menyarankan pelanggan pindah cabang, atau
 * sebelum membuat mutasi stok manual antar cabang.
 *
 * SENGAJA TIDAK melakukan merge dua arah ke Room lokal (menulis balik produk cabang lain ke
 * database sendiri) — itu berisiko konflik ID/SKU dan bisa merusak sumber kebenaran data toko
 * yang selama ini murni per-device. Kalau ke depan dibutuhkan katalog terpusat sungguhan (satu
 * sumber harga/stok dipakai bersama), itu perubahan arsitektur besar di atas fondasi ini
 * (idealnya pusat data pindah ke Firestore sepenuhnya, bukan Room lokal per device).
 */
@Singleton
class OutletCatalogSyncRepository @Inject constructor() {

    private fun firestoreOrNull(): FirebaseFirestore? = runCatching { FirebaseFirestore.getInstance() }.getOrNull()
    private fun authOrNull(): FirebaseAuth? = runCatching { FirebaseAuth.getInstance() }.getOrNull()

    fun isConfigured(): Boolean = firestoreOrNull() != null && authOrNull() != null

    private suspend fun ensureSignedIn(auth: FirebaseAuth): Boolean {
        if (auth.currentUser != null) return true
        return try {
            auth.signInAnonymously().await(); true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * TEMUAN KEAMANAN (audit ulang): sebelumnya `pushCatalog` menulis ke
     * `outlet_catalog/{outletId}` TANPA mengikat kepemilikan sama sekali — firestore.rules hanya
     * mengecek `request.auth != null` (siapa pun yang sudah sign-in anonim, yaitu SEMUA
     * pengguna app ini). Artinya siapa pun yang tahu/menebak outletId cabang lain (UUID acak,
     * tapi tetap bisa saja bocor lewat log/screenshot) bisa MENIMPA katalog stok+harga cabang itu
     * dengan data palsu — mencemari layar "Cek Stok Semua Cabang" pemilik dengan info yang
     * salah. Sekarang setiap outletId dikunci ke `ownerUid` (identitas akun anonim Firebase
     * device itu) pada tulisan PERTAMA, sama seperti pola yang sudah dipakai
     * PaymentGatewayRepository — lihat firestore.rules untuk penegakan sisi server (client tetap
     * WAJIB dianggap tidak tepercaya, field ownerUid di sini hanya melengkapi rule, bukan
     * pengganti rule).
     */
    suspend fun pushCatalog(outletId: String, outletName: String, rows: List<OutletProductRow>): Boolean {
        val db = firestoreOrNull() ?: return false
        val auth = authOrNull() ?: return false
        if (!ensureSignedIn(auth)) return false
        val uid = auth.currentUser?.uid ?: return false
        return try {
            // Klaim/perbarui dokumen induk LEBIH DULU (sebelum menulis produk) — rule Firestore
            // untuk subkoleksi `products` mengecek ownerUid di dokumen induk ini lewat get(),
            // jadi kalau urutannya dibalik, sinkronisasi PERTAMA KALI (dokumen induk belum ada)
            // akan selalu ditolak rule karena get() belum menemukan ownerUid apa pun.
            db.collection("outlet_catalog").document(outletId)
                .set(
                    hashMapOf(
                        "outletName" to outletName,
                        "lastSyncedAt" to System.currentTimeMillis(),
                        "ownerUid" to uid,
                    ),
                    com.google.firebase.firestore.SetOptions.merge()
                )
                .await()

            rows.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { row ->
                    val docRef = db.collection("outlet_catalog").document(outletId)
                        .collection("products").document(row.sku)
                    batch.set(
                        docRef,
                        hashMapOf(
                            "outletName" to outletName,
                            "sku" to row.sku,
                            "name" to row.name,
                            "stock" to row.stock,
                            "sellPrice" to row.sellPrice,
                            "updatedAt" to row.updatedAt,
                        )
                    )
                }
                batch.commit().await()
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Dengarkan katalog SATU cabang lain secara realtime (dipakai layar "Cek Stok Semua Cabang"
     * saat admin memilih salah satu cabang untuk dilihat detailnya). */
    fun observeOutletCatalog(outletId: String): Flow<List<OutletProductRow>> = callbackFlow {
        val db = firestoreOrNull()
        if (db == null) { trySend(emptyList()); close(); return@callbackFlow }
        val registration = db.collection("outlet_catalog").document(outletId).collection("products")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) { trySend(emptyList()); return@addSnapshotListener }
                val rows = snapshot.documents.mapNotNull { doc ->
                    val sku = doc.getString("sku") ?: return@mapNotNull null
                    OutletProductRow(
                        outletId = outletId,
                        outletName = doc.getString("outletName") ?: outletId,
                        sku = sku,
                        name = doc.getString("name") ?: sku,
                        stock = (doc.getLong("stock") ?: 0L).toInt(),
                        sellPrice = doc.getDouble("sellPrice") ?: 0.0,
                        updatedAt = doc.getLong("updatedAt") ?: 0L,
                    )
                }
                trySend(rows)
            }
        awaitClose { registration.remove() }
    }

    /** Daftar cabang yang pernah sinkron (untuk dropdown pemilihan cabang di UI). */
    suspend fun listKnownOutlets(): List<Pair<String, String>> {
        val db = firestoreOrNull() ?: return emptyList()
        val auth = authOrNull() ?: return emptyList()
        if (!ensureSignedIn(auth)) return emptyList()
        return try {
            db.collection("outlet_catalog").get().await().documents.map { doc ->
                doc.id to (doc.getString("outletName") ?: doc.id)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
