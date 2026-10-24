# Plan: Android notification small icon (dua segitiga, sudut dibulatkan)

Status: plan, belum diimplementasi.

## Keputusan
- Desain terpilih: **2a-r2** = dua segitiga siku di pojok berlawanan (kiri atas, kanan bawah) dengan celah diagonal, sudut dibulatkan sedang (radius ±1dp pada kanvas 24dp). Dipilih dari opsi futuristik; selaras dengan ikon app low-poly ungu.
- Satu siluet untuk semua kategori (`done` / `input` / `failed`). Varian per kategori ditunda.
- Preview acuan (bukan aset final): `cache/scratch/icons/preview2a_round.png`, mask `2a-r2_alpha.png`.

## Temuan recon (origin/main)
- `fcm/Notifications.kt:108` memakai `setSmallIcon(R.mipmap.ic_launcher)` (adaptive icon), tampil kosong sebagai small icon. Ini jalur **app foreground** (notifikasi lokal dari `onMessageReceived`).
- `AndroidManifest.xml` belum punya `com.google.firebase.messaging.default_notification_icon` / `default_notification_color`. Jalur **app background/killed** digambar FCM/sistem, jadi tanpa meta-data itu juga jatuh ke ikon launcher (kosong atau kotak abu). Mengganti `setSmallIcon` saja tidak cukup.
- Edge hanya mengirim `android.notification.channel_id = "sessions"` (`edge/src/push-notify.ts`); tidak perlu diubah.
- Tidak ada drawable monokrom saat ini; `res/drawable/` hanya berisi `ic_launcher_foreground.xml`.
- Warna tersedia: `zerona_primary` `#5C45A8`.

## Lingkup perubahan (allowlist)
1. `apps/android/app/src/main/res/drawable/ic_stat_zerona.xml` (baru): VectorDrawable 24dp x 24dp, viewport 24x24, satu `<path>` `fillColor="#FFFFFF"`, tanpa gradient/stroke tipis/alpha trik. Geometri (sebelum pembulatan): segitiga A (3.25,3.25) (21.25,3.25) (3.25,14.75); segitiga B (21.25,20.75) (3.25,20.75) (21.25,9.25). Sudut dibulatkan dengan fillet geometris (arc/quad), bukan hasil trace bitmap.
2. `fcm/Notifications.kt`: `setSmallIcon(R.drawable.ic_stat_zerona)` + `setColor(...)` dari resource warna.
3. `AndroidManifest.xml`: dua `<meta-data>` di `<application>`: `default_notification_icon` -> `@drawable/ic_stat_zerona`, `default_notification_color` -> warna aksen.
4. `res/values/colors.xml`: tambah `zerona_notification_accent` hanya jika warna aksen berbeda dari `zerona_primary`.

## Di luar lingkup
Ikon launcher, edge/payload FCM, iOS, varian per kategori, perubahan layout atau logika notifikasi, Gradle/CI.

## Verifikasi
- Tidak ada build Android di VPS. Pastikan path vector valid dengan rasterisasi path itu sendiri di Python (parser M/L/Q/A/Z sederhana) dan bandingkan IoU terhadap mask r2 (target >= 0.97), sebagai bukti bentuk, bukan bukti kompilasi.
- CI `android-app.yml` pada PR draft: `assembleDebug` + lint resource.
- Device (Pixel 3 / A07), semua gabungan: app foreground, app background, app killed; status bar gelap dan terang; notification shade mode terang dan gelap. Cek ujung segitiga tidak hilang di mdpi dan warna aksen terbaca di shade gelap.
- Laporkan terpisah: terverifikasi dari source vs teramati di device.

## Keputusan terbuka
- Warna aksen: `zerona_primary` `#5C45A8` (kalem, tapi kontras rendah di shade gelap) vs lilac lebih terang, mis. `#C4B5FD`. Default: `zerona_primary`, putuskan setelah lihat di device.

## Delegasi
- Implementasi lewat `cursor-me` (composer-2.5), allowlist file di atas, larang gradle/cargo dan commit; diff direview manual sebelum commit/push.
