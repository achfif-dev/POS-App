package com.example.posapp.data.local.dao

import androidx.room.*
import com.example.posapp.data.local.entity.TransactionEntity
import com.example.posapp.data.local.entity.TransactionItemEntity
import com.example.posapp.data.local.entity.TransactionPaymentEntity
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

    @Insert
    suspend fun insertItems(items: List<TransactionItemEntity>)

    @Insert
    suspend fun insertPayments(payments: List<TransactionPaymentEntity>)

    @Query("SELECT * FROM transaction_payments WHERE transactionId = :transactionId")
    suspend fun getPayments(transactionId: Long): List<TransactionPaymentEntity>

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
    @Query("""
        SELECT 
            COALESCE(SUM(t.total), 0) AS totalRevenue,
            COUNT(DISTINCT t.id) AS totalTransactions,
            COALESCE(SUM((ti.priceSnapshot - p.purchasePrice) * ti.quantity - ti.itemDiscount), 0) AS totalGrossProfit
        FROM transactions t
        JOIN transaction_items ti ON ti.transactionId = t.id
        JOIN products p ON p.id = ti.productId
        WHERE t.createdAt BETWEEN :start AND :end
    """)
    suspend fun getSalesSummary(start: Long, end: Long): DailySalesSummary

    @Query("""
        SELECT 
            ti.productId as productId,
            ti.productNameSnapshot as productName,
            SUM(ti.quantity) as totalQty,
            SUM(ti.lineTotalHelper) as totalRevenue
        FROM (
            SELECT *, (priceSnapshot * quantity - itemDiscount) as lineTotalHelper FROM transaction_items
        ) ti
        JOIN transactions t ON t.id = ti.transactionId
        WHERE t.createdAt BETWEEN :start AND :end
        GROUP BY ti.productId
        ORDER BY totalQty DESC
        LIMIT :limit
    """)
    suspend fun getTopSellingItems(start: Long, end: Long, limit: Int = 10): List<TopSellingItem>
}
