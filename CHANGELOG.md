# Changelog

Semua perubahan penting pada proyek ini dicatat di file ini.
Format mengikuti [Keep a Changelog](https://keepachangelog.com/), versioning mengikuti [Semantic Versioning](https://semver.org/).

## [Unrilis]
### Ditambahkan
- **Retur/Refund & Void Transaksi**: tabel baru `transaction_returns` & `transaction_return_items`
  (migrasi v10 -> v11). Retur boleh diproses Kasir maupun Admin (alasan wajib, per-item bisa
  ditandai layak jual lagi/rusak, bisa bertahap). Void (batalkan transaksi sepenuhnya) khusus
  Admin — menggantikan tombol "Hapus Transaksi" lama yang menghapus permanen; sekarang transaksi
  tetap tersimpan (status VOIDED) untuk audit, hanya dikeluarkan dari perhitungan Laporan.
- **Role Manager**: peran baru di antara Kasir dan Admin (migrasi v11 -> v12, tanpa perubahan
  skema — cuma nilai enum baru). Manager bisa jual & retur seperti Kasir, plus akses Beban Usaha
  & Laba Bersih di Laporan — tapi tetap tidak bisa Void/koreksi transaksi maupun akses
  Pengaturan/Backup/Manajemen Pengguna (murni Admin-only).
- **Log Aktivitas**: tabel baru `audit_logs`, mencatat Void, Retur, Koreksi Transaksi, Tambah/
  Hapus Pengguna, dan Restore Backup (siapa, kapan, alasan). Layar baru di Pengaturan > Log
  Aktivitas (Admin-only). Jejak Restore Backup sengaja disimpan di file teks terpisah
  (`restore_audit_log.txt`), BUKAN di tabel `audit_logs` — karena restore menimpa seluruh file
  database termasuk tabel itu sendiri, jadi jejaknya justru akan ikut hilang kalau disimpan di
  sana (lihat komentar `RestoreAuditLog.kt`).

### Diperbaiki
- Bug lama di query Laporan Omzet (`getSalesSummary`): `SUM(t.total)` ikut ter-JOIN dengan baris
  item transaksi, sehingga omzet transaksi dengan banyak item terhitung berulang kali. Sekarang
  dihitung dari subquery terpisah yang tidak ikut kelipatan oleh JOIN.

### Diubah
- `exportSchema` Room diaktifkan (`true`) + `room.schemaLocation` dikonfigurasi ke `app/schemas/`.
  Mulai sekarang setiap kenaikan versi database menyimpan snapshot skema JSON asli, sehingga
  migrasi berikutnya (v10 -> v11, dst.) bisa diuji otomatis terhadap skema versi sebelumnya yang
  sungguhan (bukan cuma dugaan) memakai `MigrationTestHelper`. Folder `app/schemas/` WAJIB
  ikut di-commit ke Git — jangan masukkan ke `.gitignore`.

## [1.2.0] - 2026-08-26
### Ditambahkan
- **Manajemen Shift Kasir**: buka/tutup kasir dengan rekonsiliasi kas otomatis (kas seharusnya
  dihitung sistem dari data transaksi tunai asli, bukan input manual) — tabel `shifts` baru.
  Saat "Wajibkan Login PIN" aktif, transaksi digerbang: harus ada shift terbuka dulu.
- **Pelanggan & Piutang (Bon)**: metode pembayaran baru `BON` (bisa dicampur/split dengan
  Cash/QRIS/Debit), saldo piutang dihitung otomatis dari transaksi Bon dikurangi pelunasan
  (tabel `customers` + `debt_payments` baru), layar daftar pelanggan + detail piutang + catat
  pelunasan (boleh mencicil).
- **Sinkronisasi Cloud / Multi-Cabang (fondasi)**: kirim ringkasan omzet harian per cabang ke
  Firestore (opsional, nonaktif default, butuh setup Firebase sendiri — lihat `FIREBASE_SETUP.md`),
  layar Ringkasan Semua Cabang untuk pemilik. Sepenuhnya fail-soft: tanpa konfigurasi Firebase,
  app tetap 100% offline seperti biasa.
- Database Room naik ke v9 (v7 Beban Usaha → v8 Shift → v9 Customer/Piutang/kolom `customerId`).

## [1.1.0] - 2026-08-25
### Diperbaiki
- Build gagal: import `Modifier.pointerInput` yang salah paket di `MainActivity.kt`.
- Build gagal: file `domain/auth/Permission.kt` (dipakai untuk role-gating rute Expenses/Settings/Backup) hilang dari repo.
### Ditambahkan
- Unit test untuk logika inti: `Cart` (kalkulasi subtotal/diskon/pajak), `CheckoutValidator`
  (validasi checkout — cart kosong, pembayaran kurang, stok tidak cukup, split payment),
  `QrisUtil.injectAmount` (TLV & CRC16 QRIS dinamis), `BackupCrypto` (round-trip enkripsi backup).
- `applicationId` diganti dari `com.example.posapp` (ditolak Play Store) ke `id.gwg.posapp`.
### Diubah
- Logika validasi checkout dipisah ke `CheckoutValidator` (objek murni tanpa dependensi Room/Hilt)
  supaya bisa diuji unit test tanpa emulator/device.

## [1.0.0] - 2026-08-24
### Ditambahkan
- Rilis awal: Kasir (POS), Manajemen Produk + Varian, Scan Barcode, Cetak Struk Bluetooth ESC/POS,
  QRIS statis→dinamis on-device, Export PDF/Excel, Login PIN multi-user, Backup & Restore terenkripsi,
  Laporan Penjualan + Laba Bersih, Stok & Inventaris, Beban Usaha.
