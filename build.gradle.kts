plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("com.google.dagger.hilt.android") version "2.51.1" apply false
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
    // apply false di sini: cuma didaftarkan di classpath, BENAR-BENAR diterapkan secara
    // kondisional di app/build.gradle.kts (hanya jika app/google-services.json ada) supaya
    // build tetap hijau untuk siapa pun yang belum bikin proyek Firebase sendiri.
    id("com.google.gms.google-services") version "4.4.2" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
