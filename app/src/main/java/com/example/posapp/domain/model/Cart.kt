package com.example.posapp.domain.model

import com.example.posapp.data.local.entity.ProductEntity

data class CartLine(
    val product: ProductEntity,
    val quantity: Int = 1,
    val discount: Double = 0.0,
    val note: String? = null
) {
    val lineTotal: Double
        get() = (product.sellPrice * quantity) - discount
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
