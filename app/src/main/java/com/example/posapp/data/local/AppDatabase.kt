package com.example.posapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.posapp.data.local.dao.CategoryDao
import com.example.posapp.data.local.dao.CustomerDao
import com.example.posapp.data.local.dao.ExpenseDao
import com.example.posapp.data.local.dao.ProductDao
import com.example.posapp.data.local.dao.ProductVariantDao
import com.example.posapp.data.local.dao.ShiftDao
import com.example.posapp.data.local.dao.StockAdjustmentDao
import com.example.posapp.data.local.dao.TransactionDao
import com.example.posapp.data.local.dao.UserDao
import com.example.posapp.data.local.entity.CategoryEntity
import com.example.posapp.data.local.entity.CustomerEntity
import com.example.posapp.data.local.entity.DebtPaymentEntity
import com.example.posapp.data.local.entity.ExpenseEntity
import com.example.posapp.data.local.entity.ExpensePeriod
import com.example.posapp.data.local.entity.PaymentMethod
import com.example.posapp.data.local.entity.ProductEntity
import com.example.posapp.data.local.entity.ProductVariantEntity
import com.example.posapp.data.local.entity.ShiftEntity
import com.example.posapp.data.local.entity.ShiftStatus
import com.example.posapp.data.local.entity.StockAdjustmentEntity
import com.example.posapp.data.local.entity.TransactionEntity
import com.example.posapp.data.local.entity.TransactionItemEntity
import com.example.posapp.data.local.entity.TransactionPaymentEntity
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

    @TypeConverter
    fun fromExpensePeriod(value: ExpensePeriod): String = value.name

    @TypeConverter
    fun toExpensePeriod(value: String): ExpensePeriod = ExpensePeriod.valueOf(value)

    @TypeConverter
    fun fromShiftStatus(value: ShiftStatus): String = value.name

    @TypeConverter
    fun toShiftStatus(value: String): ShiftStatus = ShiftStatus.valueOf(value)
}

@Database(
    entities = [
        ProductEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        TransactionItemEntity::class,
        StockAdjustmentEntity::class,
        ProductVariantEntity::class,
        UserEntity::class,
        TransactionPaymentEntity::class,
        ExpenseEntity::class,
        ShiftEntity::class,
        CustomerEntity::class,
        DebtPaymentEntity::class
    ],
    version = 10, // v10: users.pinSalt (migrasi hash PIN ke PBKDF2 bergaram) + unique index invoiceNumber
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
    abstract fun expenseDao(): ExpenseDao
    abstract fun shiftDao(): ShiftDao
    abstract fun customerDao(): CustomerDao

    companion object {
        const val DATABASE_NAME = "pos_database"
    }
}
