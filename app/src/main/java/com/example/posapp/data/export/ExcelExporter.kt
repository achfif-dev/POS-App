package com.example.posapp.data.export

import android.content.Context
import com.example.posapp.data.local.entity.ProductEntity
import com.example.posapp.data.local.entity.TransactionEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Export data ke file .xlsx menggunakan Apache POI. Cocok untuk laporan produk & penjualan
 * yang perlu diolah lebih lanjut di Excel/Google Sheets oleh pemilik usaha.
 */
@Singleton
class ExcelExporter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    fun exportProducts(products: List<ProductEntity>): File {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Produk")
        val headerStyle = headerStyle(workbook)

        val headers = listOf("Nama", "SKU", "Harga Beli", "Harga Jual", "Stok", "Alert Stok Tipis", "Diskon (%)", "Aktif")
        val headerRow = sheet.createRow(0)
        headers.forEachIndexed { i, h -> headerRow.createCell(i).apply { setCellValue(h); cellStyle = headerStyle } }

        products.forEachIndexed { index, product ->
            val row = sheet.createRow(index + 1)
            row.createCell(0).setCellValue(product.name)
            row.createCell(1).setCellValue(product.sku)
            row.createCell(2).setCellValue(product.purchasePrice)
            row.createCell(3).setCellValue(product.sellPrice)
            row.createCell(4).setCellValue(product.stock.toDouble())
            row.createCell(5).setCellValue(product.lowStockThreshold.toDouble())
            row.createCell(6).setCellValue(product.discountPercent)
            row.createCell(7).setCellValue(if (product.isActive) "Ya" else "Tidak")
        }
        headers.indices.forEach { sheet.autoSizeColumn(it) }

        return writeToFile(workbook, "Produk-${System.currentTimeMillis()}.xlsx")
    }

    fun exportTransactions(transactions: List<TransactionEntity>): File {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Transaksi")
        val headerStyle = headerStyle(workbook)

        val headers = listOf("No. Invoice", "Tanggal", "Subtotal", "Diskon", "Pajak", "Total", "Metode Bayar", "Dibayar", "Kembalian")
        val headerRow = sheet.createRow(0)
        headers.forEachIndexed { i, h -> headerRow.createCell(i).apply { setCellValue(h); cellStyle = headerStyle } }

        transactions.forEachIndexed { index, tx ->
            val row = sheet.createRow(index + 1)
            row.createCell(0).setCellValue(tx.invoiceNumber)
            row.createCell(1).setCellValue(dateFormat.format(Date(tx.createdAt)))
            row.createCell(2).setCellValue(tx.subtotal)
            row.createCell(3).setCellValue(tx.discountAmount)
            row.createCell(4).setCellValue(tx.taxAmount)
            row.createCell(5).setCellValue(tx.total)
            row.createCell(6).setCellValue(tx.paymentMethod.name)
            row.createCell(7).setCellValue(tx.amountPaid)
            row.createCell(8).setCellValue(tx.changeAmount)
        }
        headers.indices.forEach { sheet.autoSizeColumn(it) }

        return writeToFile(workbook, "Laporan-Transaksi-${System.currentTimeMillis()}.xlsx")
    }

    /** Export CSV ringan tanpa Apache POI — cocok untuk dataset besar / kompatibilitas maksimal. */
    fun exportProductsCsv(products: List<ProductEntity>): File {
        val exportDir = File(context.getExternalFilesDir(null), "exports").apply { mkdirs() }
        val file = File(exportDir, "Produk-${System.currentTimeMillis()}.csv")
        file.bufferedWriter().use { writer ->
            writer.write("Nama,SKU,Harga Beli,Harga Jual,Stok,Alert Stok Tipis,Diskon (%),Aktif\n")
            products.forEach { p ->
                writer.write(
                    "\"${p.name}\",\"${p.sku}\",${p.purchasePrice},${p.sellPrice},${p.stock},${p.lowStockThreshold},${p.discountPercent},${if (p.isActive) "Ya" else "Tidak"}\n"
                )
            }
        }
        return file
    }

    private fun headerStyle(workbook: XSSFWorkbook): XSSFCellStyle {
        val font = workbook.createFont().apply { bold = true }
        return workbook.createCellStyle().apply {
            setFont(font)
            fillForegroundColor = IndexedColors.GREY_25_PERCENT.index
            fillPattern = org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND
        }
    }

    private fun writeToFile(workbook: XSSFWorkbook, fileName: String): File {
        val exportDir = File(context.getExternalFilesDir(null), "exports").apply { mkdirs() }
        val file = File(exportDir, fileName)
        FileOutputStream(file).use { workbook.write(it) }
        workbook.close()
        return file
    }
}
