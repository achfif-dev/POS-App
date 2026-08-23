package com.example.posapp.domain.model

import com.example.posapp.data.local.entity.ProductEntity
import com.example.posapp.data.local.entity.ProductVariantEntity

data class CartLine(
    val product: ProductEntity,
    val variant: ProductVariantEntity? = null, // diisi bila produk punya matrix varian
    val quantity: Int = 1,
    val discount: Double = 0.0,
    val note: String? = null
) {
    /** Kunci unik baris di keranjang: kombinasi produk + varian (bila ada). */
    val lineKey: String get() = "${product.id}:${variant?.id ?: 0}"

    val unitPrice: Double get() = variant?.priceOverride ?: product.sellPrice

    val lineTotal: Double
        get() = (unitPrice * quantity) - discount

    /** Stok yang relevan untuk validasi keranjang: stok varian jika ada, atau stok produk. */
    val availableStock: Int get() = variant?.stock ?: product.stock
}

data class Cart(
    val lines: List<CartLine> = emptyList(),
    val transactionDiscount: Double = 0.0,
    val taxPercent: Double = 11.0 // default PPN Indonesia
) {
    val subtotal: Double
        get() = lines.sumOf { it.lineTotal }

    val taxAmount: Double
        get() = ((subtotal - transactionDiscount).coerceAtLeast(0.0)) * (taxPercent / 100.0)

    val total: Double
        get() = (subtotal - transactionDiscount + taxAmount).coerceAtLeast(0.0)

    val isEmpty: Boolean get() = lines.isEmpty()
}
