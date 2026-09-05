package com.example.posapp.data.local.dao

import androidx.room.*
import com.example.posapp.data.local.entity.TransactionEntity
import com.example.posapp.data.local.entity.TransactionItemEntity
import com.example.posapp.data.local.entity.TransactionPaymentEntity
import com.example.posapp.data.local.entity.TransactionReturnEntity
import com.example.posapp.data.local.entity.TransactionReturnItemEntity
import kotlinx.coroutines.flow.Flow

data class DailySalesSummary(
    val totalRevenue: Double,
    val totalTransactions: Int,
    val totalGrossProfit: Double
)

data class TopSellingItem(
    val productId: Long,
    val productName: String,
    val totalQty: Int,
    val totalRevenue: Double
)

@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Insert
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Update
    suspend fun updateItem(item: TransactionItemEntity)

    @Query("DELETE FROM transaction_items WHERE id = :itemId")
    suspend fun deleteItem(itemId: Long)

    // Menghapus transaksi sepenuhnya (koreksi Admin: transaksi salah total, bukan cuma 1 item).
    // transaction_items punya FK CASCADE ke transactions, jadi item ikut terhapus otomatis.
    // CATATAN: sejak ada fitur Retur/Void (lihat TransactionRepository.voidTransaction), fungsi
    // hapus permanen ini SUDAH TIDAK DIPAKAI dari UI lagi — void lebih baik karena transaksinya
    // tetap tercatat untuk audit, tidak hilang begitu saja. Dibiarkan ada untuk keperluan lain
    // (mis. pembersihan data uji coba), tapi jangan dipanggil dari layar Riwayat Penjualan lagi.
    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransaction(id: Long)

    @Insert
    suspend fun insertItems(items: List<TransactionItemEntity>)

    @Insert
    suspend fun insertPayments(payments: List<TransactionPaymentEntity>)

    @Query("SELECT * FROM transaction_payments WHERE transactionId = :transactionId")
    suspend fun getPayments(transactionId: Long): List<TransactionPaymentEntity>

    @Insert
    suspend fun insertReturn(returnEntity: TransactionReturnEntity): Long

    @Insert
    suspend fun insertReturnItems(items: List<TransactionReturnItemEntity>)

    @Query("SELECT * FROM transaction_returns WHERE transactionId = :transactionId ORDER BY createdAt DESC")
    suspend fun getReturnsForTransaction(transactionId: Long): List<TransactionReturnEntity>

    @Query("SELECT * FROM transaction_return_items WHERE returnId = :returnId")
    suspend fun getReturnItems(returnId: Long): List<TransactionReturnItemEntity>

    // Total qty yang SUDAH pernah diretur untuk satu baris transaction_items — dipakai untuk
    // mencegah retur melebihi qty yang benar-benar dibeli (retur bisa terjadi bertahap).
    @Query("SELECT COALESCE(SUM(quantityReturned), 0) FROM transaction_return_items WHERE transactionItemId = :transactionItemId")
    suspend fun getReturnedQuantityForItem(transactionItemId: Long): Int

    @Query("UPDATE transactions SET returnedAmount = returnedAmount + :amount WHERE id = :id")
    suspend fun addReturnedAmount(id: Long, amount: Double)

    @Query("UPDATE transactions SET status = 'VOIDED', voidedByName = :byName, voidedAt = :at WHERE id = :id")
    suspend fun markVoided(id: Long, byName: String, at: Long)

    @Transaction
    suspend fun insertFullTransaction(
        transaction: TransactionEntity,
        items: List<TransactionItemEntity>,
        payments: List<TransactionPaymentEntity> = emptyList()
    ): Long {
        val txId = insertTransaction(transaction)
        insertItems(items.map { it.copy(transactionId = txId) })
        if (payments.isNotEmpty()) {
            insertPayments(payments.map { it.copy(transactionId = txId) })
        }
        return txId
    }

    @Query("SELECT * FROM transactions ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE createdAt BETWEEN :start AND :end ORDER BY createdAt DESC")
    fun observeRange(start: Long, end: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transaction_items WHERE transactionId = :transactionId")
    suspend fun getItems(transactionId: Long): List<TransactionItemEntity>

    // Laba kotor = SUM((priceSnapshot - purchasePrice) * qty) - itemDiscount
    // PERBAIKAN: query lama menggabungkan SUM(t.total) dalam SATU SELECT yang sudah di-JOIN ke
    // transaction_items — akibatnya t.total ikut terhitung BERULANG sebanyak jumlah item di
    // transaksi itu (transaksi dengan 3 item, total-nya kehitung 3x di totalRevenue). Sekarang
    // totalRevenue & totalTransactions dihitung dari subquery terpisah yang cuma baca tabel
    // transactions sendiri, sehingga tidak ikut kelipatan oleh JOIN. totalGrossProfit tetap dari
    // subquery ber-JOIN karena itu memang harus dihitung per-baris item.
    // Transaksi VOIDED dikeluarkan dari semua perhitungan (dianggap tidak pernah terjadi); untuk
    // transaksi yang diretur sebagian, returnedAmount dikurangkan dari totalRevenue.
    @Query(
        """
        SELECT
            (SELECT COALESCE(SUM(total - returnedAmount), 0) FROM transactions
                WHERE createdAt BETWEEN :start AND :end AND status != 'VOIDED') AS totalRevenue,
            (SELECT COUNT(*) FROM transactions
                WHERE createdAt BETWEEN :start AND :end AND status != 'VOIDED') AS totalTransactions,
            (SELECT COALESCE(SUM((ti.priceSnapshot - p.purchasePrice) * ti.quantity - ti.itemDiscount), 0)
                FROM transaction_items ti
                JOIN transactions t ON t.id = ti.transactionId
                JOIN products p ON p.id = ti.productId
                WHERE t.createdAt BETWEEN :start AND :end AND t.status != 'VOIDED') AS totalGrossProfit
        """
    )
    suspend fun getSalesSummary(start: Long, end: Long): DailySalesSummary

    // Total nilai laba dari item yang SUDAH DIRETUR pada rentang tanggal ini — dikurangkan dari
    // totalGrossProfit di repository (lihat TransactionRepository.getSalesSummary), supaya barang
    // yang baliknya ke stok tidak dianggap laba lagi. Diskon per-item diprorata sesuai porsi qty
    // yang diretur. Retur dari transaksi VOIDED tidak mungkin ada (void tidak punya baris retur
    // biasa), jadi tidak perlu filter status di sini.
    @Query(
        """
        SELECT COALESCE(SUM(
            (ti.priceSnapshot - p.purchasePrice) * tri.quantityReturned
            - (ti.itemDiscount * tri.quantityReturned * 1.0 / ti.quantity)
        ), 0)
        FROM transaction_return_items tri
        JOIN transaction_items ti ON ti.id = tri.transactionItemId
        JOIN transactions t ON t.id = ti.transactionId
        JOIN products p ON p.id = ti.productId
        WHERE t.createdAt BETWEEN :start AND :end
        """
    )
    suspend fun getReturnedGrossProfitInRange(start: Long, end: Long): Double

    // Dipakai saat tutup shift: total tunai (CASH) yang seharusnya masuk laci selama shift
    // berjalan, dihitung dari transaction_payments (bukan transactions.total) supaya transaksi
    // split payment (MIXED) hanya menyumbang porsi CASH-nya saja, bukan total transaksi penuh.
    // Transaksi VOIDED dikeluarkan (uangnya dianggap tidak pernah masuk laci).
    @Query(
        """
        SELECT COALESCE(SUM(tp.amount), 0)
        FROM transaction_payments tp
        JOIN transactions t ON t.id = tp.transactionId
        WHERE tp.method = 'CASH' AND t.createdAt BETWEEN :start AND :end AND t.status != 'VOIDED'
    """
    )
    suspend fun getCashTotalInRange(start: Long, end: Long): Double

    @Query(
        """
        SELECT 
            ti.productId as productId,
            ti.productNameSnapshot as productName,
            SUM(ti.quantity) as totalQty,
            SUM(ti.lineTotalHelper) as totalRevenue
        FROM (
            SELECT *, (priceSnapshot * quantity - itemDiscount) as lineTotalHelper FROM transaction_items
        ) ti
        JOIN transactions t ON t.id = ti.transactionId
        WHERE t.createdAt BETWEEN :start AND :end AND t.status != 'VOIDED'
        GROUP BY ti.productId
        ORDER BY totalQty DESC
        LIMIT :limit
    """
    )
    suspend fun getTopSellingItems(start: Long, end: Long, limit: Int = 10): List<TopSellingItem>
}
