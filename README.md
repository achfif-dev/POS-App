# Kasir POS (Full Offline) — Android Kotlin + Jetpack Compose

Aplikasi kasir offline-first, Clean Architecture, siap di-build otomatis lewat GitHub Actions
tanpa perlu PC lokal.

## Fitur yang sudah diimplementasikan penuh

- **Struktur project** Clean Architecture (`data`, `domain`, `presentation`, `di`).
- **Room Database** — Produk, Kategori, Transaksi + Item, Penyesuaian Stok, lengkap dengan
  query laporan (omzet, laba kotor, top-selling items).
- **Kasir (POS Screen)** — grid produk, pencarian, keranjang, diskon per-item/transaksi,
  pajak otomatis, checkout Cash/Debit/QRIS.
- **Manajemen Produk** — CRUD lengkap (nama, SKU, harga beli/jual, stok, alert stok tipis,
  diskon, varian) + kelola kategori.
- **Scan Barcode** — CameraX + ML Kit, otomatis menambahkan produk ke keranjang begitu
  barcode terbaca (`presentation/scanner/BarcodeScannerScreen.kt`).
- **Cetak Struk Bluetooth (ESC/POS)** — `data/printer/PrinterRepository.kt` menggunakan
  DantSu/ESCPOS-ThermalPrinter-Android, terhubung ke printer yang sudah di-pair. Dipicu dari
  dialog struk setelah checkout berhasil.
- **Export PDF Invoice** — `data/export/PdfInvoiceGenerator.kt` memakai
  `android.graphics.pdf.PdfDocument` native (tanpa dependency tambahan), langsung dibagikan
  lewat Share Menu/WhatsApp.
- **Export Excel/CSV** — `data/export/ExcelExporter.kt` (Apache POI) untuk produk & riwayat
  transaksi, plus opsi CSV ringan. Diakses dari layar Pengaturan.
- **Backup & Restore** — `data/backup/BackupRepository.kt` meng-copy file database SQLite
  secara utuh (termasuk checkpoint WAL) untuk memastikan integritas data.
- **Laporan Penjualan** — `presentation/report/ReportScreen.kt`: filter Hari Ini/Minggu
  Ini/Bulan Ini, ringkasan omzet & laba kotor, daftar produk terlaris.
- **Stok & Inventaris** — `presentation/stock/StockScreen.kt`: stok masuk/keluar/opname
  dengan riwayat, filter stok tipis.
- **`.github/workflows/android_build.yml`** — build otomatis: lint → unit test → APK debug
  & release ter-upload sebagai Build Artifact.

## Yang masih berupa penyempurnaan opsional (bukan blocker untuk pemakaian)

- **Nama toko** saat ini hardcode `"Toko Saya"` di struk/PDF (`PosViewModel.printReceipt()` /
  `exportReceiptPdf()`). Tambahkan layar "Profil Toko" + simpan ke DataStore/SharedPreferences
  bila ingin bisa diubah dari UI.
- **Varian produk** baru berupa field teks bebas (`variantName`), belum ada UI matrix varian
  (mis. Ukuran x Warna dengan stok terpisah per kombinasi).
- **QRIS statis** saat ini hanya tercatat sebagai metode pembayaran; belum ada tampilan
  gambar QRIS di layar pembayaran. Tambahkan `Image` dari file QRIS yang di-upload pemilik
  toko di layar Pengaturan bila diperlukan.
- **Riwayat backup otomatis terjadwal** belum ada (saat ini backup manual via tombol).
  `BackupRepository.listLocalBackups()` sudah siap dipakai untuk menampilkan riwayat backup
  di UI bila mau dikembangkan lebih lanjut.
- **Multi-user/kasir dengan PIN login** belum ada — saat ini single-user.

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
  mempercepat development awal. Sebelum rilis ke pengguna nyata, aktifkan `exportSchema = true`
  + tulis `Migration` resmi supaya data pengguna tidak hilang saat update versi app.
- Izin runtime yang diminta: Kamera (scan barcode) dan Bluetooth Connect/Scan (Android 12+,
  untuk cetak struk). Printer harus sudah di-pair lewat pengaturan Bluetooth sistem terlebih
  dahulu sebelum mencetak dari app.

