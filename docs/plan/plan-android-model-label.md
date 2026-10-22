# Plan: Android read-only selected-model label

Status: implementasi (stacked di atas PR #25 `feat/android-m3b`, belum merge).

## Tujuan
Session screen di Android app menampilkan model yang dipilih untuk chat itu (mis. "Claude Sonnet 5.5 · high"), read-only. Mengubah model dari app BUKAN bagian scope ini.

## Dependensi
- Branch ini dipotong dari `feat/android-m3b` (c51aa858, PR #25) karena `SessionViewModel`/`SessionScreen` versi composer hanya ada di sana. PR ini harus diretarget ke `main` setelah #25 merge (atau merge #25 dulu).
- Tidak ada perubahan Rust. `CoreClient.sessionRow(chatId)` sudah diekspos lewat UniFFI.

## Analisis (ringkas, path:baris dari analisis Cursor, sudah dispot-check)
- Model terpilih per chat disimpan di `Chat.config` (`crates/proto/src/entities.rs:162-169`) dan sinkron antar device; katalog (label) TIDAK sinkron, tiap client/host cache sendiri.
- `SessionRow` (`crates/mobile/src/client_ffi/types.rs:481-487`) sudah punya `harness`, `harnessLabel`, `model`, `modelLabel`, `reasoning`; `CoreClient.sessionRow(chatId)` (`client_ffi/mod.rs:184-189`).
- iOS pakai pola yang sama: `row?.modelLabel ?? row?.harnessLabel` + chip reasoning (`apps/ios/Zeron/Session/CoreSessionSource.swift:50-67`).
- Fallback label: `model_label` -> raw model id (`crates/client/src/catalog.rs:532-545`).
- Gap Android: `AppModel.handleEvent` hanya naikkan `refreshTick` pada `ComposerChanged`/`SessionChanged`; perubahan config model datang sebagai `WorkspaceChanged`, jadi label tidak akan refresh kalau device lain ganti model.

## Desain
- Sumber: `client.sessionRow(chatId)`; label = `modelLabel ?: model ?: harnessLabel`; reasoning ditambahkan sebagai teks kecil ("· high") bila ada. Semua null -> tidak ada label.
- Tempat: TopAppBar session screen, baris kedua kecil di bawah judul (read-only, tidak bisa diklik). TIDAK di composer, supaya tidak bentrok dengan chip Queue/Steer yang muncul saat mengetik.
- Refresh: `AppModel` dapat `workspaceTick` StateFlow terpisah yang naik pada `WorkspaceChanged`; SessionScreen punya `LaunchedEffect` sendiri yang memanggil `viewModel.refreshModelLabel()`. Jangan menaikkan `refreshTick` pada WorkspaceChanged (menghindari refreshTranscript berlebih).
- Panggilan `sessionRow` lewat `withContext(Dispatchers.Default)`; rethrow `CancellationException` dulu di catch.
- Tidak ada panggilan relay `listModels` (opsi 2 ditunda; label bisa jatuh ke raw id kalau katalog host belum ter-cache).

## Di luar scope
- Mengubah model/reasoning dari app, model picker, `model_options`, iOS, perubahan Rust/bindings.

## File yang boleh diubah
- `apps/android/app/src/main/java/sh/zeron/zerona/app/AppModel.kt`
- `apps/android/app/src/main/java/sh/zeron/zerona/ui/SessionViewModel.kt`
- `apps/android/app/src/main/java/sh/zeron/zerona/ui/SessionScreen.kt`
- unit test Kotlin kecil untuk fungsi format label (fungsi murni, tanpa FFI) bila ada folder test yang sudah dipakai.

## Verifikasi
- Tidak ada build lokal di VPS. CI `Android app` (APK + kompilasi Kotlin) pada head SHA yang di-push, lalu uji di HP: label tampil, ikut berubah setelah model diganti dari Mac/desktop.
- Status sampai CI hijau: "belum dikompilasi".

## Keputusan
- Read-only, tanpa picker (permintaan user).
- Tempat label: top bar subtitle (non-typing, tidak bentrok dengan chip delivery).
- Basis: stacked di atas `feat/android-m3b`.
