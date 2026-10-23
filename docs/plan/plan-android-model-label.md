# Plan: Android read-only selected-model label

Status: **approved for integration** on post-PR30 `main` (`077620f3`, native transcript / Study01 painter). Implementation on `feat/android-model-label` after merge of `origin/main` (PR29+PR30).

## Tujuan
Session screen menampilkan model yang dipilih untuk chat itu (mis. `Claude Sonnet 5.5 · high`), **read-only**. Mengubah model dari app Android bukan bagian scope.

## Dependensi
- Basis: `origin/main` @ `077620f3` (native `TranscriptListView` / `FrameRelay`, bukan M3b `LazyColumn`).
- Tidak ada perubahan Rust. `CoreClient.sessionRow(chatId)` sudah diekspos lewat UniFFI.

## Analisis (ringkas)
- Model per chat di `Chat.config`; katalog label per host/client (`modelLabel` bisa null → fallback raw `model` → `harnessLabel`).
- `SessionRow` (`crates/mobile/src/client_ffi/types.rs`) punya `model`, `modelLabel`, `harnessLabel`, `reasoning`.
- iOS: pola serupa (`CoreSessionSource.swift`).
- Gap: `WorkspaceChanged` tidak memicu `refreshTick` (hanya composer/session events) → subtitle butuh `workspaceTick` terpisah.

## Desain (disetujui)
- **Sumber:** `client.sessionRow(chatId)` di `Dispatchers.Default`; formatter murni `formatModelLabel` di `ui/ModelLabelPresentation.kt`.
- **Label:** `modelLabel ?: model ?: harnessLabel`; reasoning ditambah ` · {reasoning}` hanya jika base non-blank; semua blank → `null` (tanpa subtitle, tanpa default palsu).
- **UI:** `TopAppBar` baris kedua (`labelSmall`, `onSurfaceVariant`, max 1 baris + ellipsis); judul tetap `titleMedium` SemiBold; semantics `Model: …`. **Lab:** tidak panggil `sessionRow`, tidak tampilkan subtitle.
- **Refresh:** `AppModel.workspaceTick` naik hanya pada `WorkspaceChanged` ( **`refreshTick` tidak diubah** ). `SessionScreen` `LaunchedEffect(workspaceTick)` → `refreshModelLabel()`. Pembukaan session non-lab juga memanggil `refreshModelLabel()` sekali. Tidak memicu `refreshHydration` / frame repaint hanya untuk label.
- **Lifecycle:** `CancellationException` di-rethrow; `client == null` aman; job refresh dibatalkan saat refresh baru; hasil stale tidak menimpa jika `handle` sudah null.
- Tidak ada picker, context meter, atau `listModels` relay.

## Di luar scope
- Model picker, edit config, Rust/bindings, perubahan transcript/composer/theme.

## File yang boleh diubah (allowlist)
- `apps/android/app/src/main/java/sh/zeron/zerona/app/AppModel.kt` — hanya `workspaceCounter` / `workspaceTick` + bump pada `WorkspaceChanged`.
- `apps/android/app/src/main/java/sh/zeron/zerona/ui/SessionViewModel.kt` — `modelLabel`, `refreshModelLabel()`.
- `apps/android/app/src/main/java/sh/zeron/zerona/ui/SessionScreen.kt` — subtitle + `LaunchedEffect(workspaceTick)`.
- `apps/android/app/src/main/java/sh/zeron/zerona/ui/ModelLabelPresentation.kt` (+ `ModelLabelPresentationTest.kt`).
- `scripts/android/test-design-helpers.py` — daftar sumber/kelas pure JVM bila perlu.
- Dokumen ini.

## Verifikasi
| Lapisan | Status |
|--------|--------|
| Pure JVM `formatModelLabel` | `test-design-helpers.py` (kelas `ModelLabelPresentationTest`) |
| `assembleDebug` / `testDebugUnitTest` | CI (Gradle task chain di `app/build.gradle.kts`) — tidak dijalankan di VPS integrasi ini |
| Device: label tampil / ikut `WorkspaceChanged` | **Pending** (parent / HP) |
| Device: ganti model dari Mac → subtitle update tanpa flicker transcript | **Pending** |
| Lab: tanpa subtitle | By code (`isLab`) |

## Native / gap
- Tidak ada API gap di luar scope; refresh label saat model diubah dari desktop/Mac masih perlu uji manual end-to-end.

## Keputusan
- Read-only subtitle di top bar; `workspaceTick` terpisah dari `refreshTick`.
