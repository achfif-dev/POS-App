package com.example.posapp.data.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.posapp.data.local.entity.TransactionEntity
import com.example.posapp.data.local.entity.TransactionItemEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generator invoice PDF menggunakan android.graphics.pdf.PdfDocument (built-in Android,
 * tanpa dependency tambahan). Ukuran halaman dibuat menyerupai struk kasir (bukan A4)
 * agar hasilnya ringkas dan cocok dibagikan lewat WhatsApp/Share Menu.
 */
@Singleton
class PdfInvoiceGenerator @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val rupiah = NumberFormat.getCurrencyInstance(Locale("in", "ID"))
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    /** Lebar mengikuti ukuran struk 58mm (~164pt pada 72dpi), tinggi menyesuaikan jumlah item. */
    fun generate(
        storeName: String,
        transaction: TransactionEntity,
        items: List<TransactionItemEntity>,
        storeAddress: String = "",
        receiptFooter: String = "Terima kasih!"
    ): File {
        val pageWidth = 220
        val lineHeight = 16
        val addressLines = if (storeAddress.isNotBlank()) 1 else 0
        val headerHeight = 90 + (addressLines * 12)
        val footerHeight = 70
        val pageHeight = headerHeight + (items.size * lineHeight) + footerHeight + 60

        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = document.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val titlePaint = Paint().apply { textSize = 12f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        val normalPaint = Paint().apply { textSize = 9f; typeface = Typeface.DEFAULT }
        val centerSmallPaint = Paint().apply { textSize = 8f; typeface = Typeface.DEFAULT; textAlign = Paint.Align.CENTER }
        val boldPaint = Paint().apply { textSize = 9f; typeface = Typeface.DEFAULT_BOLD }
        val rightPaint = Paint().apply { textSize = 9f; typeface = Typeface.DEFAULT; textAlign = Paint.Align.RIGHT }

        var y = 16f
        canvas.drawText(storeName, pageWidth / 2f, y, titlePaint)
        y += 14f
        if (storeAddress.isNotBlank()) {
            canvas.drawText(storeAddress, pageWidth / 2f, y, centerSmallPaint)
            y += 12f
        }
        canvas.drawText("No: ${transaction.invoiceNumber}", 8f, y, normalPaint)
        y += 12f
        canvas.drawText(dateFormat.format(Date(transaction.createdAt)), 8f, y, normalPaint)
        y += 12f
        canvas.drawLine(8f, y, pageWidth - 8f, y, normalPaint)
        y += 14f

        items.forEach { item ->
            canvas.drawText(item.productNameSnapshot, 8f, y, normalPaint)
            y += 11f
            canvas.drawText("${item.quantity} x ${rupiah.format(item.priceSnapshot)}", 8f, y, normalPaint)
            canvas.drawText(rupiah.format(item.lineTotal), pageWidth - 8f, y, rightPaint)
            y += 13f
        }

        canvas.drawLine(8f, y, pageWidth - 8f, y, normalPaint)
        y += 14f
        drawRow(canvas, "Subtotal", rupiah.format(transaction.subtotal), y, normalPaint, rightPaint); y += 12f
        drawRow(canvas, "Diskon", "-" + rupiah.format(transaction.discountAmount), y, normalPaint, rightPaint); y += 12f
        drawRow(canvas, "Pajak (${transaction.taxPercent}%)", rupiah.format(transaction.taxAmount), y, normalPaint, rightPaint); y += 14f
        drawRow(canvas, "Total", rupiah.format(transaction.total), y, boldPaint, rightPaint); y += 16f
        drawRow(canvas, "Bayar", rupiah.format(transaction.amountPaid), y, normalPaint, rightPaint); y += 12f
        drawRow(canvas, "Kembali", rupiah.format(transaction.changeAmount), y, normalPaint, rightPaint); y += 20f

        canvas.drawText(receiptFooter, pageWidth / 2f, y, titlePaint.apply { textSize = 10f })

        document.finishPage(page)

        val exportDir = File(context.getExternalFilesDir(null), "exports").apply { mkdirs() }
        val file = File(exportDir, "Invoice-${transaction.invoiceNumber}.pdf")
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()

        return file
    }

    private fun drawRow(canvas: Canvas, label: String, value: String, y: Float, labelPaint: Paint, valuePaint: Paint) {
        canvas.drawText(label, 8f, y, labelPaint)
        canvas.drawText(value, 220 - 8f, y, valuePaint)
    }
}
