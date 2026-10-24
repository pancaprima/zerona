# Plan: Android launcher icon = potret ungu (selaras icon Mac)

Status: diimplementasi di branch ini; menunggu CI dan cek di device.

## Keputusan
- Launcher icon Android memakai potret low-poly yang sama dengan icon Mac, dalam gaya **P1**: gradasi ungu pekat (semua nada dipetakan ke satu rona ungu gelap-ke-lilac) supaya terkesan siluet ungu.
- Adaptive icon: potret 72dp di tengah layer 108dp, latar solid `#0F0635` (diambil dari tepi artwork) supaya mask apa pun tidak menampilkan jahitan. Dipilih dari varian A (pas area terlihat); varian zoom (84dp) ditolak karena memotong rambut.
- Foreground berupa PNG opak 648x648 (108dp @ 6x) di `drawable-nodpi`, menggantikan placeholder belah ketupat `ic_launcher_foreground.xml`.
- Tidak ada layer monochrome/themed icon pada PR ini (potret tidak cocok jadi siluet satu warna). Bisa ditambah terpisah memakai siluet dua segitiga dari icon notif.

## Lingkup (allowlist)
1. `apps/android/app/src/main/res/drawable-nodpi/ic_launcher_foreground.png` (baru, dibuat oleh Asa dari artwork repo).
2. `apps/android/app/src/main/res/drawable/ic_launcher_foreground.xml` (dihapus, agar tidak bentrok nama resource).
3. `apps/android/app/src/main/res/values/colors.xml`: `ic_launcher_background` -> `#0F0635`.
4. `apps/android/app/src/main/res/values-night/colors.xml`: samakan jika ada override `ic_launcher_background`.
`mipmap-anydpi-v26/ic_launcher.xml` tidak berubah (sudah merujuk `@drawable/ic_launcher_foreground` dan `@color/ic_launcher_background`).

## Di luar lingkup
Icon notif (PR #36), icon desktop/iOS, layer monochrome, nama app, tema.

## Verifikasi
- CI `android-app.yml`: `assembleDebug` (merge resource, tidak ada duplikasi nama).
- Device: bentuk mask launcher (circle/squircle), kebacaan di 48dp, layar app info. Terverifikasi di device hanya setelah user melihat sendiri.
