package com.example.posapp.data.repository

import com.example.posapp.data.local.dao.DailySalesSummary
import com.example.posapp.data.local.dao.ProductDao
import com.example.posapp.data.local.dao.TopSellingItem
import com.example.posapp.data.local.dao.TransactionDao
import com.example.posapp.data.local.entity.TransactionEntity
import com.example.posapp.data.local.entity.TransactionItemEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val productDao: ProductDao
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

    /**
     * Menyimpan transaksi + item-nya secara atomik, lalu mengurangi stok tiap produk.
     * Dipanggil oleh CheckoutUseCase agar 1 checkout = 1 operasi konsisten.
     */
    suspend fun checkout(transaction: TransactionEntity, items: List<TransactionItemEntity>): Long {
        val txId = transactionDao.insertFullTransaction(transaction, items)
        items.forEach { item ->
            productDao.decreaseStock(item.productId, item.quantity)
        }
        return txId
    }
}
