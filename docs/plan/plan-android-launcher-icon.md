# Plan: Android launcher icon = neon line-art (revisi dari potret ungu / P1)

Status: revisi artwork, menunggu CI dan cek device.

## Keputusan
- Launcher icon Android memakai artwork **neon line-art** pilihan user (potret profil bergaya neon, tanpa frame)—bukan potret low-poly P1 (gradasi ungu selaras icon Mac) yang direncanakan awal.
- Frame rounded-square neon pada gambar sumber **sengaja dibuang** (di-crop dan tepi di-fade ke latar): di mask circle terpotong, di squircle terlihat sebagai bingkai ganda.
- Adaptive icon: potret **66dp** di tengah layer 108dp (zona aman). Dipilih dari varian A (frame utuh, ditolak), B (72dp tanpa frame, tepi terlalu mepet), C (**66dp**, dipilih).
- Latar solid `#09052B` diambil dari warna latar di dalam gambar, supaya mask apa pun tidak menampilkan jahitan.
- Foreground berupa PNG opak **648×648** (108dp @ 6x) di `drawable-nodpi`, menggantikan placeholder belah ketupat `ic_launcher_foreground.xml`.
- Tidak ada layer monochrome/themed icon pada PR ini (potret tidak cocok jadi siluet satu warna). Bisa ditambah terpisah memakai siluet dua segitiga dari icon notif.

## Lingkup (allowlist)
1. `apps/android/app/src/main/res/drawable-nodpi/ic_launcher_foreground.png` (baru, dibuat oleh Asa dari gambar neon kiriman user; frame di-crop dan tepi di-fade ke latar).
2. `apps/android/app/src/main/res/drawable/ic_launcher_foreground.xml` (dihapus, agar tidak bentrok nama resource).
3. `apps/android/app/src/main/res/values/colors.xml`: `ic_launcher_background` -> `#09052B`.
4. `apps/android/app/src/main/res/values-night/colors.xml`: samakan jika ada override `ic_launcher_background`.
`mipmap-anydpi-v26/ic_launcher.xml` tidak berubah (sudah merujuk `@drawable/ic_launcher_foreground` dan `@color/ic_launcher_background`).

## Di luar lingkup
Icon notif (PR #36), icon desktop/iOS, layer monochrome, nama app, tema.

## Verifikasi
- CI `android-app.yml`: `assembleDebug` (merge resource, tidak ada duplikasi nama).
- Device: bentuk mask launcher (circle/squircle), kebacaan di 48dp, layar app info. Terverifikasi di device hanya setelah user melihat sendiri.
