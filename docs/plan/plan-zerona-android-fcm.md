# Plan — Zerona Android push notification ("Run finished")

Status: **draft v1 (detail), menunggu keputusan self-host edge + Firebase setup.**
Repo: `pancaprima/zerona` (fork), worktree `/root/zeron-android-fcm`, branch `feat/android-fcm` (base `origin/main` @ `2ad194e7`).
Tanggal: 2026-10-23.

---

## 1. Tujuan & non-goal

**Tujuan.** Android dapat notifikasi push saat sesi agent selesai ("Run finished"),
sama seperti yang sudah dimiliki desktop & iOS. Kategori notifikasi tetap tiga:
`done` (finish), `input` (nunggu jawaban), `failed` (error), semuanya bisa di-toggle
per device.

**Non-goal.**
- Mengubah aturan deteksi notifikasi (sudah ada & benar di edge, lihat §3).
- Menambah kategori notifikasi baru.
- Voice push / rich notification (FCM data-only ke voice) — itu milestone lain.
- Mengubah perilaku iOS/desktop. iOS parity adalah concern terpisah; kontrak binding
  hanya disentuh kalau di-approve eksplisit (§5).

---

## 2. Kenapa Android butuh pekerjaan baru

Push di Zerona sudah jalan untuk desktop + iOS, lewat jalur **APNs** (Apple). Android
tidak memakai APNs, jadi butuh jalur **FCM** (Firebase Cloud Messaging, punya Google).

| | APNs (sudah ada) | FCM (baru) |
|---|---|---|
| Pengirim di edge | `edge/src/apns.ts` (`sendApns`) | `edge/src/fcm.ts` (`sendFcm`) — baru |
| Payload | `push-notify.ts` `apnsPayload` | `fcmPayload` — baru |
| Auth | `.p8` → ES256 JWT | service account JSON → RS256 JWT → OAuth2 token |
| Endpoint | `api.push.apple.com` | `fcm.googleapis.com/v1/projects/{id}/messages:send` |
| Replace-id | `apns-collapse-id` | `collapse_key` |
| Device token | token APNs (hex) | FCM registration token |

Deteksi "kapan notifikasi dikirim" **tidak berubah** — itu murni di `edge/src/push-notify.ts`
(`notificationFor`): transisi status session `working → idle` + `lastCompletedTurn` baru =
`done`. Bagian ini sudah benar dan di-unit-test (`push-notify.test.ts`).

---

## 3. Arsitektur saat ini (verified dari source)

```
[HP Android] --FCM token--> /push-target (edge)
                              ^ register via CoreClient.register_push_target (FFI)
[desktop/engine] --WS push--> RegistryRoom (Cloudflare Durable Object)
                              |
                              v (applyPushBatch -> notifySessions)
                     notificationFor(prev,next) -> category done/input/failed
                              |
                              v (per registered push target)
                     sendApns(cfg, env, token, apnsPayload, chatId)  <-- APNs ONLY
```

Poin penting hasil baca source:
- `RegistryRoom.notifySessions` (`edge/src/registry-room.ts:506-555`) sudah mengirim ke
  **semua** `push_targets`; cuma satu-satunya sender adalah APNs.
- `PushTarget` (`registry-room.ts:59-65`) = `{ device, token, environment, prefs }`.
  `environment` = `"production" | "sandbox"` (khusus APNs).
- Endpoint `/push-target` (`registry-room.ts:233-256`) validasi token + environment;
  `DELETE` untuk unregister.
- `apnsConfig(env)` (`edge/src/env.ts:35`) mengembalikan `undefined` kalau `APNS_KEY_P8`
  belum di-set ⇒ notifikasi tetap di-decide & di-log, cuma tidak dikirim. Pola yang sama
  dipakai untuk FCM.
- FFI `register_push_target(token, environment, prefs)` sudah di-export
  (`crates/mobile/src/client_ffi/mod.rs:363`), dan `Client::register_push_target`
  (`crates/client/src/client.rs:1184`) sudah POST ke `/push-target`. `environment` adalah
  string APNs-only.
- Binding Kotlin di-generate UniFFI saat build (tidak di-commit); `registerPushTarget`
  akan tersedia otomatis begitu binding direbuild.

---

## 4. Rancangan perubahan per lapisan

### 4.1 Edge (Cloudflare Worker) — menambah saluran FCM

1. **`edge/src/fcm.ts`** (baru, mirror `apns.ts`):
   - `FcmConfig { serviceAccountJson, projectId }` (di-parse dari JSON service account).
   - `providerToken(cfg)` — sign JWT **RS256** pakai `private_key`, `client_email` sbg
     issuer/subject, scope `https://www.googleapis.com/auth/firebase.messaging`, lalu
     `POST https://oauth2.googleapis.com/token` (grant `urn:ietf:params:oauth:grant-type:jwt-bearer`)
     → dapat access token. Cache per-isolate (mirror `apns.ts` TTL 50 menit).
   - `sendFcm(cfg, token, payload, collapseId)` — `POST
     https://fcm.googleapis.com/v1/projects/{projectId}/messages:send` dengan body
     `{ message: { token, notification, data, android: { collapse_key, notification:{channel_id} } } }`.
   - `isDeadToken(r)` — FCM error codes: `UNREGISTERED`, `INVALID_ARGUMENT` (token invalid),
     `NOT_FOUND` (unregistered). Map ke hapus target.

2. **`edge/src/push-notify.ts`**:
   - Tambah `fcmPayload(chatId, title, category)` — payload data-only atau
     notification+data? → **notification + data** (`chatId`, `category`) supaya tap bisa
     deep-link, dengan `android.notification.channel_id` = channel notif Android.
   - `bodyFor`/`chatForNotification`/`notificationFor`/`indicatorOf` **tidak berubah**.

3. **`edge/src/registry-room.ts`**:
   - `PushTarget` tambah field `kind: "apns" | "fcm"` (default `"apns"` untuk row lama,
     biar backward compatible).
   - `/push-target` POST: terima `kind`; untuk `fcm` cukup `token` (FCM token) + `prefs`,
     tanpa `environment`. Validasi regex token terpisah (format FCM beda dari hex APNs).
   - `notifySessions`: pilih sender & payload per target → `sendApns`+`apnsPayload` vs
     `sendFcm`+`fcmPayload`; `isDeadToken` sesuai sender.

4. **`edge/src/env.ts` + `wrangler.jsonc`**:
   - Tambah `FCM_SERVICE_ACCOUNT?: string` (JSON service account, wrangler secret).
   - `fcmConfig(env)` — parse JSON → `FcmConfig`; `undefined` kalau belum di-set.

### 4.2 Core FFI (Rust / UniFFI)

- **Opsi A (rekomendasi):** tambah method baru `register_fcm_push_target(token, prefs)`
  (dan `unregister_fcm_push_target` kalau perlu beda), body POST `{ kind: "fcm", token, prefs }`.
  Method APNs yang ada **tidak disentuh** ⇒ iOS/Swift tidak berubah, binding contract hanya
  menambah (additive).
- **Opsi B:** generic-ize `register_push_target(kind, token, environment?, prefs)` —
  lebih "bersih" tapi **breaking** ke Swift call site; butuh update iOS + regenerate + verifikasi
  binding Swift. **Butuh approval eksplisit** (skill: contract expansion = obtain approval).
- Keputusan default untuk v1: **Opsi A**.

### 4.3 Android (Kotlin)

1. **Firebase setup**: tambah dependency Firebase Messaging + plugin
   `com.google.gms.google-services`, taruh `google-services.json` (dari Firebase console)
   di `apps/android/app/`. Package name harus `sh.zeron.zerona`.
2. **`ZeronFirebaseService : FirebaseMessagingService`** — `onNewToken` → register ke edge;
   `onMessageReceived` → build notif lokal (kalau foreground, tampilkan channel; parity
   desktop/iOS = quiet saat app foreground → gated).
3. **Permission & channel**: `POST_NOTIFICATIONS` runtime (API 33+), notification channel
   `"sessions"` (API 26+; minSdk sudah 26).
4. **Register/unregister**: saat signed-in → panggil `registerFcmPushTarget(token, prefs)`;
   saat sign-out → `unregister...`. Prefs `done/input/failed` + master toggle di UI
   (More/Settings), default semua on (parity `DEFAULT_PREFS`).
5. **Tap handling**: deep-link buka session dari `data.chatId` (intent-filter `zeron://callback`
   yang sudah dipakai untuk auth JANGAN dipakai buat notif — pakai path/deeplink terpisah).
6. **Quiet saat foreground**: `onMessageReceived` saat app foreground → tampilkan notif hanya
   kalau bukan chat yang sedang dibuka (parity iOS `willPresent → []`).

---

## 5. Prasyarat & keputusan terbuka

1. **[BLOKIR] Self-host edge.** Edge yang dipakai fork sekarang `edge.zeron.sh` (shared
   upstream). Service account FCM harus di-deploy ke edge yang kita kontrol. Ada plan
   `docs/plan/plan-edge-selfhost.md` (draft v2, belum dieksekusi). **Keputusan**: apakah
   FCM ikut self-host edge (rekomendasi), atau ada akses edge lain?
2. **[BLOKIR] Firebase project.** Butuh akun Google user untuk membuat project + Android
   app (`sh.zeron.zerona`) + service account key. User yang setup (dia pegang akunnya).
3. **[Keputusan desain] FFI contract** — Opsi A (method baru, rekomendasi) vs Opsi B
   (generic-ize, breaking iOS).
4. **[Keputusan kecil] `google-services.json`** di-commit ke repo (default Firebase untuk
   Android, isinya non-secret) — confirm.

---

## 6. Task list

- [ ] `edge/src/fcm.ts` — sender + providerToken + isDeadToken + unit test (mock fetch)
- [ ] `edge/src/push-notify.ts` — `fcmPayload` + test
- [ ] `edge/src/registry-room.ts` — `PushTarget.kind`, `/push-target` fcm, `notifySessions` pilih sender
- [ ] `edge/src/env.ts` + `wrangler.jsonc` — `FCM_SERVICE_ACCOUNT`, `fcmConfig`
- [ ] `crates/client` + `crates/mobile` — `register_fcm_push_target` (Opsi A) + binding
- [ ] Android: Firebase dep + `google-services.json` + plugin
- [ ] Android: `ZeronFirebaseService` + permission + channel + tap→open chat
- [ ] Android: UI toggle prefs + register saat sign-in / unregister saat sign-out
- [ ] Verifikasi: unit test edge (vitest) hijau; Android CI (`android-app.yml`) hijau
- [ ] Device test: install APK, sign-in, trigger "finish", pastikan notif muncul

---

## 7. Verifikasi

- **Edge**: unit test `push-notify` & `fcm` (vitest/workerd, mock fetch) — tidak butuh
  credential. Kirim FCM sungguhan hanya bisa setelah service account di-set.
- **FFI**: `cargo` test — tapi VPS tidak bisa full build; route ke CI `ui-tests.yml` /
  `android-core.yml` (binding). Kotlin `registerFcmPushTarget` diverifikasi dari binding
  yang di-generate CI (sumber nama/type).
- **Android**: `android-app.yml` build APK hijau; device test lewat `phone-bridge`.
- **End-to-end**: butuh edge self-host + Firebase project aktif.

## 8. Catatan kerjaan yang bisa jalan tanpa token FCM

Semua kode di §4.1 (edge) + §4.2 (FFI) bisa ditulis, di-review, dan di-unit-test **tanpa**
service account FCM / `google-services.json`. Yang butuh user: buat Firebase project +
Android app + service account key, dan self-host edge untuk deploy credential.
