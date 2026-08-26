package com.example.posapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.posapp.data.local.entity.CustomerEntity
import com.example.posapp.data.local.entity.DebtPaymentEntity
import kotlinx.coroutines.flow.Flow

/** Ringkasan piutang per pelanggan, dihitung langsung dari data transaksi & pelunasan asli. */
data class CustomerWithDebt(
    val id: Long,
    val name: String,
    val phone: String?,
    val address: String?,
    val debtBalance: Double
)

@Dao
interface CustomerDao {

    @Query("SELECT * FROM customers WHERE isActive = 1 ORDER BY name ASC")
    fun observeAll(): Flow<List<CustomerEntity>>

    @Query(
        """
        SELECT c.id as id, c.name as name, c.phone as phone, c.address as address,
            COALESCE((
                SELECT SUM(tp.amount) FROM transaction_payments tp
                JOIN transactions t ON t.id = tp.transactionId
                WHERE tp.method = 'BON' AND t.customerId = c.id
            ), 0) - COALESCE((
                SELECT SUM(dp.amount) FROM debt_payments dp WHERE dp.customerId = c.id
            ), 0) as debtBalance
        FROM customers c
        WHERE c.isActive = 1
        ORDER BY c.name ASC
        """
    )
    fun observeAllWithDebt(): Flow<List<CustomerWithDebt>>

    @Query(
        """
        SELECT c.id as id, c.name as name, c.phone as phone, c.address as address,
            COALESCE((
                SELECT SUM(tp.amount) FROM transaction_payments tp
                JOIN transactions t ON t.id = tp.transactionId
                WHERE tp.method = 'BON' AND t.customerId = c.id
            ), 0) - COALESCE((
                SELECT SUM(dp.amount) FROM debt_payments dp WHERE dp.customerId = c.id
            ), 0) as debtBalance
        FROM customers c
        WHERE c.id = :customerId
        """
    )
    fun observeDebtDetail(customerId: Long): Flow<CustomerWithDebt?>

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun getById(id: Long): CustomerEntity?

    @Insert
    suspend fun insert(customer: CustomerEntity): Long

    @Update
    suspend fun update(customer: CustomerEntity)

    @Query("SELECT * FROM debt_payments WHERE customerId = :customerId ORDER BY createdAt DESC")
    fun observePayments(customerId: Long): Flow<List<DebtPaymentEntity>>

    @Insert
    suspend fun insertPayment(payment: DebtPaymentEntity): Long
}
