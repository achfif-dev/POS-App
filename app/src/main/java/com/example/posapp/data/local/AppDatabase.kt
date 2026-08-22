package com.example.posapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.posapp.data.local.dao.CategoryDao
import com.example.posapp.data.local.dao.ProductDao
import com.example.posapp.data.local.dao.StockAdjustmentDao
import com.example.posapp.data.local.dao.TransactionDao
import com.example.posapp.data.local.entity.CategoryEntity
import com.example.posapp.data.local.entity.PaymentMethod
import com.example.posapp.data.local.entity.ProductEntity
import com.example.posapp.data.local.entity.StockAdjustmentEntity
import com.example.posapp.data.local.entity.TransactionEntity
import com.example.posapp.data.local.entity.TransactionItemEntity

class Converters {
    @TypeConverter
    fun fromPaymentMethod(value: PaymentMethod): String = value.name

    @TypeConverter
    fun toPaymentMethod(value: String): PaymentMethod = PaymentMethod.valueOf(value)
}

@Database(
    entities = [
        ProductEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        TransactionItemEntity::class,
        StockAdjustmentEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun stockAdjustmentDao(): StockAdjustmentDao

    companion object {
        const val DATABASE_NAME = "pos_database"
    }
}
