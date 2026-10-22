# Fix: "↓ Latest" berhenti dengan viewport kosong (transcript blank)

Status: implementasi (ronde 3) — setelah commit `6b4a2126`.

## Apa yang sudah terbukti di Pixel 3 (build `6b4a2126`)

1. **Invarian tinggi container: BERES.**
   `dumpsys` konsisten: `viewport=1483 content=446797 maxScroll=445314`,
   `lastRowBottom=446749`, `rowsBeyondContent=[]`, dan angkanya tidak berubah
   selama 15+ detik. Container sekarang selalu >= bawah baris terakhir.

2. **Tombol "↓ Latest" masih rusak, dan sekarang lebih parah: layar jadi KOSONG.**
   Reproduksi: scroll ke atas, tap "Latest" sekali.
   - `ZScroll: jump` lalu `drain max=445314 scrollY=359223 child.height=446797`.
   - 1,5 detik sampai 15 detik kemudian: `rows=5 lastRowBottom=370696`,
     `rowsBeyondContent=[]` (stabil, bukan animasi).
   - `uiautomator dump`: tidak ada satu pun baris transcript yang ter-expose;
     screenshot 18 KB = area transcript kosong (polos). Yang kelihatan cuma
     app bar + kolom "Message".
   - Tombol "Latest" hilang (dianggap sudah di bawah), padahal yang dilihat user
     cuma layar kosong.
   - Setelah satu swipe kecil (finger turun ~600 px), konten muncul lagi dan
     `ZScroll: follow=false scrollY=445097 max=445314` — artinya posisi scroll
     sebenarnya SUDAH dekat ujung. Jadi scroll-nya sampai, tapi **RowView yang
     ter-layout masih milik jendela lama** (sekitar 366k-370k), tidak ada baris
     yang ter-attach untuk jendela ujung (445k-446k) → viewport kosong sampai
     user menyentuh layar.

## Akar masalah (hipotesis, berbasis bukti di atas)

`scrollTo(0, max)` terjadi di dalam `drainPendingScrollsAfterLayout()` (dipanggil
dari `onLayout`), sementara `layoutRows()` di jalur yang sama masih memakai
`scrollY` lama / RowView pool lama. Tidak ada jaminan ada pass `layoutRows()`
setelah posisi scroll benar-benar final, jadi set baris yang tergambar tidak
pernah sinkron dengan posisi akhir. Sentuhan user "menyelamatkan" tampilan
karena gesture memicu `layoutRows()` baru.

## Arah fix

1. Pisahkan "pindahkan scroll" dari "susun baris": setelah scroll final, WAJIB
   ada `layoutRows()` untuk `scrollY` saat itu (jadwalkan lewat `post`/
   next-frame, bukan di tengah `onLayout` yang sama).
2. Jangan biarkan `following=true` + `Latest` disembunyikan sebelum baris untuk
   jendela ujung benar-benar ter-layout (blank != "sudah di bawah").
3. Tambah log `ZScroll` yang bisa dibaca di perangkat: `scrollY` SETELAH
   `scrollTo`, rentang jendela (`y0..y1`), jumlah baris ter-layout, dan jumlah
   baris yang benar-benar ter-attach.

## Verifikasi

- JVM test untuk helper murni (`TranscriptAnchor`/window math) kalau ada.
- Di Pixel 3: tap "Latest" dari atas → layar langsung menampilkan pesan terakhir
  (bukan kosong), `lastRowBottom` mendekati `content`, tombol Latest hilang,
  dan tidak perlu swipe untuk memunculkan isi.
