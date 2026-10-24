# Plan: APK Android release-signed untuk install pribadi

## Tujuan
Menghasilkan APK Zerona Android bertipe `release`, ditandatangani dengan kunci milik pengguna, agar bisa dipasang dan diperbarui di HP pribadi tanpa bergantung pada debug signing. Belum menyiapkan Play Store atau publikasi publik.

## Rencana kerja
1. Periksa konfigurasi Gradle dan workflow Android yang berlaku di `main`; pertahankan jalur `debug` yang sekarang.
2. Tambahkan konfigurasi build type `release` dan signing config yang membaca signing material dari environment/CI secrets. Jangan pernah memasukkan keystore atau password ke Git.
3. Siapkan keystore rilis privat yang persisten. Simpan salinan cadangan terenkripsi di lokasi yang dikendalikan pengguna; unggah material yang diperlukan ke GitHub Actions Secrets tanpa menampilkan password di chat atau log.
4. Tambahkan job/step CI untuk membangun `assembleRelease`, verifikasi APK ditandatangani dengan sertifikat yang diharapkan, lalu unggah artifact bernama jelas sebagai APK pribadi.
5. Jalankan pemeriksaan statis dan CI; unduh artifact, verifikasi signature dan package/version metadata. Jika memungkinkan, pasang/update pada perangkat hanya setelah pengguna menyetujui tindakan di HP.
6. Catat fingerprint sertifikat dan instruksi backup/update. Jangan hapus branch atau mengubah release publik.

## Keputusan dan risiko
- Kunci signing adalah identitas permanen aplikasi. Kehilangan atau menggantinya dapat membuat Android menolak update; pengguna mungkin harus uninstall dan kehilangan data.
- Jangan membuat atau memasang kunci sebelum pengguna menyetujui eksekusi. Jangan pernah commit kunci, password, atau base64 keystore ke repo.
- APK release tidak otomatis berarti aman untuk distribusi luas. Scope sekarang hanya sideload pribadi.
- Jangan menaikkan version code atau mengubah application ID tanpa kebutuhan dan pemeriksaan kompatibilitas.

## Batas scope
- Tidak ada Play Store, AAB, Play App Signing, publikasi release publik, atau deployment.
- Pertahankan APK debug dan workflow yang sudah ada.
- Implementasi kode didelegasikan ke Cursor setelah persetujuan pengguna; perubahan remote secrets dan signing key dilakukan terpisah dan diverifikasi.
