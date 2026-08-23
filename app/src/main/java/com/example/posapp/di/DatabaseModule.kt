package com.example.posapp.di

import android.content.Context
import androidx.room.Room
import com.example.posapp.data.local.AppDatabase
import com.example.posapp.data.local.dao.CategoryDao
import com.example.posapp.data.local.dao.ProductDao
import com.example.posapp.data.local.dao.ProductVariantDao
import com.example.posapp.data.local.dao.StockAdjustmentDao
import com.example.posapp.data.local.dao.TransactionDao
import com.example.posapp.data.local.dao.UserDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            // Full offline app: tidak ada sinkronisasi server, aman melakukan migrasi destruktif
            // selama pengembangan awal. Ganti dengan Migration resmi sebelum rilis produksi.
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideProductDao(db: AppDatabase): ProductDao = db.productDao()

    @Provides
    fun provideCategoryDao(db: AppDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideTransactionDao(db: AppDatabase): TransactionDao = db.transactionDao()

    @Provides
    fun provideStockAdjustmentDao(db: AppDatabase): StockAdjustmentDao = db.stockAdjustmentDao()

    @Provides
    fun provideProductVariantDao(db: AppDatabase): ProductVariantDao = db.productVariantDao()

    @Provides
    fun provideUserDao(db: AppDatabase): UserDao = db.userDao()
}
