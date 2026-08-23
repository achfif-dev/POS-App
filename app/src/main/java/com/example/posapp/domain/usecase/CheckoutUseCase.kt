package com.example.posapp.domain.usecase

import com.example.posapp.data.local.entity.PaymentMethod
import com.example.posapp.data.local.entity.TransactionEntity
import com.example.posapp.data.local.entity.TransactionItemEntity
import com.example.posapp.data.local.entity.TransactionPaymentEntity
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

/** Satu baris pembayaran. Satu transaksi bisa punya beberapa (mis. sebagian Cash, sisanya QRIS). */
data class PaymentSplit(val method: PaymentMethod, val amount: Double)

/**
 * Use case tunggal untuk memproses pembayaran:
 * - Validasi cart tidak kosong & total pembayaran (bisa lebih dari satu metode/split) cukup
 * - Generate nomor invoice
 * - Simpan transaksi + item + rincian pembayaran ke Room (mengurangi stok otomatis lewat repository)
 */
class CheckoutUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository
) {
    suspend operator fun invoke(
        cart: Cart,
        payments: List<PaymentSplit>,
        note: String? = null,
        cashierName: String? = null
    ): CheckoutResult {
        if (cart.isEmpty) return CheckoutResult.Error("Keranjang masih kosong")
        val validPayments = payments.filter { it.amount > 0 }
        if (validPayments.isEmpty()) {
            return CheckoutResult.Error("Masukkan minimal satu metode pembayaran")
        }

        val totalPaid = validPayments.sumOf { it.amount }
        if (totalPaid < cart.total) {
            return CheckoutResult.Error("Total pembayaran belum mencukupi total belanja")
        }

        cart.lines.firstOrNull { it.quantity > it.availableStock }?.let { line ->
            return CheckoutResult.Error("Stok ${line.product.name}${line.variant?.let { " (${it.variantLabel})" } ?: ""} tidak mencukupi")
        }

        val invoiceNumber = generateInvoiceNumber()
        val change = (totalPaid - cart.total).coerceAtLeast(0.0)
        val primaryMethod = if (validPayments.size == 1) validPayments.first().method else PaymentMethod.MIXED

        val transaction = TransactionEntity(
            invoiceNumber = invoiceNumber,
            subtotal = cart.subtotal,
            taxPercent = cart.taxPercent,
            taxAmount = cart.taxAmount,
            discountAmount = cart.transactionDiscount,
            total = cart.total,
            paymentMethod = primaryMethod,
            amountPaid = totalPaid,
            changeAmount = change,
            note = note,
            cashierName = cashierName
        )

        val items = cart.lines.map { line ->
            TransactionItemEntity(
                transactionId = 0,
                productId = line.product.id,
                variantId = line.variant?.id,
                variantLabelSnapshot = line.variant?.variantLabel,
                productNameSnapshot = line.product.name + (line.variant?.let { " (${it.variantLabel})" } ?: ""),
                priceSnapshot = line.unitPrice,
                quantity = line.quantity,
                unitSnapshot = line.product.unit,
                itemDiscount = line.discount,
                itemNote = line.note
            )
        }

        val paymentEntities = validPayments.map {
            TransactionPaymentEntity(transactionId = 0, method = it.method, amount = it.amount)
        }

        return try {
            val txId = transactionRepository.checkout(transaction, items, paymentEntities)
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
