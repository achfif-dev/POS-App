plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    kotlin("plugin.serialization") version "1.9.24"
}

// Sinkronisasi Cloud (Fase 4 - lihat FIREBASE_SETUP.md) BUTUH proyek Firebase sendiri, yang
// tidak bisa dibuatkan otomatis dari sini. Plugin google-services HANYA diterapkan kalau file
// konfigurasinya sudah ada, supaya siapa pun yang clone/upload ulang repo ini tanpa membuat
// proyek Firebase dulu TETAP bisa build normal (fitur cloud sync otomatis nonaktif, bukan error).
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

// Kredensial keystore release DIBACA DARI ENVIRONMENT VARIABLE, bukan di-hardcode di sini
// atau di gradle.properties yang ikut ter-commit — supaya aman untuk repo publik/privat.
// Di GitHub Actions, env var ini diisi dari GitHub Secrets (lihat android_build.yml).
// Kalau tidak di-set (mis. build lokal tanpa keystore), release TIDAK akan ditandatangani —
// build tetap jalan (tidak error), tapi APK hasilnya tidak akan bisa diinstall sampai
// signing config di-set. Gunakan APK debug untuk testing cepat di HP.
val releaseKeystoreFile: String? = System.getenv("RELEASE_KEYSTORE_FILE")
val releaseKeystorePassword: String? = System.getenv("RELEASE_KEYSTORE_PASSWORD")
val releaseKeyAlias: String? = System.getenv("RELEASE_KEY_ALIAS")
val releaseKeyPassword: String? = System.getenv("RELEASE_KEY_PASSWORD")
val hasReleaseSigningConfig: Boolean =
    !releaseKeystoreFile.isNullOrBlank() &&
        !releaseKeystorePassword.isNullOrBlank() &&
        !releaseKeyAlias.isNullOrBlank() &&
        !releaseKeyPassword.isNullOrBlank()

android {
    namespace = "com.example.posapp"
    compileSdk = 34

    defaultConfig {
        // CATATAN: applicationId sengaja dipisah dari `namespace` (yang masih com.example.posapp
        // dan HANYA menentukan package R/BuildConfig internal, aman dibiarkan). applicationId inilah
        // identitas app yang publik/dipakai Play Store & dilihat pengguna — com.example.* ditolak
        // Play Store dan tidak profesional untuk app yang mau disewakan ke toko lain. Ganti
        // "id.gwg.posapp" di bawah sesuai domain/brand Anda sendiri sebelum rilis publik.
        applicationId = "id.gwg.posapp"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (hasReleaseSigningConfig) {
            create("release") {
                storeFile = file(releaseKeystoreFile!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// FIX: Firebase Firestore/Auth (Fase 4 - Sinkronisasi Cloud) menyeret com.google.guava:guava
// (Guava penuh, berisi class ListenableFuture asli) sebagai dependency transitif. CameraX
// (camera-core) sendiri bergantung pada com.google.guava:listenablefuture:1.0 — artifact
// TERPISAH & KOSONG (cuma stub) yang JUGA mendefinisikan class com.google.common.util.concurrent
// .ListenableFuture. Kalau keduanya sama-sama masuk classpath, Gradle/Kotlin bingung class mana
// yang dipakai -> "Cannot access class ListenableFuture" di BarcodeScannerScreen.kt (dulu tidak
// terjadi karena app ini belum punya dependency lain yang menarik Guava penuh, sebelum Fase 4
// menambahkan Firebase). Percobaan pertama (cuma `force` versi guava) TIDAK CUKUP karena kedua
// artifact tetap sama-sama ada di classpath. Fix yang benar: exclude stub-nya sepenuhnya supaya
// semua konsumen (CameraX & Firebase) dipaksa memakai SATU ListenableFuture asli dari Guava.
configurations.all {
    exclude(group = "com.google.guava", module = "listenablefuture")
    resolutionStrategy {
        force("com.google.guava:guava:32.1.3-android")
    }
}

dependencies {
    // Core / Compose
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    // Google Fonts "downloadable" — mengambil font asli dari Google Play Services saat
    // runtime (bukan font sistem Roboto bawaan), tanpa perlu membundel file .ttf di APK.
    implementation("androidx.compose.ui:ui-text-google-fonts")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Room (offline persistent storage)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Hilt DI
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-android-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // CameraX + ML Kit Barcode Scanning
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // Bluetooth ESC/POS Thermal Printer
    implementation("com.github.DantSu:ESCPOS-ThermalPrinter-Android:3.3.0")

    // Excel (.xlsx) export ditulis manual via java.util.zip (lihat XlsxWriter.kt) — Apache POI
    // SUDAH DIHAPUS karena tidak kompatibel dengan runtime Android (penyebab crash tombol export).

    // PDF generation is done via native android.graphics.pdf.PdfDocument (no extra dep needed)

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Coil for local product photos & QRIS image
    implementation("io.coil-kt:coil-compose:2.6.0")

    // ZXing core: generate kode QR dinamis per transaksi (QRIS amount injection), murni offline
    implementation("com.google.zxing:core:3.5.3")

    // DataStore (profil toko, preferensi login PIN, dsb.)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Sinkronisasi Cloud lintas cabang (Fase 4 - opsional, nonaktif sampai google-services.json
    // ada & toggle "Sinkronisasi Cloud" dinyalakan Admin di Pengaturan). Lihat FIREBASE_SETUP.md.
    // Dependency ini AMAN ditambahkan walau belum ada proyek Firebase — hanya dipakai (dan hanya
    // butuh config valid) saat runtime memanggilnya, bukan saat compile.
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-auth-ktx")

    // Testing
    testImplementation("junit:junit:4.13.2") // termasuk org.junit.rules.TemporaryFolder dipakai BackupCryptoTest
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
