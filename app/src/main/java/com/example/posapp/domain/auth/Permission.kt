package com.example.posapp.domain.auth

import com.example.posapp.data.local.entity.UserEntity
import com.example.posapp.data.local.entity.UserRole

/**
 * Guard peran terpusat (fail-closed) untuk rute-rute sensitif (Expenses, Settings, Backup/Store
 * Profile). Dipakai independen di MainActivity untuk setiap rute, bukan cuma mengandalkan menu
 * yang disembunyikan di UI — supaya deep link atau navigasi langsung tidak bisa menembus guard.
 *
 * Aturan: kalau fitur "Wajibkan Login PIN" nonaktif, app dianggap mode single-user dan semua
 * diizinkan. Kalau aktif, hanya user dengan role ADMIN yang boleh mengakses rute-rute ini.
 */
object Permission {
    fun canAccessExpenses(user: UserEntity?, pinLoginEnabled: Boolean): Boolean =
        isAdminOrPinDisabled(user, pinLoginEnabled)

    fun canAccessSettings(user: UserEntity?, pinLoginEnabled: Boolean): Boolean =
        isAdminOrPinDisabled(user, pinLoginEnabled)

    fun canManageBackup(user: UserEntity?, pinLoginEnabled: Boolean): Boolean =
        isAdminOrPinDisabled(user, pinLoginEnabled)

    private fun isAdminOrPinDisabled(user: UserEntity?, pinLoginEnabled: Boolean): Boolean {
        if (!pinLoginEnabled) return true
        return user?.role == UserRole.ADMIN
    }
}
