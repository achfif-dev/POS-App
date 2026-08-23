package com.example.posapp.data.printer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections
import com.example.posapp.data.local.entity.PaymentMethod
import com.example.posapp.data.local.entity.TransactionEntity
import com.example.posapp.data.local.entity.TransactionItemEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

sealed class PrintResult {
    object Success : PrintResult()
    data class Error(val message: String) : PrintResult()
}

/**
 * Menangani koneksi & pencetakan struk ke thermal printer Bluetooth (ESC/POS)
 * menggunakan library DantSu/ESCPOS-ThermalPrinter-Android.
 *
 * Alur penggunaan:
 * 1. Pastikan printer sudah di-pair lewat pengaturan Bluetooth Android terlebih dahulu.
 * 2. Panggil [listPairedPrinters] untuk menampilkan daftar printer ke pengguna (opsional,
 *    jika ada lebih dari satu printer terpasang).
 * 3. Panggil [printReceipt] dengan data transaksi untuk mencetak struk.
 */
@Singleton
class PrinterRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val rupiah = NumberFormat.getCurrencyInstance(Locale("in", "ID"))
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    /** Nama printer Bluetooth yang sudah di-pair di sistem (untuk ditampilkan sebagai pilihan). */
    fun listPairedPrinters(): List<String> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return emptyList()
        }
        return try {
            BluetoothPrintersConnections().list?.map { it.device.name ?: "Unknown Printer" } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun printReceipt(
        storeName: String,
        transaction: TransactionEntity,
        items: List<TransactionItemEntity>,
        storeAddress: String = "",
        receiptFooter: String = "Terima kasih!"
    ): PrintResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return PrintResult.Error("Izin Bluetooth belum diberikan. Aktifkan izin Bluetooth di pengaturan aplikasi.")
        }
        return try {
            val connection: BluetoothConnection = BluetoothPrintersConnections.selectFirstPaired()
                ?: return PrintResult.Error("Tidak ada printer Bluetooth yang terpasang/di-pair")

            // 384 dots ~ printer thermal 58mm umum. Ganti ke 576 untuk printer 80mm.
            val printer = EscPosPrinter(connection, 203, 48f, 32)

            val sb = StringBuilder()
            sb.append("[C]<b>$storeName</b>\n")
            if (storeAddress.isNotBlank()) sb.append("[C]$storeAddress\n")
            sb.append("[C]--------------------------------\n")
            sb.append("[L]No: ${transaction.invoiceNumber}\n")
            sb.append("[L]${dateFormat.format(Date(transaction.createdAt))}\n")
            sb.append("[C]--------------------------------\n")

            items.forEach { item ->
                sb.append("[L]${item.productNameSnapshot}\n")
                sb.append("[L]${item.quantity} x ${rupiah.format(item.priceSnapshot)}[R]${rupiah.format(item.lineTotal)}\n")
            }

            sb.append("[C]--------------------------------\n")
            sb.append("[L]Subtotal[R]${rupiah.format(transaction.subtotal)}\n")
            sb.append("[L]Diskon[R]-${rupiah.format(transaction.discountAmount)}\n")
            sb.append("[L]Pajak (${transaction.taxPercent}%)[R]${rupiah.format(transaction.taxAmount)}\n")
            sb.append("[L]<b>Total</b>[R]<b>${rupiah.format(transaction.total)}</b>\n")
            sb.append("[C]--------------------------------\n")
            sb.append("[L]Bayar (${paymentLabel(transaction.paymentMethod)})[R]${rupiah.format(transaction.amountPaid)}\n")
            sb.append("[L]Kembali[R]${rupiah.format(transaction.changeAmount)}\n")
            sb.append("[C]--------------------------------\n")
            sb.append("[C]$receiptFooter\n")
            sb.append("[L]\n")

            printer.printFormattedTextAndCut(sb.toString())
            PrintResult.Success
        } catch (e: Exception) {
            PrintResult.Error(e.message ?: "Gagal mencetak struk. Pastikan printer menyala dan sudah di-pair.")
        }
    }

    private fun paymentLabel(method: PaymentMethod): String = when (method) {
        PaymentMethod.CASH -> "Cash"
        PaymentMethod.DEBIT_CREDIT -> "Debit/Kredit"
        PaymentMethod.QRIS -> "QRIS"
    }
}
