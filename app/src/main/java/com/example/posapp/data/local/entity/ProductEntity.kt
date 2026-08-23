package com.example.posapp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "products",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("sku", unique = true), Index("categoryId")]
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sku: String,               // barcode / SKU, harus unik
    val categoryId: Long? = null,
    val purchasePrice: Double,      // harga beli
    val sellPrice: Double,          // harga jual
    val stock: Int,
    val lowStockThreshold: Int = 5, // ambang batas alert stok tipis
    val photoPath: String? = null,  // path foto lokal di internal storage
    val variantName: String? = null,   // label bebas (dipakai jika produk TIDAK punya matrix varian)
    val hasVariants: Boolean = false,  // true = kelola stok lewat ProductVariantEntity (matrix Ukuran x Warna)
    val discountPercent: Double = 0.0, // diskon produk permanen (%)
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
