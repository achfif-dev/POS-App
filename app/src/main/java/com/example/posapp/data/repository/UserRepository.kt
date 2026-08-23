package com.example.posapp.data.repository

import com.example.posapp.data.local.dao.UserDao
import com.example.posapp.data.local.entity.UserEntity
import com.example.posapp.data.local.entity.UserRole
import kotlinx.coroutines.flow.Flow
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val userDao: UserDao
) {
    fun observeAll(): Flow<List<UserEntity>> = userDao.observeAll()

    suspend fun hasAnyUser(): Boolean = userDao.countActive() > 0

    /** Mencoba login dengan PIN. Mengembalikan user jika PIN cocok dengan salah satu user aktif. */
    suspend fun login(pin: String): UserEntity? = userDao.findByPinHash(hash(pin))

    suspend fun createUser(name: String, pin: String, role: UserRole): Long =
        userDao.insert(UserEntity(name = name.trim(), pinHash = hash(pin), role = role))

    suspend fun updatePin(user: UserEntity, newPin: String) {
        userDao.update(user.copy(pinHash = hash(newPin)))
    }

    suspend fun renameUser(user: UserEntity, newName: String) {
        userDao.update(user.copy(name = newName.trim()))
    }

    suspend fun deleteUser(id: Long) = userDao.softDelete(id)

    private fun hash(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
