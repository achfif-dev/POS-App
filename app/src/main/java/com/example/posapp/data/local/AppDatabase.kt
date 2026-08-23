package com.example.posapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.posapp.data.local.dao.CategoryDao
import com.example.posapp.data.local.dao.ProductDao
import com.example.posapp.data.local.dao.ProductVariantDao
import com.example.posapp.data.local.dao.StockAdjustmentDao
import com.example.posapp.data.local.dao.TransactionDao
import com.example.posapp.data.local.dao.UserDao
import com.example.posapp.data.local.entity.CategoryEntity
import com.example.posapp.data.local.entity.PaymentMethod
import com.example.posapp.data.local.entity.ProductEntity
import com.example.posapp.data.local.entity.ProductVariantEntity
import com.example.posapp.data.local.entity.StockAdjustmentEntity
import com.example.posapp.data.local.entity.TransactionEntity
import com.example.posapp.data.local.entity.TransactionItemEntity
import com.example.posapp.data.local.entity.UserEntity
import com.example.posapp.data.local.entity.UserRole

class Converters {
    @TypeConverter
    fun fromPaymentMethod(value: PaymentMethod): String = value.name

    @TypeConverter
    fun toPaymentMethod(value: String): PaymentMethod = PaymentMethod.valueOf(value)

    @TypeConverter
    fun fromUserRole(value: UserRole): String = value.name

    @TypeConverter
    fun toUserRole(value: String): UserRole = UserRole.valueOf(value)
}

@Database(
    entities = [
        ProductEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        TransactionItemEntity::class,
        StockAdjustmentEntity::class,
        ProductVariantEntity::class,
        UserEntity::class
    ],
    version = 2,
    exportSchema = false // App full offline, tidak butuh histori schema untuk migrasi terjadwal server
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun stockAdjustmentDao(): StockAdjustmentDao
    abstract fun productVariantDao(): ProductVariantDao
    abstract fun userDao(): UserDao

    companion object {
        const val DATABASE_NAME = "pos_database"
    }
}
