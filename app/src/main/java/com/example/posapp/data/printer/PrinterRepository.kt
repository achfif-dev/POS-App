package com.example.posapp.data.printer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.content.ContextCompat
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections
import com.dantsu.escposprinter.textparser.PrinterTextParserImg
import com.example.posapp.data.local.entity.PaymentMethod
import com.example.posapp.data.local.entity.TransactionEntity
import com.example.posapp.data.local.entity.TransactionItemEntity
import com.example.posapp.data.export.ReceiptStrings
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

    /**
     * Sebelumnya SELALU memakai [BluetoothPrintersConnections.selectFirstPaired] walau
     * [listPairedPrinters] menampilkan semua printer ter-pairing — kalau toko punya >1 printer,
     * pengguna tidak pernah bisa benar-benar memilih yang mana dipakai. Sekarang cocokkan dulu
     * dengan [printerName] yang tersimpan di Pengaturan; kalau tidak diisi atau tidak ketemu
     * (mis. printer itu sudah di-unpair), fallback ke printer ter-pairing pertama seperti biasa.
     */
    private fun selectConnection(printerName: String?): BluetoothConnection? {
        // Pengecekan izin di FUNGSI INI SENDIRI (bukan cuma di printReceipt yang memanggilnya) —
        // Lint (MissingPermission) butuh bukti pengecekan izin di scope yang sama dengan
        // pemanggilan API yang butuh BLUETOOTH_CONNECT (device.name), persis pola yang sudah
        // dipakai di listPairedPrinters().
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        val allPaired = try {
            BluetoothPrintersConnections().list
        } catch (e: Exception) {
            null
        }
        if (!printerName.isNullOrBlank()) {
            allPaired?.firstOrNull { it.device.name == printerName }?.let { return it }
        }
        return BluetoothPrintersConnections.selectFirstPaired()
    }

    fun printReceipt(
        storeName: String,
        transaction: TransactionEntity,
        items: List<TransactionItemEntity>,
        storeAddress: String = "",
        receiptFooter: String = "Terima kasih!",
        logoImagePath: String? = null,
        language: String = "id",
        printerName: String? = null
    ): PrintResult {
        val strings = ReceiptStrings.forLanguage(language)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return PrintResult.Error("Izin Bluetooth belum diberikan. Aktifkan izin Bluetooth di pengaturan aplikasi.")
        }
        return try {
            val connection: BluetoothConnection = selectConnection(printerName)
                ?: return PrintResult.Error("Tidak ada printer Bluetooth yang terpasang/di-pair")

            // 384 dots ~ printer thermal 58mm umum. Ganti ke 576 untuk printer 80mm.
            val printer = EscPosPrinter(connection, 203, 48f, 32)

            val sb = StringBuilder()
            val logoBitmap = logoImagePath?.let { path ->
                try {
                    BitmapFactory.decodeFile(path)
                } catch (e: Exception) {
                    null
                }
            }
            if (logoBitmap != null) {
                // Batasi lebar logo agar proporsional dengan lebar kertas struk (~384 dots / 58mm).
                val maxWidth = 280
                val scaledLogo = if (logoBitmap.width > maxWidth) {
                    val scale = maxWidth.toFloat() / logoBitmap.width
                    Bitmap.createScaledBitmap(logoBitmap, maxWidth, (logoBitmap.height * scale).toInt(), true)
                } else {
                    logoBitmap
                }
                sb.append("[C]<img>${PrinterTextParserImg.bitmapToHexadecimalString(printer, scaledLogo)}</img>\n")
            }
            sb.append("[C]<b>$storeName</b>\n")
            if (storeAddress.isNotBlank()) sb.append("[C]$storeAddress\n")
            sb.append("[C]--------------------------------\n")
            sb.append("[L]No: ${transaction.invoiceNumber}\n")
            sb.append("[L]${dateFormat.format(Date(transaction.createdAt))}\n")
            sb.append("[C]--------------------------------\n")

            items.forEach { item ->
                sb.append("[L]${item.productNameSnapshot}\n")
                sb.append("[L]${item.quantity} ${item.unitSnapshot} x ${rupiah.format(item.priceSnapshot)}[R]${rupiah.format(item.lineTotal)}\n")
            }

            sb.append("[C]--------------------------------\n")
            sb.append("[L]${strings.subtotal}[R]${rupiah.format(transaction.subtotal)}\n")
            sb.append("[L]${strings.discount}[R]-${rupiah.format(transaction.discountAmount)}\n")
            if (transaction.taxPercent > 0.0) {
                sb.append("[L]${strings.tax} (${transaction.taxPercent}%)[R]${rupiah.format(transaction.taxAmount)}\n")
            }
            sb.append("[L]<b>${strings.total}</b>[R]<b>${rupiah.format(transaction.total)}</b>\n")
            sb.append("[C]--------------------------------\n")
            sb.append("[L]${strings.paid} (${paymentLabel(transaction.paymentMethod, language)})[R]${rupiah.format(transaction.amountPaid)}\n")
            sb.append("[L]${strings.change}[R]${rupiah.format(transaction.changeAmount)}\n")
            sb.append("[C]--------------------------------\n")
            sb.append("[C]$receiptFooter\n")
            sb.append("[L]\n")

            printer.printFormattedTextAndCut(sb.toString())
            PrintResult.Success
        } catch (e: Exception) {
            PrintResult.Error(e.message ?: "Gagal mencetak struk. Pastikan printer menyala dan sudah di-pair.")
        }
    }

    private fun paymentLabel(method: PaymentMethod, language: String = "id"): String = when (method) {
        PaymentMethod.CASH -> "Cash"
        PaymentMethod.DEBIT_CREDIT -> "Debit/Kredit"
        PaymentMethod.QRIS -> "QRIS"
        PaymentMethod.BON -> if (language == "en") "Store Credit" else "Bon"
        PaymentMethod.MIXED -> if (language == "en") "Mixed" else "Campuran"
    }
}
