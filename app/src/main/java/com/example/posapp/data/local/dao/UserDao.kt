package com.example.posapp.data.local.dao

import androidx.room.*
import com.example.posapp.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {

    @Query("SELECT * FROM users WHERE isActive = 1 ORDER BY role ASC, name ASC")
    fun observeAll(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE isActive = 1 AND pinHash = :pinHash LIMIT 1")
    suspend fun findByPinHash(pinHash: String): UserEntity?

    @Query("SELECT COUNT(*) FROM users WHERE isActive = 1")
    suspend fun countActive(): Int

    @Insert
    suspend fun insert(user: UserEntity): Long

    @Update
    suspend fun update(user: UserEntity)

    @Query("UPDATE users SET isActive = 0 WHERE id = :id")
    suspend fun softDelete(id: Long)
}
