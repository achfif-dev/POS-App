# Kasir POS (Full Offline) — Android Kotlin + Jetpack Compose

Aplikasi kasir offline-first, Clean Architecture, siap di-build otomatis lewat GitHub Actions
tanpa perlu PC lokal.

# Kasir POS (Full Offline) — Android Kotlin + Jetpack Compose

Aplikasi kasir offline-first, Clean Architecture, siap di-build otomatis lewat GitHub Actions
tanpa perlu PC lokal. UI menggunakan tema Material 3 modern minimalis (mendukung mode gelap).

## Fitur yang sudah diimplementasikan penuh

- **Struktur project** Clean Architecture (`data`, `domain`, `presentation`, `di`).
- **Tema Material 3 modern minimalis** — `presentation/theme/` (palet netral + aksen, light/dark otomatis).
- **Room Database** — Produk, Kategori, Transaksi + Item, Penyesuaian Stok, Varian Produk,
  Pengguna, lengkap dengan query laporan (omzet, laba kotor, top-selling items).
- **Kasir (POS Screen)** — grid produk, pencarian, keranjang, diskon per-item/transaksi,
  pajak otomatis, checkout Cash/Debit/QRIS (dengan tampilan gambar QRIS otomatis).
- **Manajemen Produk** — CRUD lengkap (nama, SKU, harga beli/jual, stok, alert stok tipis,
  diskon) + kelola kategori.
- **Varian Produk (Matrix)** — toggle "Produk Punya Varian" pada form produk; setiap kombinasi
  (mis. Ukuran x Warna) punya SKU, stok, dan harga khusus sendiri. Dikelola lewat dialog "Kelola
  Varian" dan otomatis muncul sebagai bottom sheet pemilih di layar Kasir saat produk dipilih.
- **Scan Barcode** — CameraX + ML Kit, otomatis menambahkan produk (atau varian via SKU) ke
  keranjang begitu barcode terbaca (`presentation/scanner/BarcodeScannerScreen.kt`).
- **Cetak Struk Bluetooth (ESC/POS)** — `data/printer/PrinterRepository.kt` menggunakan
  DantSu/ESCPOS-ThermalPrinter-Android, terhubung ke printer yang sudah di-pair, memakai nama
  toko/alamat/catatan dari Profil Toko.
- **Export PDF Invoice** — `data/export/PdfInvoiceGenerator.kt` memakai
  `android.graphics.pdf.PdfDocument` native, memakai data dari Profil Toko.
- **Export Excel/CSV** — `data/export/ExcelExporter.kt` (Apache POI) untuk produk & riwayat
  transaksi, plus opsi CSV ringan. Diakses dari layar Pengaturan.
- **Profil Toko** — layar khusus (`presentation/settings/StoreProfileScreen.kt`) untuk mengatur
  nama toko, alamat, telepon, catatan kaki struk, dan upload gambar QRIS statis (disimpan via
  DataStore + internal storage, dipakai di struk/PDF/layar pembayaran).
- **Login PIN Multi-User** — `presentation/auth/LoginScreen.kt` + manajemen kasir/admin di
  Pengaturan > Pengguna & Login PIN. PIN di-hash SHA-256, sesi login in-memory (wajib login lagi
  tiap buka app bila fitur diaktifkan), setiap transaksi mencatat nama kasir yang login.
- **Backup & Restore** — `data/backup/BackupRepository.kt` meng-copy file database SQLite
  secara utuh, dengan **riwayat backup lokal** yang tampil di layar Pengaturan (tanggal, ukuran,
  tombol restore/bagikan langsung).
- **Laporan Penjualan** — `presentation/report/ReportScreen.kt`: filter Hari Ini/Minggu
  Ini/Bulan Ini, ringkasan omzet & laba kotor, daftar produk terlaris.
- **Stok & Inventaris** — `presentation/stock/StockScreen.kt`: stok masuk/keluar/opname
  dengan riwayat, filter stok tipis.
- **`.github/workflows/android_build.yml`** — build otomatis: lint → unit test → APK debug
  & release ter-upload sebagai Build Artifact.

## Yang masih berupa penyempurnaan opsional (bukan blocker untuk pemakaian)

- **Riwayat penyesuaian stok per varian** belum ada di layar Stok (saat ini penyesuaian stok
  manual di layar Stok hanya untuk produk tanpa varian; stok varian diatur lewat dialog "Kelola
  Varian" di Manajemen Produk).
- **Role-based permission** — role Admin/Kasir sudah tersimpan di database tapi belum membatasi
  akses menu tertentu (mis. Kasir tidak bisa masuk Pengaturan). Bisa ditambahkan dengan mengecek
  `SessionManager.currentUser.value?.role` di navigasi.
- **QRIS dinamis** (generate QR per transaksi) belum ada — saat ini QRIS berupa gambar statis
  yang diunggah pemilik toko.

## Alur kerja GitHub Actions (tanpa PC)

1. Upload/commit seluruh isi folder ini ke repo GitHub kamu lewat web UI.
2. Push ke branch `main` (atau jalankan manual lewat tab **Actions → Run workflow**).
3. Setelah build selesai, buka run terkait di tab **Actions**, scroll ke
   **Artifacts**, download `app-debug-apk` (langsung bisa diinstall) atau `app-release-apk`
   (unsigned, perlu signing config sebelum dipakai produksi).

## Catatan teknis penting

- `minSdk = 26` (Android 8.0+) karena Apache POI (export Excel) memakai
  `java.lang.invoke.MethodHandle` yang baru didukung mulai API 26.
- Workflow CI menggunakan `gradle/actions/setup-gradle` dengan `gradle-version: '8.7'` (bukan
  `./gradlew`) supaya repo tetap ringan tanpa commit `gradle-wrapper.jar` biner.
- `proguard-rules.pro` sudah menyertakan `-dontwarn` untuk dependency opsional Apache POI
  (OSGi, Batik/SVG) yang tidak dipakai di path Android manapun di app ini.
- Skema Room di-set `exportSchema = false` dan `fallbackToDestructiveMigration()` untuk
  mempercepat development awal (versi database saat ini: 2, ditambah tabel `product_variants`
  & `users`). Sebelum rilis ke pengguna nyata, aktifkan `exportSchema = true` + tulis
  `Migration` resmi supaya data pengguna tidak hilang saat update versi app.
- PIN pengguna disimpan sebagai hash SHA-256 (`UserRepository`), bukan plaintext. Sesi login
  bersifat in-memory (`SessionManager`) sehingga aplikasi akan meminta PIN lagi setiap kali
  dibuka ulang selama fitur "Login PIN" aktif di Pengaturan > Pengguna & Login PIN.
- Izin runtime yang diminta: Kamera (scan barcode) dan Bluetooth Connect/Scan (Android 12+,
  untuk cetak struk). Printer harus sudah di-pair lewat pengaturan Bluetooth sistem terlebih
  dahulu sebelum mencetak dari app.

