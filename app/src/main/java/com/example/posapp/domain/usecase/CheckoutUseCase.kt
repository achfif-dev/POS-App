package com.example.posapp.domain.usecase

import com.example.posapp.data.local.entity.PaymentMethod
import com.example.posapp.data.local.entity.TransactionEntity
import com.example.posapp.data.local.entity.TransactionItemEntity
import com.example.posapp.data.repository.TransactionRepository
import com.example.posapp.domain.model.Cart
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

sealed class CheckoutResult {
    data class Success(val transactionId: Long, val invoiceNumber: String, val change: Double) : CheckoutResult()
    data class Error(val message: String) : CheckoutResult()
}

/**
 * Use case tunggal untuk memproses pembayaran:
 * - Validasi cart tidak kosong & pembayaran cukup
 * - Generate nomor invoice
 * - Simpan transaksi + item ke Room (mengurangi stok otomatis lewat repository)
 */
class CheckoutUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository
) {
    suspend operator fun invoke(
        cart: Cart,
        paymentMethod: PaymentMethod,
        amountPaid: Double,
        note: String? = null
    ): CheckoutResult {
        if (cart.isEmpty) return CheckoutResult.Error("Keranjang masih kosong")
        if (paymentMethod == PaymentMethod.CASH && amountPaid < cart.total) {
            return CheckoutResult.Error("Jumlah pembayaran kurang dari total")
        }

        val invoiceNumber = generateInvoiceNumber()
        val change = (amountPaid - cart.total).coerceAtLeast(0.0)

        val transaction = TransactionEntity(
            invoiceNumber = invoiceNumber,
            subtotal = cart.subtotal,
            taxPercent = cart.taxPercent,
            taxAmount = cart.taxAmount,
            discountAmount = cart.transactionDiscount,
            total = cart.total,
            paymentMethod = paymentMethod,
            amountPaid = amountPaid,
            changeAmount = change,
            note = note
        )

        val items = cart.lines.map { line ->
            TransactionItemEntity(
                transactionId = 0, // di-set ulang oleh repository saat insert
                productId = line.product.id,
                productNameSnapshot = line.product.name,
                priceSnapshot = line.product.sellPrice,
                quantity = line.quantity,
                itemDiscount = line.discount,
                itemNote = line.note
            )
        }

        return try {
            val txId = transactionRepository.checkout(transaction, items)
            CheckoutResult.Success(txId, invoiceNumber, change)
        } catch (e: Exception) {
            CheckoutResult.Error(e.message ?: "Gagal menyimpan transaksi")
        }
    }

    private fun generateInvoiceNumber(): String {
        val datePart = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
        return "INV-$datePart"
    }
}
