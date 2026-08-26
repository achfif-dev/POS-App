package com.example.posapp.domain.usecase

import android.database.sqlite.SQLiteConstraintException
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
        cashierName: String? = null,
        customerId: Long? = null
    ): CheckoutResult {
        when (val validation = CheckoutValidator.validate(cart, payments, customerId)) {
            is CheckoutValidator.ValidationResult.Invalid -> return CheckoutResult.Error(validation.message)
            CheckoutValidator.ValidationResult.Valid -> Unit
        }

        val validPayments = payments.filter { it.amount > 0 }
        val totalPaid = CheckoutValidator.totalPaid(validPayments)
        val change = (totalPaid - cart.total).coerceAtLeast(0.0)
        val primaryMethod = if (validPayments.size == 1) validPayments.first().method else PaymentMethod.MIXED

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

        // `transactions.invoiceNumber` punya unique index di DB (lihat Migrations.kt). Nomor
        // invoice sudah dibuat presisi milidetik + sufiks acak supaya tabrakan nyaris mustahil,
        // tapi tetap coba ulang beberapa kali dengan nomor baru kalau entah bagaimana tetap
        // bentrok (mis. jam sistem device diubah manual), daripada checkout gagal total.
        var lastError: Exception? = null
        repeat(MAX_INVOICE_RETRY) {
            val invoiceNumber = generateInvoiceNumber()
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
                cashierName = cashierName,
                customerId = customerId
            )
            try {
                val txId = transactionRepository.checkout(transaction, items, paymentEntities)
                return CheckoutResult.Success(txId, invoiceNumber, change)
            } catch (e: SQLiteConstraintException) {
                lastError = e // nomor invoice bentrok, ulangi dengan nomor baru
            } catch (e: Exception) {
                return CheckoutResult.Error(e.message ?: "Gagal menyimpan transaksi")
            }
        }
        return CheckoutResult.Error(lastError?.message ?: "Gagal menyimpan transaksi (nomor invoice bentrok)")
    }

    private fun generateInvoiceNumber(): String {
        val datePart = SimpleDateFormat("yyyyMMdd-HHmmssSSS", Locale.getDefault()).format(Date())
        val randomSuffix = (100..999).random()
        return "INV-$datePart-$randomSuffix"
    }

    private companion object {
        const val MAX_INVOICE_RETRY = 3
    }
}
