# Ultrawide Camera Hook (modul LSPosed)

Membuat aplikasi kamera `com.android.camera` langsung memakai kamera ultrawide (0.6x)
setiap kali dibuka, sehingga kamera utama yang rusak tidak pernah dibuka dan aplikasi tidak freeze.

> `com.android.camera` adalah **target (scope)** modul. Package modul sendiri adalah
> `dev.ultrawide.camerahook` (tidak boleh sama dengan package aplikasi kamera).

## Cara kerja

Hook dipasang di level framework Camera2 (`CameraManager`), bukan di class internal aplikasi kamera:

1. Saat aplikasi membuka kamera belakang utama (ID terendah, biasanya `0`), ID dialihkan ke kamera ultrawide.
2. Saat aplikasi menanyakan spesifikasi kamera utama, diberikan spesifikasi ultrawide
   supaya ukuran preview/foto yang dipilih aplikasi cocok dengan kamera yang benar-benar dibuka.
3. Kamera ultrawide dideteksi otomatis: kamera belakang dengan field of view terlebar
   (minimal 15 derajat lebih lebar dari kamera utama).

## Build lewat GitHub Actions (tanpa install apa pun)

1. Buat repository baru di GitHub, lalu upload seluruh isi folder ini (termasuk folder `.github`).
2. Buka tab **Actions** -> workflow **Build APK** -> **Run workflow** (atau otomatis jalan saat push).
3. Setelah selesai, unduh artifact `UltrawideCameraHook-apk` -> `app-release.apk`.

Build lokal: `./gradlew assembleRelease` (butuh JDK 17 dan Android SDK).

## Pemasangan

1. Install APK.
2. LSPosed -> **Modul** -> aktifkan *Ultrawide Camera Hook*, scope `com.android.camera` (sudah disarankan otomatis).
3. Force stop aplikasi Kamera, lalu buka lagi.

## Jika tidak berhasil

Buka LSPosed -> **Log** -> **Modul**, cari baris berawalan `[UltrawideCam]`. Log memuat daftar ID kamera
beserta arah lensa dan FOV-nya. Dari situ:

- Jika kamera ultrawide terdeteksi di ID lain, isi `FORCED_ULTRAWIDE_ID` di
  `app/src/main/java/dev/ultrawide/camerahook/MainHook.java`, lalu build ulang.
- Jika log menulis "kamera ultrawide terpisah tidak ditemukan", berarti ultrawide tidak terlihat
  sebagai ID kamera tersendiri bagi aplikasi. Pendekatan ini tidak cukup dan perlu hook di dalam
  aplikasi kamera (butuh versi/APK kameranya untuk dianalisis).
