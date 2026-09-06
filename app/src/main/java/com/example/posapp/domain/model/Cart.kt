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
    val taxPercent: Double = 11.0, // default PPN Indonesia
    /** Jumlah poin loyalitas yang ditukar pelanggan pada transaksi ini (v13) — 0 jika fitur
     * loyalitas nonaktif atau pelanggan tidak menukar poin. Dikurangi dari saldo poin
     * pelanggan setelah checkout berhasil, lihat CheckoutUseCase. */
    val loyaltyPointsRedeemed: Long = 0,
    /** Nilai Rupiah dari [loyaltyPointsRedeemed] (points * StoreProfile.loyaltyPointValueRupiah,
     * sudah dibatasi PosViewModel agar tidak melebihi saldo poin maupun subtotal belanja). */
    val loyaltyDiscount: Double = 0.0,
    /** Nomor meja atau nama pemesan (v13) — hanya relevan saat StoreProfile.tableTaggingEnabled
     * aktif (mode Resto/Kafe). Disimpan ke TransactionEntity.note saat checkout, lihat
     * PosViewModel.checkout(). Null/kosong = tidak dipakai (mode Retail/Umum). */
    val tableTag: String? = null
) {
    val subtotal: Double
        get() = lines.sumOf { it.lineTotal }

    /** Total potongan sebelum pajak: diskon manual transaksi + penukaran poin loyalitas. */
    val totalDiscount: Double
        get() = transactionDiscount + loyaltyDiscount

    val taxAmount: Double
        get() = ((subtotal - totalDiscount).coerceAtLeast(0.0)) * (taxPercent / 100.0)

    val total: Double
        get() = (subtotal - totalDiscount + taxAmount).coerceAtLeast(0.0)

    val isEmpty: Boolean get() = lines.isEmpty()
}
