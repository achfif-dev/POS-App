package com.example.posapp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class UserRole { ADMIN, KASIR }

/**
 * Pengguna aplikasi (pemilik toko/admin & kasir) untuk fitur login PIN.
 * [pinHash] disimpan sebagai SHA-256 hex, PIN asli tidak pernah disimpan.
 */
@Entity(tableName = "users", indices = [Index("name", unique = true)])
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val pinHash: String,
    val role: UserRole = UserRole.KASIR,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
