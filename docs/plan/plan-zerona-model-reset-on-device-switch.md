# Plan: reset model eksplisit kalau tidak ada di device tujuan

Status: dikerjakan (PR terpisah dari #13). Branch `fix/model-reset-on-device-switch`.

## Masalah
Di kanvas chat baru, user memilih model (mis. `gpt`) di device A, lalu pindah ke device B
(lewat picker device atau baris Home `~`). Katalog model per-device di-revalidasi dari B
(`state_observe` di `Pickers::new`, `target_generation` naik, `stale_models`), tapi
pilihan user (`config.model`) atau model terakhir yang diingat (`defaults.model_for`)
tetap dikirim ke B walau B tidak punya model itu. Akibatnya run di B bisa gagal.
Bukti test: `home_pick_on_other_device_revalidates_catalog_and_keeps_explicit_model`
mengunci `resolved().model == Some("gpt")` setelah katalog B (tanpa `gpt`) masuk.

## Batas yang harus dijaga (desain sengaja)
- Chat TERSIMPAN tidak boleh disentuh: device-nya tetap, dan test
  `saved_model_survives_a_fresh_catalog_without_becoming_a_new_choice` serta
  `explicit_missing_model_does_not_resolve_to_the_catalog_default` mengunci bahwa ID
  eksplisit yang hilang dari katalog tetap dipertahankan. Jangan ubah perilaku itu.
- Refresh katalog di device YANG SAMA tidak boleh mereset apa pun (refresh gagal/berubah).
- Selama katalog masih stale (belum landing dari device baru), jangan reset: tunggu katalog.
- Hanya kanvas chat baru (tanpa `selected_chat`), bukan side chat/title picker.

## Rencana
1. `Pickers`: field baru `model_check_pending: HashSet<HarnessId>` (atau flag setara),
   diisi di cabang `device_changed` pada `state_observe` untuk harness yang sedang dipakai
   (dan semua harness di `stale_models`).
2. Di `land_model_reload` / `apply_model_catalog` saat `Loadable::Ready(models)` untuk harness
   yang pending: hapus dari pending, lalu cek model efektif (`effective_model_id`, hanya bila
   tidak ada `selected_chat` dan bukan title picker). Kalau ID tidak ada di `models`:
   - set `config.model = Some(<id default katalog device baru>)` (hasil
     `selected_catalog_model(models, None)`), dan bersihkan `config.reasoning` serta opsi
     yang terikat model lama bila ada di `config`;
   - set `model_notice = Some("<label lama> tidak tersedia di <nama device>, pakai <label baru>")`.
   Kalau katalog `Error`, jangan reset (tetap perilaku sekarang), pending dibuang.
3. Tampilkan `model_notice` sebagai baris info kecil di popover model (pola sama dengan
   `switch_error`, tapi warna muted, bukan danger). Bersihkan notice saat user memilih
   model/harness, atau saat `draft_owner` berubah.
4. Jangan menulis ke `defaults` (model terakhir yang diingat tetap milik pilihan user
   sebelumnya; hanya pilihan eksplisit user di device baru yang mengubahnya).

## Test (di `pickers.rs`, gaya `device_switch_revalidates_*`)
- Pilih `gpt` eksplisit di local, pindah ke remote (`select_device`), `run_until_parked`,
  land katalog remote tanpa `gpt`: `resolved().model` == default remote, notice terisi.
- Sama, tapi katalog remote memuat `gpt`: model tetap `gpt`, tidak ada notice.
- Model remembered (bukan eksplisit) yang tidak ada di remote: ikut direset ke default remote.
- Pindah device lalu katalog gagal (`Error`): tidak ada reset, tidak ada crash.
- Chat tersimpan dengan model yang tidak ada di katalog: TIDAK direset.
- Refresh katalog di device yang sama tanpa model terpilih: tidak ada reset.
- Pindah balik remote -> local: notice lama hilang.
- Update test `home_pick_on_other_device_revalidates_catalog_and_keeps_explicit_model`
  bila ada di branch ini (ada di PR #13; kalau belum merge, jangan sentuh, rebase nanti).

## Risiko
- Race: katalog landing sebelum observer jalan. Pastikan flag pending di-set sebelum landing
  dengan memakai `target_generation` yang sama seperti guard load yang ada.
- Reasoning/opsi terikat model: salah bersihkan bisa menghapus pilihan valid. Bersihkan hanya saat model benar-benar diganti.
- Build penuh tidak bisa di VPS; verifikasi lewat CI fork.

## Di luar scope
- Perilaku chat tersimpan, Settings, dan sinkronisasi default antar device.
