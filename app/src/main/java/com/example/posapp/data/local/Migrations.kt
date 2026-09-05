package com.example.posapp.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migrasi resmi Room, menggantikan `.fallbackToDestructiveMigration()` yang sebelumnya dipakai
 * (lihat DatabaseModule) — database sudah dipakai di instalasi nyata (versi 9), jadi migrasi
 * destruktif akan MENGHAPUS seluruh data toko (produk, transaksi, stok, piutang) begitu skema
 * berubah lagi. Mulai dari sini, setiap kenaikan versi WAJIB punya Migration eksplisit di sini.
 *
 * v9 -> v10:
 * 1. Tambah kolom `users.pinSalt` (nullable) untuk migrasi hash PIN dari SHA-256 polos ke
 *    PBKDF2WithHmacSHA256 bergaram (lihat UserRepository). Baris lama (pinSalt masih NULL)
 *    tetap bisa login seperti biasa lalu otomatis di-upgrade begitu login berikutnya berhasil.
 * 2. Tambah unique index pada `transactions.invoiceNumber`. Sebelum index dibuat, bereskan dulu
 *    kemungkinan duplikat lama (instalasi yang sudah lama berjalan dengan generator invoice
 *    lama yang cuma presisi detik) dengan memberi sufiks pada baris duplikat ke-2 dst., supaya
 *    migrasi tidak gagal/crash di tengah data produksi yang sudah ada.
 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE users ADD COLUMN pinSalt TEXT DEFAULT NULL")

        val dupeInvoices = mutableListOf<String>()
        db.query("SELECT invoiceNumber FROM transactions GROUP BY invoiceNumber HAVING COUNT(*) > 1").use { cursor ->
            while (cursor.moveToNext()) {
                dupeInvoices.add(cursor.getString(0))
            }
        }
        dupeInvoices.forEach { invoiceNumber ->
            db.query(
                SimpleSQLiteQuery(
                    "SELECT id FROM transactions WHERE invoiceNumber = ? ORDER BY id ASC",
                    arrayOf(invoiceNumber)
                )
            ).use { idCursor ->
                var n = 0
                while (idCursor.moveToNext()) {
                    n++
                    if (n == 1) continue // baris pertama (paling lama) tetap pakai invoice number asli
                    val id = idCursor.getLong(0)
                    db.execSQL(
                        "UPDATE transactions SET invoiceNumber = invoiceNumber || '-DUP$n' WHERE id = $id"
                    )
                }
            }
        }

        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_transactions_invoiceNumber ON transactions(invoiceNumber)"
        )
    }
}

/**
 * v10 -> v11: Fitur Retur/Refund & Void Transaksi.
 * 1. Kolom baru di `transactions`: `status` (COMPLETED/VOIDED), `returnedAmount` (akumulasi
 *    nominal yang sudah diretur), `voidedByName` & `voidedAt` (jejak audit void).
 * 2. Tabel baru `transaction_returns` (header retur/void) & `transaction_return_items`
 *    (rincian item yang diretur per baris retur) — lihat ReturnEntity.kt untuk penjelasan model.
 * Baris transaksi lama otomatis dianggap status='COMPLETED' & returnedAmount=0 (DEFAULT), jadi
 * tidak ada data yang berubah maknanya untuk transaksi yang sudah ada.
 */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transactions ADD COLUMN status TEXT NOT NULL DEFAULT 'COMPLETED'")
        db.execSQL("ALTER TABLE transactions ADD COLUMN returnedAmount REAL NOT NULL DEFAULT 0.0")
        db.execSQL("ALTER TABLE transactions ADD COLUMN voidedByName TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE transactions ADD COLUMN voidedAt INTEGER DEFAULT NULL")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS transaction_returns (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                transactionId INTEGER NOT NULL,
                isVoid INTEGER NOT NULL,
                reason TEXT NOT NULL,
                refundAmount REAL NOT NULL,
                refundMethod TEXT DEFAULT NULL,
                processedByName TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(transactionId) REFERENCES transactions(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_transaction_returns_transactionId ON transaction_returns(transactionId)"
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS transaction_return_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                returnId INTEGER NOT NULL,
                transactionItemId INTEGER NOT NULL,
                productId INTEGER NOT NULL,
                variantId INTEGER DEFAULT NULL,
                quantityReturned INTEGER NOT NULL,
                restocked INTEGER NOT NULL,
                FOREIGN KEY(returnId) REFERENCES transaction_returns(id) ON DELETE CASCADE,
                FOREIGN KEY(transactionItemId) REFERENCES transaction_items(id) ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_transaction_return_items_returnId ON transaction_return_items(returnId)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_transaction_return_items_transactionItemId ON transaction_return_items(transactionItemId)"
        )
    }
}
