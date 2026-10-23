# Plan: Zerona Android M3 (sign-in live + kirim pesan)

Status: M3a sudah merged (PR #24, login terbukti di HP). M3b (composer + kirim) diimplementasi di branch `feat/android-m3b`, belum dites di HP. Lanjutan `plan-zerona-android-app.md` (M1 demo sudah di `main`, PR #23).
Build Rust/Gradle HANYA di CI. Implementasi didelegasikan ke Cursor; verifikasi lewat CI + HP (Pixel 3 / A07).

## Tujuan
- User bisa sign-in (WorkOS) di Android, app memakai `CoreClient` live (bukan Demo), dan bisa kirim pesan ke sesi nyata.

## Non-goal
- Painter transcript penuh (itu M2), gambar di chat, voice, push (M4), FCM.

## Kondisi sekarang (sudah dicek path:baris)
- Android selalu `Credentials.Demo(...)` + `dataDir = filesDir/demo`: `apps/android/.../app/AppModel.kt:100-123`.
- Composer placeholder "Sign-in required to send (demo)": `apps/android/.../ui/SessionScreen.kt:64-76`.
- Core sudah punya semua FFI auth: `workos_authorize_url`, `parse_auth_callback`, `auth_exchange_code`, `auth_list_orgs`, `auth_refresh` di `crates/mobile/src/client_ffi/mod.rs:546-587`; redirect `zeron://callback` (`crates/client/src/auth.rs:33-34`); refresh token otomatis di Rust (`auth.rs` TokenProvider).
- iOS referensi: `apps/ios/Zeron/Shell/SignInViewController.swift:91-116` (sign-in), `apps/ios/Zeron/App/AppModel.swift:155-216` (live start + exchange/org/refresh), `AppModel.swift:671-714` (simpan token di Keychain).
- Event yang belum ditangani Android: `ConnectivityChanged`, `AuthRefreshed`, `AuthExpired` (`AppModel.kt:139-150` baru 3 event workspace/session/composer).
- Manifest cuma LAUNCHER (belum ada intent-filter `zeron://callback`); `allowBackup=true`; belum ada `security-crypto`.

## Urutan FFI sign-in (Kotlin, `uniffi.zeron_core`)
1. `workosAuthorizeUrl(state)` -> buka lewat Custom Tab.
2. Redirect `zeron://callback?code&state` -> `parseAuthCallback(url)`; cek `state` cocok.
3. `authExchangeCode(edgeUrl, code)` -> `AuthExchange(user, tokens)`.
4. `authListOrgs(edgeUrl, accessToken)` -> pilih org (auto kalau 1).
5. `authRefresh(edgeUrl, refreshToken, orgId)` -> token scoped org.
6. Simpan `userId/orgId/access/refresh`, lalu `CoreClient(config, Credentials.WorkOs(...), listener)` dengan `dataDir` per `userId/orgId`.
7. Runtime: `AuthRefreshed` -> simpan token baru + `client.updateTokens`; `AuthExpired` -> shutdown + clear + balik ke layar sign-in.

## Opsi pemecahan
- A: M2 painter dulu, baru auth + send. Aman visual, tapi lama sebelum ada hasil yang bisa dicoba.
- **B (rekomendasi): M3a auth + live client -> M3b composer + send -> M2 painter.** Transcript M1 (teks polos) cukup untuk validasi pesan masuk/keluar; tiap tahap = APK yang bisa dicoba.
- C: satu PR besar. Risiko compile error numpuk (VPS tidak build), review susah.

## Sub-milestone B
### M3a: sign-in + live client (PR sendiri) — [x] kode
- [x] Layar sign-in + gate navigasi (belum login -> SignIn, sudah -> Sessions).
- [x] Custom Tab (`androidx.browser`) + intent-filter `zeron://callback` di `AndroidManifest.xml`; `launchMode=singleTask` biar callback balik ke Activity yang sama.
- [x] `CredentialsStore.kt` (EncryptedSharedPreferences / `security-crypto`), `allowBackup=false`.
- [x] `AppModel.kt`: cabang `startLive` vs `startDemo`; tangani `AuthRefreshed`, `AuthExpired`; `ConnectivityManager` -> `setNetworkOnline`; lifecycle `onForeground`/`onBackground`.
- [x] Picker org kalau user punya >1 org.
- [x] Tombol Sign out (hapus kredensial; kebijakan `dataDir` lihat keputusan #4).
- [x] Mode Demo tetap ada (tombol di layar sign-in) supaya APK tetap bisa dicoba tanpa akun.

File: `AppModel.kt`, baru `CredentialsStore.kt`, `SignInScreen.kt`, `ZeronaApp.kt`, `MainActivity.kt`, `AndroidManifest.xml`, `build.gradle.kts`, `ClientListenerBridge.kt`.

### M3b: composer + kirim pesan (PR sendiri, setelah M3a) — [x] kode
- Composer Compose di `SessionScreen.kt` menggantikan placeholder; `SessionHandle.send(SendRequest(text, attachments, worktree?, busy))`.
- Sesi sibuk: Queue vs Steer (interrupt lalu send), mengikuti iOS `CoreSessionSource.swift:123-126`.
- Baca state composer lewat `handle.composer()` saat `ComposerChanged`.
- Tanpa attachments dulu (M4).

File: `SessionScreen.kt`, `SessionViewModel.kt`, baru `Composer*.kt`, sedikit `AppModel.kt`.

### M2 (setelah itu): painter display list (di luar plan ini)
Gap M1: teks polos `frame.messageText(i)` (`SessionViewModel.kt:92-95`) vs `LayoutFrame.display(index)` iOS; tool/thought rows, streaming veil, widget belum tampil.

## Risiko
- Redirect `zeron://callback` harus terdaftar di WorkOS untuk app Android (iOS sudah). Tanpa itu login gagal.
- Kirim pesan live butuh org WorkOS nyata + execution host online (mis. Mac relay).
- Token: jangan masuk backup, jangan di-log; intent-filter hanya untuk scheme `zeron`, host `callback`.
- Compile error Kotlin hanya terlihat di CI: siapkan 2-3 ronde fix berbasis log CI.
- OAuth Custom Tab, rotasi refresh, dan kirim pesan nyata hanya bisa diuji di HP, bukan CI.

## Keputusan (dijawab user 2026-10-22)
1. Akun tes: akun Zeron yang sama dengan di Mac. Fork memakai backend + WorkOS client upstream (`README.md:5`), user tidak punya panel WorkOS sendiri. Dicek: `redirect_uri=zeron://callback` DITERIMA WorkOS (302 ke authkit.app), URI acak DITOLAK (`redirect-uri-invalid`). Android wajib memakai persis `zeron://callback`; tidak ada yang perlu didaftarkan.
2. Edge: prod saja (`authProductionEdgeUrl()`), tanpa opsi staging.
3. URL authorize: `workosAuthorizeUrl(state)` dari Rust, tanpa duplikasi `client_id`.
4. Sign-out: sama dengan iOS. Sign-out yang diminta user menghapus `dataDir` live; sesi expired (`AuthExpired`) mempertahankan store (outbox bisa berisi pesan belum terkirim) dan marker `.owner` (`userId/orgId`) mencegah akun lain membaca store itu (`AppModel.swift:136-167`, `226-240`).
5. Host tes kirim pesan: Mac user.

## Verifikasi
- CI: `android-app.yml` hijau (APK + `libc++_shared.so` assert), tes Rust `parse_auth_callback` yang sudah ada tetap hijau.
- HP (Pixel 3, lalu A07): sign-in lewat Custom Tab, relaunch tetap login, kirim pesan muncul di transcript, airplane-mode tidak crash, sign out balik ke layar sign-in. Bukti: logcat + `uiautomator dump`, bukan cuma screenshot.

## Rollback
- Tiap sub-milestone = satu PR; revert PR-nya. Mode Demo tetap jalan sebagai fallback.
