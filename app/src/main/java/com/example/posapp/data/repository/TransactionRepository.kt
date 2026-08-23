package com.example.posapp.data.repository

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

@Singleton
class TransactionRepository @Inject constructor(
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
     * Menyimpan transaksi + item-nya (+ rincian split pembayaran bila ada) secara atomik,
     * lalu mengurangi stok tiap produk. Dipanggil oleh CheckoutUseCase agar 1 checkout = 1
     * operasi konsisten.
     */
    suspend fun checkout(
        transaction: TransactionEntity,
        items: List<TransactionItemEntity>,
        payments: List<TransactionPaymentEntity> = emptyList()
    ): Long {
        val txId = transactionDao.insertFullTransaction(transaction, items, payments)
        items.forEach { item ->
            if (item.variantId != null) {
                productVariantDao.decreaseStock(item.variantId, item.quantity)
            } else {
                productDao.decreaseStock(item.productId, item.quantity)
            }
        }
        return txId
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
    ) {
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
                if (updated.variantId != null) {
                    if (delta > 0) productVariantDao.decreaseStock(updated.variantId, delta)
                    else productVariantDao.increaseStock(updated.variantId, -delta)
                } else {
                    if (delta > 0) productDao.decreaseStock(updated.productId, delta)
                    else productDao.increaseStock(updated.productId, -delta)
                }
            }
            transactionDao.updateItem(updated)
        }

        transactionDao.updateTransaction(
            updatedTransaction.copy(editedByName = editedByName, editedAt = System.currentTimeMillis())
        )
    }
}
