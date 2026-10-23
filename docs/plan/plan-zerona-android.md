# Plan: Zerona Android app

Status: draft (belum ada kode). Build Rust HANYA di CI, bukan di VPS (disk/CPU terbatas).

## Kondisi sekarang
- `crates/mobile` (zeron-mobile) sudah UniFFI dan crate-type `lib/staticlib/cdylib`; docs/mobile-rewrite.md bilang core yang sama akan dipakai Android.
- `scripts/android/build-core.sh` generate binding Kotlin + build `.so` via cargo-ndk (arm64-v8a, x86_64). Catatan di script: "Not yet exercised in CI".
- iOS: `apps/ios` (UIKit shell: tab bar, transcript painter CoreText di posisi dari Rust, composer, sheets).
- Tidak ada `apps/android`, tidak ada workflow Android.

## Prinsip
Rust memutuskan apa yang digambar dan di mana; platform cuma paint, scroll, gesture. Android mengikuti pola iOS, jangan bikin logika baru di Kotlin.

## Fase
### Fase 0: bukti core jalan di Android (CI saja)
- Workflow `android-core.yml` (ubuntu-24.04): setup JDK 17, Android NDK (r27), `rustup target add aarch64-linux-android x86_64-linux-android`, `cargo install cargo-ndk` (cache), jalankan `scripts/android/build-core.sh`.
- Upload artifact: `kotlin/` binding + `jniLibs/` (.so).
- Kriteria selesai: workflow hijau di PR, artifact berisi `libzeron_mobile.so` untuk 2 ABI.
- Trigger: path filter `crates/mobile/**`, `crates/client/**`, `scripts/android/**` + workflow_dispatch.

### Fase 1: shell `apps/android`
- Gradle project (Kotlin, minSdk 26, targetSdk terbaru), modul `app`.
- Pasang binding Kotlin + jniLibs dari Fase 0 (Gradle task copy dari artifact atau build-core.sh langsung di CI).
- Layar minimum: login/pairing, daftar session (front page dari `zeron-client`), transcript.
- Transcript painter: custom View/Canvas memakai posisi dan display list dari Rust (setara CoreText di iOS); Geist font bundled.
- Composer + kirim pesan lewat FFI yang sama dengan iOS.
- Kriteria: APK debug jalan di emulator x86_64 (CI: reactivecircus/android-emulator-runner) dan layar session terbuka dengan demo dataset.

### Fase 2: CI APK
- Workflow `android-app.yml`: `./gradlew assembleDebug`, upload `app-debug.apk` sebagai artifact untuk di-sideload.
- Release signing (keystore) belakangan, via secrets; sama kayak pola macOS (tanpa secret = debug-signed).

### Fase 3: paritas fitur
- Gambar di chat (PR #20 chat-images): renderer mobile dari path owner-device + URL HTTPS, harus lewat allowlist yang sama di engine (SSRF dan path escape sudah ditest di `chat_media.rs`).
- Push notification, voice, dll dikerjakan terpisah setelah dasar jalan.

## Risiko / tradeoff
- Compose vs View/Canvas: transcript virtualized dengan posisi dari Rust lebih cocok custom View; Compose dipakai untuk sheets/pengaturan saja. Putusan di awal Fase 1.
- Waktu build cargo-ndk di CI lama (belasan menit); pakai Swatinem/rust-cache.
- UniFFI 0.32 Kotlin: perlu JNA (`net.java.dev.jna:jna@aar`) di Gradle.
- iOS dan Android berbagi `crates/mobile`: perubahan API FFI harus kompatibel dua sisi, tambah cek CI untuk kedua binding.

## Urutan kerja
1. Fase 0 dulu, PR kecil terpisah dari PR #20 (branch `feat/android-core-ci`).
2. Lanjut Fase 1 hanya setelah Fase 0 hijau.
3. Semua implementasi didelegasikan ke Cursor; verifikasi lewat CI.
