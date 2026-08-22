package com.example.posapp.data.repository

import com.example.posapp.data.local.dao.ProductDao
import com.example.posapp.data.local.dao.StockAdjustmentDao
import com.example.posapp.data.local.entity.ProductEntity
import com.example.posapp.data.local.entity.StockAdjustmentEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductRepository @Inject constructor(
    private val productDao: ProductDao,
    private val stockAdjustmentDao: StockAdjustmentDao
) {
    fun observeAll(): Flow<List<ProductEntity>> = productDao.observeAll()

    fun search(query: String, categoryId: Long?): Flow<List<ProductEntity>> =
        productDao.search(query, categoryId)

    fun observeLowStock(): Flow<List<ProductEntity>> = productDao.observeLowStock()

    suspend fun findBySku(sku: String): ProductEntity? = productDao.findBySku(sku)

    suspend fun upsert(product: ProductEntity): Long =
        if (product.id == 0L) productDao.insert(product) else {
            productDao.update(product); product.id
        }

    suspend fun delete(id: Long) = productDao.softDelete(id)

    /** Penyesuaian stok manual (Stock In / Stock Out / Opname) dengan pencatatan riwayat. */
    suspend fun adjustStock(productId: Long, type: String, quantity: Int, reason: String?) {
        when (type) {
            "IN" -> productDao.increaseStock(productId, quantity)
            "OUT" -> productDao.decreaseStock(productId, quantity)
            "OPNAME" -> {
                val current = productDao.findById(productId)?.stock ?: 0
                val diff = quantity - current
                if (diff > 0) productDao.increaseStock(productId, diff)
                if (diff < 0) productDao.decreaseStock(productId, -diff)
            }
        }
        stockAdjustmentDao.insert(
            StockAdjustmentEntity(productId = productId, type = type, quantity = quantity, reason = reason)
        )
    }

    suspend fun getAllForExport(): List<ProductEntity> = productDao.getAllForExport()
}
