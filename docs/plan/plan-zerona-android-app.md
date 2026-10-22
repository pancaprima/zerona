# Plan: Zerona Android app (Fase 1 + 2, jalur menuju APK yang bisa dicoba)

Lanjutan `plan-zerona-android.md`. Fase 0 (core .so + binding Kotlin di CI) sudah hijau (PR #21).
Build Rust/Gradle HANYA di CI. Semua implementasi didelegasikan ke Cursor; verifikasi lewat CI.

## Prinsip
Rust memutuskan apa yang digambar dan di mana; Android cuma paint, scroll, gesture (sama seperti `apps/ios`).
Tidak ada logika baru di Kotlin selain glue UI.

## Milestone (tiap milestone = APK debug yang bisa di-sideload)
- **M1 APK pertama (demo mode):** Gradle project `apps/android` (Kotlin, Compose, minSdk 26), memuat `libzeron_mobile.so`
  (arm64-v8a untuk HP, x86_64 untuk emulator) + binding UniFFI, `CoreClient` mode Demo, layar daftar session
  (`front_page` / `projects`) dan layar transcript sederhana. Workflow `android-app.yml` menghasilkan `app-debug.apk`.
- **M2 Transcript penuh:** painter display list dari `layout` Rust di Canvas (Geist font bundled, setara CoreText iOS),
  streaming veil, tool rows.
- **M3 Sign-in + live:** WorkOS lewat Custom Tab + custom scheme callback (`auth_*` FFI), simpan token (EncryptedSharedPreferences),
  `CoreClient` live, composer kirim pesan.
- **M4 Paritas:** gambar di chat (PR #20), voice, push.

## Keputusan teknis
- Compose untuk shell (list, sheet, settings); transcript = custom View/Canvas (virtualized, posisi dari Rust).
- Gradle: tanpa wrapper di repo; CI memakai distribusi Gradle yang di-pin (unduh dari services.gradle.org), AGP + Kotlin yang kompatibel.
- UniFFI Kotlin butuh JNA (`net.java.dev.jna:jna:<ver>@aar`).
- APK hanya ABI `arm64-v8a` (+ `x86_64` opsional lewat input workflow) supaya ukuran wajar; `.so` di-strip oleh AGP.

## Risiko
- Kotlin/Compose compile error baru terlihat di CI (VPS tidak build) -> loop perbaikan berbasis log CI.
- Ukuran APK (`.so` besar) -> pakai profile `mobile-dist` kalau perlu.
- Auth live butuh akun/edge yang valid; M1 sengaja demo-only.
