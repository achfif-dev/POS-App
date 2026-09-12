# Setup Firebase untuk Sinkronisasi Cloud & Multi-Cabang

Ada 3 fitur di **Pengaturan > Multi-Cabang** yang memakai setup di file ini:

- **Sinkronisasi Cloud** — kirim ringkasan omzet harian (total omzet + jumlah transaksi, BUKAN
  detail transaksi/produk/pelanggan) cabang ini ke [Firestore](https://firebase.google.com/docs/firestore).
- **Ringkasan Semua Cabang** — lihat gabungan omzet hari ini dari semua cabang yang sudah
  Sinkronisasi Cloud, dari satu HP.
- **Cek Stok Semua Cabang** — kirim & lihat katalog produk + stok + harga jual cabang lain
  secara *read-only* (bukan omzet), disinkron tiap beberapa jam.

Semua fitur ini **nonaktif secara default** dan aplikasi tetap 100% offline-first tanpa langkah
di bawah ini — hanya perlu dilakukan kalau kamu memang ingin memakai salah satu fitur multi-cabang
di atas.

> **Wajib: Lisensi aplikasi harus sudah aktif di tiap HP cabang.** Sejak perbaikan keamanan
> cross-tenant (lihat bagian "Kenapa butuh lisensi aktif" di bawah), ketiga fitur ini terkunci ke
> kode lisensi toko — tanpa lisensi aktif, `TenantAuthProvider` tidak akan bisa sign-in ke sesi
> cloud dan ketiganya otomatis gagal (fail-soft, tidak crash, cuma tidak sinkron).

## 1. Buat proyek Firebase

1. Buka [console.firebase.google.com](https://console.firebase.google.com), klik **Add project**.
2. Beri nama bebas (mis. "POS App Toko Saya"), lanjutkan sampai selesai (Google Analytics boleh
   dimatikan, tidak dipakai fitur ini).

> Kalau kamu **sudah** setup Firebase project untuk Lisensi Anti-Bajakan/Payment Gateway
> Otomatis (lihat LICENSING_SETUP.md/PAYMENT_GATEWAY_SETUP.md), pakai project Firebase yang SAMA
> — jangan buat project baru. Fitur cloud sync sengaja dibuat menumpang di satu project yang sama
> supaya `mintSyncToken` (langkah 5) bisa memverifikasi lisensi tanpa setup ganda.

## 2. Daftarkan aplikasi Android

1. Di dashboard proyek, klik ikon Android untuk **Add app**.
2. **Android package name**: isi persis sesuai `applicationId` di `app/build.gradle.kts`
   (repo ini sekarang pakai: `id.shiftq.posapp` — cek `app/build.gradle.kts` kalau sudah diganti lagi).
3. Nickname app & SHA-1 boleh dikosongkan (tidak dipakai fitur ini).
4. **Download `google-services.json`**.

## 3. Taruh file konfigurasi

Upload file `google-services.json` yang barusan didownload ke folder **`app/`** di repo ini
(sejajar dengan `app/build.gradle.kts`), lewat GitHub web UI (Add file > Upload files).

> Build sengaja dibuat mendeteksi keberadaan file ini secara otomatis (lihat komentar di
> `app/build.gradle.kts`) — kalau file belum ada, build tetap sukses dan fitur cloud sync
> otomatis nonaktif (bukan error).

> **Penting:** plugin `com.google.gms.google-services` yang memproses file ini HARUS sudah
> terdaftar di `build.gradle.kts` (root project, bukan `app/build.gradle.kts`). Kalau repo ini
> kamu dapat dari sebelum perbaikan ini ditambahkan, build akan gagal dengan error
> `Plugin with id 'com.google.gms.google-services' not found` begitu `google-services.json`
> di-upload — root `build.gradle.kts` sudah diperbaiki untuk mendaftarkan plugin ini di baris
> `plugins { ... }`, jadi cukup pastikan repo kamu sudah pakai versi terbaru.

## 4. Aktifkan Firestore & Authentication

Di Firebase Console, proyek yang tadi dibuat:

1. **Build > Firestore Database > Create database** — pilih mode **production**, lokasi server
   terdekat (mis. `asia-southeast2` untuk Indonesia — HARUS sama dengan region yang dipakai
   Cloud Functions, lihat `FUNCTIONS_REGION` di `functions/index.js`).
2. **Build > Authentication > Get started > Sign-in method > Anonymous** — aktifkan.
   (Dipakai LicenseRepository/PaymentGatewayRepository untuk sign-in anonim biasa. Sesi Cloud
   Sync/Cek Stok Semua Cabang sendiri memakai custom token — lihat langkah 5 — BUKAN anonim,
   tapi provider Anonymous tetap harus aktif untuk dua fitur lain itu.)

## 5. Deploy Cloud Functions & Firestore Rules lewat GitHub Actions

**Beda dari sebelumnya:** rules TIDAK lagi ditempel manual lewat Firebase Console — sekarang jadi
satu paket dengan `functions/index.js` (berisi `mintSyncToken`, dipanggil `TenantAuthProvider.kt`
untuk memverifikasi lisensi + mengeluarkan custom token bergrup) dan `firestore.rules` (sudah
disiapkan di repo ini, mensyaratkan custom claim `customerGroupId` yang cuma didapat dari token
hasil `mintSyncToken` — lihat komentar "TEMUAN KEAMANAN" di `firestore.rules` untuk detail
lengkap kenapa perlu serumit ini).

1. Kalau belum pernah, siapkan 2 GitHub Secrets sesuai instruksi di
   `.github/workflows/deploy_license_functions.yml` (`FIREBASE_SERVICE_ACCOUNT_JSON`,
   `FIREBASE_PROJECT_ID`) — SAMA dengan yang dipakai LICENSING_SETUP.md, tidak perlu dibuat ulang
   kalau sudah ada.
2. Buka tab **Actions** di GitHub, pilih workflow **"Deploy Functions Lisensi & Payment
   Gateway"**, klik **Run workflow**.
3. Workflow ini otomatis men-deploy ULANG semua Cloud Functions (termasuk `mintSyncToken`) DAN
   `firestore.rules` sekaligus — tidak perlu menyalin rule manual ke Firebase Console lagi.

> Kalau kamu SUDAH pernah menjalankan workflow ini untuk Lisensi/Payment Gateway sebelum membaca
> dokumen ini, cukup jalankan ulang (**Run workflow** lagi) supaya `mintSyncToken` &
> `firestore.rules` versi terbaru ikut ter-deploy — keduanya baru ditambahkan belakangan.

### Kenapa butuh lisensi aktif (ringkas)

Sebelum perbaikan ini, semua pengguna app ini (dari toko manapun) sign-in anonim yang setara
tanpa identitas kepemilikan apa pun — artinya dua toko yang sama sekali tidak berhubungan tapi
sama-sama mengaktifkan Sinkronisasi Cloud/Cek Stok Semua Cabang bisa saling membaca omzet dan
katalog satu sama lain. Sekarang setiap device WAJIB membuktikan lisensinya aktif dulu ke
`mintSyncToken` sebelum dapat token untuk baca/tulis Firestore, dan token itu membawa
`customerGroupId` (dihitung dari kode lisensi) yang mengunci cabang-cabang HANYA bisa saling
lihat data cabang lain dengan lisensi yang sama.

## 6. Build & jalankan

Push/upload perubahan (termasuk `google-services.json` yang baru ditambahkan) ke GitHub, jalankan
Actions build APK seperti biasa. Setelah APK terinstall & **lisensi diaktivasi** di tiap cabang:

1. Buka **Pengaturan > Multi-Cabang > Sinkronisasi Cloud** di tiap device cabang.
2. Isi **Nama Cabang** yang berbeda-beda per device (mis. "Cabang Kelapa Gading", "Cabang Bekasi").
3. Nyalakan toggle **Aktifkan Sinkronisasi Cloud**.
4. Setelah ada transaksi, cek **Pengaturan > Multi-Cabang > Ringkasan Semua Cabang** — omzet hari
   itu dari semua cabang (dengan lisensi/grup yang sama) yang sudah sinkron akan muncul digabung.
5. Kalau juga mau ikut membagikan & melihat stok cabang lain, buka **Cek Stok Semua Cabang** dan
   nyalakan toggle di sana (terpisah dari toggle Sinkronisasi Cloud).

## Batasan versi ini

- Yang disinkronkan **hanya ringkasan omzet harian** (total omzet + jumlah transaksi per hari per
  cabang) lewat Sinkronisasi Cloud, dan **katalog+stok+harga jual** (bukan pelanggan/piutang) lewat
  Cek Stok Semua Cabang. Manajemen produk, stok, dan piutang tetap sepenuhnya per-device/per-cabang
  sebagai sumber kebenaran utama — data cloud murni salinan untuk dilihat, bukan pusat kendali.
- Ringkasan Semua Cabang menampilkan data **hari ini** saja (belum ada pilihan rentang tanggal).
- Cabang-cabang dengan kode lisensi yang SAMA otomatis dianggap satu grup/toko (bisa saling lihat
  data) — kalau kamu punya beberapa toko yang benar-benar terpisah (bukan cabang dari satu toko
  yang sama), pakai kode lisensi yang BERBEDA untuk masing-masing supaya tidak tercampur.
- Ini fondasi awal — kalau ke depan kamu butuh sinkronisasi penuh (produk/stok terpusat, riwayat
  multi-hari, dsb.), itu pengembangan lanjutan di atas fondasi ini.
