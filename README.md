# Kasir POS (Full Offline) — Android Kotlin + Jetpack Compose

Fondasi arsitektur Clean Architecture (data / domain / presentation / di) untuk aplikasi
kasir offline-first, siap di-build otomatis lewat GitHub Actions tanpa perlu PC lokal.

## Yang sudah diimplementasikan penuh (siap pakai)

- **Struktur project** Clean Architecture lengkap (`data`, `domain`, `presentation`, `di`).
- **`app/build.gradle.kts`** — semua dependency yang diminta: Compose, Room, Hilt,
  Navigation, CameraX + ML Kit, ESC/POS printer (DantSu), Apache POI (Excel), Coroutines.
- **Room Database** — `ProductEntity`, `CategoryEntity`, `TransactionEntity`,
  `TransactionItemEntity`, `StockAdjustmentEntity` beserta DAO & query laporan
  (ringkasan penjualan, laba kotor, top-selling items).
- **Domain layer** — `Cart` model (subtotal/diskon/pajak/total otomatis) dan
  `CheckoutUseCase` yang menyimpan transaksi + item secara atomik dan mengurangi stok.
- **`PosViewModel`** — state kasir reaktif (StateFlow), search + filter kategori,
  tambah/kurang item, diskon per-item & per-transaksi, checkout.
- **`PosScreen`** — UI Compose Material 3: grid produk, keranjang, ringkasan
  subtotal/pajak/total, modal pembayaran (Cash/Debit/QRIS) via `ModalBottomSheet`.
- **Hilt DI** — `DatabaseModule` menyediakan `AppDatabase` + semua DAO sebagai singleton.
- **`.github/workflows/android_build.yml`** — build otomatis saat push ke `main`:
  lint → unit test → build APK debug & release → upload sebagai Build Artifact
  yang bisa didownload langsung dari tab **Actions**.

## Yang perlu kamu tambahkan (kerangka arsitektur sudah mendukung, tinggal isi UI/logic)

Kode-kode berikut butuh implementasi tambahan yang scope-nya besar sendiri-sendiri —
saya sengaja tidak memaksakan semuanya dalam satu paket agar kualitas kode yang sudah
ada tetap valid dan bisa langsung di-build:

1. **Scan Barcode (CameraX + ML Kit)** — buat `BarcodeScannerScreen.kt` di
   `presentation/scanner/`, gunakan `ImageAnalysis` + `BarcodeScanning.getClient()`,
   lalu panggil `productRepository.findBySku(hasil)` dan `viewModel.addToCart(...)`.
2. **Cetak Struk Bluetooth (ESC/POS)** — pakai `com.dantsu.escposprinter.EscPosPrinter`
   (sudah ada di dependency). Buat `PrinterRepository` untuk connect via
   `BluetoothPrintersConnections.selectFirstPaired()` dan format struk dari
   `TransactionEntity` + `TransactionItemEntity`.
3. **Export PDF Invoice** — gunakan `android.graphics.pdf.PdfDocument` (built-in,
   tanpa dependency tambahan) untuk render struk ke PDF, simpan ke
   `getExternalFilesDir("exports")`, lalu share via `FileProvider` (authority sudah
   dikonfigurasi di `AndroidManifest.xml`).
4. **Export Excel/CSV** — Apache POI sudah ada di dependency; buat
   `ExportUseCase` yang membaca `productRepository.getAllForExport()` /
   riwayat transaksi lalu menulis `XSSFWorkbook`.
5. **Backup & Restore** — cara paling aman untuk Room adalah copy file database
   (`context.getDatabasePath("pos_database")`) ke penyimpanan lokal untuk backup,
   dan menimpanya kembali (dengan app database tertutup) untuk restore.
6. **Manajemen Produk & Kategori (CRUD UI)**, **Laporan penjualan (UI grafik)**,
   **Stok Opname (UI)** — gunakan pola yang sama dengan `PosViewModel`/`PosScreen`
   (repository sudah menyediakan semua query yang dibutuhkan).

## Alur kerja GitHub Actions (tanpa PC)

1. Upload/commit seluruh isi folder ini ke repo GitHub kamu lewat web UI.
2. Push ke branch `main` (atau jalankan manual lewat tab **Actions → Run workflow**).
3. Setelah build selesai, buka run terkait di tab **Actions**, scroll ke
   **Artifacts**, download `app-debug-apk` atau `app-release-apk`.
4. APK release belum ditandatangani (unsigned) — untuk distribusi publik, tambahkan
   signing config di `app/build.gradle.kts` menggunakan GitHub Secrets
   (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, dst.), pola yang sama seperti workflow
   GWG Super App kamu.

## Catatan teknis

- Workflow CI menggunakan `gradle/actions/setup-gradle` (bukan `./gradlew`) supaya
  repo tetap ringan tanpa perlu meng-commit `gradle-wrapper.jar` biner. Kalau kamu
  ingin pakai wrapper standar, jalankan `gradle wrapper` sekali di Android Studio,
  commit hasilnya, lalu ganti step di workflow menjadi `./gradlew ...`.
- Skema Room di-set `fallbackToDestructiveMigration()` untuk mempercepat development
  awal. Sebelum rilis ke pengguna nyata, ganti dengan `Migration` resmi supaya data
  pengguna tidak hilang saat update versi app.
