package com.example.posapp.data.repository

import androidx.room.withTransaction
import com.example.posapp.data.local.AppDatabase
import com.example.posapp.data.local.dao.DailySalesSummary
import com.example.posapp.data.local.dao.ProductDao
import com.example.posapp.data.local.dao.ProductVariantDao
import com.example.posapp.data.local.dao.TopSellingItem
import com.example.posapp.data.local.dao.TransactionDao
import com.example.posapp.data.local.entity.TransactionEntity
import com.example.posapp.data.local.entity.TransactionItemEntity
import com.example.posapp.data.local.entity.TransactionPaymentEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Dilempar saat stok produk/varian di database ternyata tidak cukup PERSIS SAAT checkout
 * dieksekusi (bukan cuma snapshot keranjang di UI) — misalnya kasir lain sudah menjual item
 * yang sama beberapa detik sebelumnya. Membatalkan seluruh transaksi (lihat withTransaction). */
class InsufficientStockException(message: String) : Exception(message)

@Singleton
class TransactionRepository @Inject constructor(
    private val appDatabase: AppDatabase,
    private val transactionDao: TransactionDao,
    private val productDao: ProductDao,
    private val productVariantDao: ProductVariantDao
) {
    fun observeAll(): Flow<List<TransactionEntity>> = transactionDao.observeAll()

    fun observeRange(start: Long, end: Long): Flow<List<TransactionEntity>> =
        transactionDao.observeRange(start, end)

    suspend fun getSalesSummary(start: Long, end: Long): DailySalesSummary =
        transactionDao.getSalesSummary(start, end)

    suspend fun getTopSellingItems(start: Long, end: Long, limit: Int = 10): List<TopSellingItem> =
        transactionDao.getTopSellingItems(start, end, limit)

    suspend fun getTransactionWithItems(id: Long): Pair<TransactionEntity, List<TransactionItemEntity>>? {
        val transaction = transactionDao.getById(id) ?: return null
        val items = transactionDao.getItems(id)
        return transaction to items
    }

    suspend fun getPayments(transactionId: Long): List<TransactionPaymentEntity> =
        transactionDao.getPayments(transactionId)

    /**
     * Menyimpan transaksi + item-nya (+ rincian split pembayaran bila ada) DAN mengurangi stok
     * tiap produk sebagai SATU transaksi database (`withTransaction`) — sebelumnya insert
     * transaksi dan pengurangan stok berjalan sebagai operasi terpisah, jadi kalau app crash di
     * tengah proses (mis. keranjang berisi banyak item), transaksi bisa tersimpan tapi stok
     * sebagian tidak ter-update. Dipanggil oleh CheckoutUseCase agar 1 checkout = 1 operasi
     * benar-benar konsisten (semua berhasil, atau semua dibatalkan/rollback).
     *
     * @throws InsufficientStockException bila stok riil di DB ternyata tidak cukup saat
     * dieksekusi (bukan cuma snapshot keranjang) — seluruh transaksi otomatis dibatalkan.
     */
    suspend fun checkout(
        transaction: TransactionEntity,
        items: List<TransactionItemEntity>,
        payments: List<TransactionPaymentEntity> = emptyList()
    ): Long = appDatabase.withTransaction {
        val txId = transactionDao.insertFullTransaction(transaction, items, payments)
        items.forEach { item ->
            val rowsAffected = if (item.variantId != null) {
                productVariantDao.decreaseStock(item.variantId, item.quantity)
            } else {
                productDao.decreaseStock(item.productId, item.quantity)
            }
            if (rowsAffected == 0) {
                throw InsufficientStockException(
                    "Stok ${item.productNameSnapshot} tidak mencukupi (mungkin baru saja terjual di transaksi lain)"
                )
            }
        }
        txId
    }

    /**
     * Mengoreksi transaksi yang sudah tersimpan (fitur "Riwayat Penjualan bisa diedit Admin",
     * untuk memperbaiki kesalahan input kasir). Menyesuaikan stok produk/varian berdasarkan
     * selisih quantity tiap item, mengembalikan stok utuh untuk item yang dihapus, lalu
     * menyimpan total baru + jejak audit (editedByName/editedAt).
     *
     * @param originalItems item transaksi SEBELUM diedit (dipakai untuk menghitung selisih stok).
     * @param updatedItems item yang tersisa setelah diedit (quantity/harga/diskon boleh berubah).
     * @param deletedItemIds id item yang dihapus sepenuhnya dari transaksi.
     */
    suspend fun updateTransactionWithCorrection(
        updatedTransaction: TransactionEntity,
        originalItems: List<TransactionItemEntity>,
        updatedItems: List<TransactionItemEntity>,
        deletedItemIds: List<Long>,
        editedByName: String
    ): Unit = appDatabase.withTransaction {
        // Item yang dihapus total: kembalikan stoknya lalu hapus baris item.
        deletedItemIds.forEach { itemId ->
            val original = originalItems.find { it.id == itemId } ?: return@forEach
            if (original.variantId != null) {
                productVariantDao.increaseStock(original.variantId, original.quantity)
            } else {
                productDao.increaseStock(original.productId, original.quantity)
            }
            transactionDao.deleteItem(itemId)
        }

        // Item yang masih ada: sesuaikan stok hanya sebesar SELISIH quantity lama vs baru.
        updatedItems.forEach { updated ->
            val original = originalItems.find { it.id == updated.id }
            val delta = updated.quantity - (original?.quantity ?: 0) // >0 = tambah qty (stok berkurang lagi)
            if (delta != 0) {
                val rowsAffected = if (updated.variantId != null) {
                    if (delta > 0) productVariantDao.decreaseStock(updated.variantId, delta)
                    else { productVariantDao.increaseStock(updated.variantId, -delta); 1 }
                } else {
                    if (delta > 0) productDao.decreaseStock(updated.productId, delta)
                    else { productDao.increaseStock(updated.productId, -delta); 1 }
                }
                if (rowsAffected == 0) {
                    throw InsufficientStockException(
                        "Stok ${updated.productNameSnapshot} tidak mencukupi untuk koreksi ini"
                    )
                }
            }
            transactionDao.updateItem(updated)
        }

        transactionDao.updateTransaction(
            updatedTransaction.copy(editedByName = editedByName, editedAt = System.currentTimeMillis())
        )
    }

    /**
     * Menghapus SATU transaksi sepenuhnya (koreksi Admin untuk transaksi yang salah input
     * total dari awal, mis. kasir checkout transaksi yang salah/ganda). Mengembalikan stok
     * seluruh item ke produk/varian terkait sebelum baris transaksi (beserta item-itemnya,
     * lewat FK CASCADE) dihapus. Tidak bisa dibatalkan.
     */
    suspend fun deleteTransaction(transactionId: Long): Unit = appDatabase.withTransaction {
        val items = transactionDao.getItems(transactionId)
        items.forEach { item ->
            if (item.variantId != null) {
                productVariantDao.increaseStock(item.variantId, item.quantity)
            } else {
                productDao.increaseStock(item.productId, item.quantity)
            }
        }
        transactionDao.deleteTransaction(transactionId)
    }
}
