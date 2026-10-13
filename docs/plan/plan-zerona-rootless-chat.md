# Plan: Chat di home (`~`) salah satu device tanpa project

Status: draft, belum ada kode diubah. Branch `plan/rootless-chat`.

## Requirement
Chat langsung di "root" (= home `~`) salah satu device, tanpa menunjuk project tertentu.
Keputusan user: opsi 1, `~` sudah cukup. Bukan `/`, bukan CLI/headless.

## Yang sudah ada (hasil baca kode, origin/main 38d0da31)
- Picker project punya baris "Don't work in a project" (`crates/ui/src/pickers.rs`, `render_space_popover`, `pick_no_project`). Set `no_project = true`, `selected_space = None`.
- `composer.rs` (~8764): tanpa project, device dari picker device jadi host, session jalan dari `~`.
- Engine: `repos::expand_home` (`crates/engine/src/repos.rs:86`) expand `~`, error kalau home tidak ketemu (tidak fallback ke `/`).
- Persist: `ComposerDefaults.no_project` (`settings/composer.rs`), jadi pilihan diingat antar boot.
- `state.effective_device_id()`: tanpa project pakai `selected_device`, lalu `local_device_id`.

## Gap yang ketemu (kandidat penyebab "nggak bisa")
1. **Device picker terkunci ke member project.** `device_rows` (`pickers.rs:2550`): kalau ada project terpilih, daftar device HANYA device yang punya checkout project itu. Mau chat di `~` device lain, user harus tahu: buka project picker, pilih "Don't work in a project" dulu, baru buka device picker. Dua langkah, nggak intuitif.
2. **Opsi opt-out tersembunyi.** Baris "Don't work in a project" ada di bawah list project dengan ikon X (CLOSE), mudah dikira tombol tutup. Label tidak menyebut `~`/home, tidak jelas hasilnya.
3. **Device offline/headless.** Perlu dicek: device tanpa project (mis. VPS headless baru sync) tampil di `state.devices`? Baris offline tetap bisa dipilih tapi chat gagal diam-diam atau tidak.
4. **Label setelah dipilih.** Chip project di composer saat `no_project` perlu jelas ("~ di <device>"), bukan kosong.

Catatan: poin 3 butuh verifikasi runtime, bukan cuma baca kode.

## Rencana (kecil, UI-only, tanpa ubah proto/engine)
### Fase 0: audit (read-only, wajib sebelum coding)
- Reproduksi di Mac: pilih device lain saat project aktif, catat langkah persis dan di mana user tersangkut.
- Cek `state.devices` untuk VPS headless dan perilaku saat device offline.
- Output: catatan 5-10 baris di bawah ini, konfirmasi gap mana yang nyata.

### Fase 1: perbaikan discoverability
- Device popover: tambahkan baris/seksi "Home (`~`)" per device saat project aktif, atau tambah toggle "Tanpa project" di popover device. Pilih baris itu = `select_device(id)` + `no_project = true` sekaligus (satu klik).
- Ganti ikon dan label baris opt-out: "Chat di home (~), tanpa project", ikon folder/home (bukan CLOSE).
- Chip composer saat `no_project`: tampilkan "~ @ <nama device>".
- Pastikan `remember_target` menyimpan `device` + `no_project` bersama.

### Fase 2: edge case
- Device offline: tampilkan glyph offline (sudah ada) dan blok kirim dengan pesan jelas.
- Home tidak ketemu di host: pastikan error engine muncul di UI, bukan hilang.
- Sidebar: grup `~` per device tetap muncul untuk chat baru.

## File yang kemungkinan disentuh
- `crates/ui/src/pickers.rs` (`device_rows`, `render_device_popover`, `render_space_popover`, `pick_device_row`)
- `crates/ui/src/state.rs` (`select_device`, mungkin helper `select_device_no_project`)
- `crates/ui/src/composer.rs` (label chip)
- Test: `state.rs` (sekitar baris 4799-4945) dan `shell.rs` (~15885), tambah kasus "project aktif -> pilih Home device lain -> no_project true, device benar".

## Risiko
- Rendah: tidak ada perubahan proto/engine/sandbox. `~` sudah dipakai jalur projectless mobile/voice.
- `device_rows` dipakai juga navigasi keyboard (`selected_device_index`, `active`), baris baru harus ikut index agar nggak geser.
- Build penuh tidak bisa di VPS (1 vCPU). Verifikasi lewat CI fork (`scripts/ci/test-core.sh`) dan cek manual di Mac.

## Di luar scope
- Root `/` atau folder custom, `zeron chat --device` dari CLI. Kalau nanti dibutuhkan, plan terpisah.

## Keputusan
- Device picker: baris "Home (`~`)" per device (opsi A), dipilih user. Toggle opsi B tidak dipakai.
