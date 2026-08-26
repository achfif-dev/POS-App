package com.example.posapp.data.repository

import com.example.posapp.data.local.dao.ShiftDao
import com.example.posapp.data.local.dao.TransactionDao
import com.example.posapp.data.local.entity.ShiftEntity
import com.example.posapp.data.local.entity.ShiftStatus
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

sealed class OpenShiftResult {
    data class Success(val shiftId: Long) : OpenShiftResult()
    data class Error(val message: String) : OpenShiftResult()
}

sealed class CloseShiftResult {
    data class Success(val expectedCash: Double, val difference: Double) : CloseShiftResult()
    data class Error(val message: String) : CloseShiftResult()
}

@Singleton
class ShiftRepository @Inject constructor(
    private val shiftDao: ShiftDao,
    private val transactionDao: TransactionDao
) {
    fun observeActiveShift(): Flow<ShiftEntity?> = shiftDao.observeActiveShift()

    fun observeHistory(): Flow<List<ShiftEntity>> = shiftDao.observeHistory()

    suspend fun openShift(cashierId: Long?, cashierName: String, startCash: Double): OpenShiftResult {
        if (startCash < 0) {
            return OpenShiftResult.Error("Kas awal tidak boleh negatif")
        }
        if (shiftDao.getActiveShift() != null) {
            return OpenShiftResult.Error("Masih ada shift yang belum ditutup")
        }
        val id = shiftDao.insert(
            ShiftEntity(cashierId = cashierId, cashierName = cashierName, startCash = startCash)
        )
        return OpenShiftResult.Success(id)
    }

    /**
     * Tutup shift yang sedang aktif. [actualCash] adalah hasil hitung fisik kasir di laci —
     * [expectedCash] TIDAK diminta dari kasir, melainkan dihitung sistem (kas awal + total
     * penjualan tunai selama shift, dari data transaksi asli), supaya selisih mencerminkan
     * kejujuran fisik, bukan angka yang bisa disesuaikan manual.
     */
    suspend fun closeShift(actualCash: Double, note: String? = null): CloseShiftResult {
        if (actualCash < 0) {
            return CloseShiftResult.Error("Kas akhir tidak boleh negatif")
        }
        val active = shiftDao.getActiveShift()
            ?: return CloseShiftResult.Error("Tidak ada shift yang sedang berjalan")

        val endedAt = System.currentTimeMillis()
        val cashSales = transactionDao.getCashTotalInRange(active.startedAt, endedAt)
        val expectedCash = active.startCash + cashSales
        val difference = actualCash - expectedCash

        shiftDao.update(
            active.copy(
                status = ShiftStatus.CLOSED,
                expectedCash = expectedCash,
                actualCash = actualCash,
                difference = difference,
                note = note,
                endedAt = endedAt
            )
        )
        return CloseShiftResult.Success(expectedCash, difference)
    }
}
