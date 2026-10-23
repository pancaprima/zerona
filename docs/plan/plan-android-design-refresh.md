# Zerona Android: light design refresh

Status: IMPLEMENTATION AUTHORISED. User explicitly requested implementation with Cursor Composer. Selected: calm v1 palette + Edge Dock option 4 + subtle motion. Implement on an isolated Android feature branch; review/test and CI before handing over APK. No merge/release authorised.

## Implementation contract
- Worktree /root/zeron-android-redesign, branch feat/android-design-refresh, base origin/main 362c561495b790da00072b5df50acec9c7df8d5f. Composer/send #25 and native painter #26 are merged on this base.
- Keep independent PR28 ZScroll log removal untouched. PR27 model label remains separate: no legacy transcript/UI replacement, cherry-pick or merge; style existing header without inventing new model/config controls.
- Implement visual tokens, typography/shape hierarchy, sign-in/org picker/fatal error surfaces, sessions/badges/account menu, session chrome/Latest, Edge Dock composer states, and matching native transcript/menu/panel/image-preview colours. Existing capabilities only, no new navigation/auth/messaging features.
- Edge Dock: opaque full-width bottom region, thin top divider, no floating enclosing card/pill, integrated input and trailing send, contextual Queue/Steer, multiline growth. Preserve existing send, retry, offline, queue action, demo/lab gating and draft behaviour.
- Motion stays on shell/composer/controls: about 160-240ms, content-size and contextual appearance; preserve system animator-duration/reduced-motion handling. Never animate/rewrite native scroll positioning or double-apply duration scaling.
- Preserve AndroidView lifecycle, viewport measurement, font scaling, follow-tail/jump fixes, image loading/selection/link semantics. No changes to Rust/UniFFI, SessionViewModel state machine, AppModel/auth logic, TranscriptListView geometry, or logging-removal scope.
- Native dialogue theme must follow system light/dark rather than inherit the current forced-light Activity theme, with explicit readable foreground/background roles on custom text/buttons/checkboxes. Preserve every menu action and controller lifetime.
- Commit design plan/reference images first; delegate implementation/testing through personal cursor-me composer-2.5 non-fast. Cursor must not commit/push or run Cargo/Gradle on VPS. Cheap pure-Kotlin/JVM tests may use cached compiler with actual production helpers.
- Verify test-first theme contrast pairs and composer presentation rules, then inspect complete diff. Android CI must execute :app:testDebugUnitTest and :app:assembleDebug for the exact candidate commit; confirm libzeron_mobile.so and libc++_shared.so packaging.
- Visual evidence is separate: test signed-out/demo paths and lab on available test phone; real-send/authenticated flows require user login and an online execution host. Report unverified native behaviours explicitly.
- Design references: docs/design/zerona-android/edge-dock-light.png and edge-dock-dark.png, plus permanent full catalog /root/.hermes/profiles/girlfriend/design/zerona-android-v1-catalog/ and input gallery /root/.hermes/profiles/girlfriend/design/zerona-composer-options/.

## Keputusan dan mockup pertama
- Arah pilihan user: opsi 2, lebih expressive dan fun.
- Eksplorasi awal: lilac primary, peach dan mint accents, rounded tonal surfaces, hierarki teks yang lebih playful. Warna masih usulan, belum keputusan user.
- Mockup statis HTML, bukan screenshot aplikasi atau implementasi Compose. Data session, model dan status adalah ilustrasi.
- Source mock dan renderer: /root/.hermes/profiles/girlfriend/cache/scratch/zerona-expressive-mock/index.html dan render.py. Scratch bukan arsip permanen; pindahkan source ke lokasi proyek bila arah disetujui.
- Output: light.png dan dark.png, masing-masing 1000x1060, menampilkan Sessions + Chat. Crop chat tersedia 390x844.
- Parent menjalankan ulang render.py: empat PNG berhasil, tanpa console errors/warnings.
- QA visual: tidak ada clipping/overlap yang terlihat. Metadata dan code block dark mode perlu penyesuaian ukuran/kontras sebelum implementasi.
- Mock ini baru studi visual dua layar, belum preview keyboard-open, prototype interaktif atau bukti aksesibilitas native.
- Tunggu feedback visual user sebelum mengubah kode aplikasi.

## Mockup kedua: lebih berani
- Feedback user: "lebih berani lagi coba". Tetap mock-only; belum izin implementasi Android.
- V2 memakai violet lebih jenuh, coral dan lime, blok warna tegas, judul dua baris serta motif empat kelopak. Area isi chat tetap lebih tenang.
- Source dan renderer terpisah di /root/.hermes/profiles/girlfriend/cache/scratch/zerona-expressive-mock-v2/. V1 tidak ditimpa.
- Render ulang oleh parent menghasilkan empat PNG, board 1000x1060 dan crop chat 390x844, tanpa browser warnings/errors.
- QA visual tidak menemukan clipping/overlap yang berarti. Kontras teks sekunder dan warna syntax code masih perlu dipoles sebelum produksi. Ini studi visual, bukan validasi native atau aksesibilitas.
- Semua data contoh ilustratif. Footer status idle tidak konsisten dengan chip Working; jangan jadikan footer contoh kontrak state aplikasi.

## Katalog lengkap: keputusan terbaru dan hasil
- User memilih mock pertama, bukan bold v2. Lilac/peach/mint tenang menjadi arah visual; rasa fun melalui motion halus, bukan saturasi lebih kuat.
- User meminta mock setiap komponen Android yang ada. Inventaris dibaca dari painter 44cb33a4 dan delta label model ef33a38a; tidak menganggap semua branch sudah tergabung.
- Source katalog permanen: /root/.hermes/profiles/girlfriend/design/zerona-android-v1-catalog/.
- 82 entri katalog, 116 variasi/state, delapan board dalam light dan dark; 71 existing, empat fixture, empat proposal/annotation, satu branch-specific, dua renderer-only. Label ini tertera di inventory.json/COVERAGE.md, bukan klaim 82 komponen native terpasang.
- Bagian: akses/akun; session/shell; chat/navigasi; composer; transcript; tool/rendering; aksi pesan/link; gambar/lab/motion. Tujuh kelompok yang tidak tersedia atau di luar UI Android ditandai excluded.
- Deliverable: index.html mandiri, PDF 16 halaman, 16 board PNG, preview dan ZIP lengkap dengan generator/renderer.
- Parent menjalankan ulang render.py, mengecek hitungan unik dan kombinasi board/tema, sumber, arsip ZIP, dan inspeksi visual. verification.json: passed true, issues kosong.
- ZIP diekstrak dan dibuka offline pada viewport 375px; tema, pemilih board, Play motion dan reduced motion diuji sukses, tanpa request jaringan atau page errors.
- Perubahan native tetap nol; hanya file plan di worktree berubah. Mock menu/state bukan workflow aplikasi sungguhan.
- Animasi berupa prototype/arah motion dengan Play di galeri, bukan animasi Compose yang sudah diimplementasikan. Keyboard/seleksi berupa simulasi, bukan validasi Android asli.
- Motion: transisi sekitar 160-240ms, reveal existing sekitar 220ms, tanpa menggeser posisi baca atau animasi berlebihan; hormati reduced-motion.

## Revisi kontras dark mode dan lima opsi input
- Feedback user: ada teks hitam di dark mode, visibilitas rendah; minta lima desain input dengan vibe yang sama.
- Reproduksi DOM menemukan button hitam bawaan browser pada dialog/menu/Latest/tool/panel/preview/lab/motion. Perbaikan di semantic foreground/background tokens dan pewarisan warna form controls, bukan memutihkan semua teks (badge terang tetap foreground gelap).
- Regresi sebelum perbaikan mencatat 61 kegagalan kontrol; audit diperluas ke toolbar/placeholder menghasilkan perbaikan tambahan. Parent menjalankan ulang audit final katalog: 210 lulus, enam peringatan kontrol disabled, nol gagal. Ini audit HTML terpilih, bukan sertifikasi aksesibilitas seluruh aplikasi.
- Katalog lengkap, PDF dan ZIP dirender ulang dengan warna diperbaiki. Masih mock-only, tidak ada perubahan Android.
- Source eksplorasi input: /root/.hermes/profiles/girlfriend/design/zerona-composer-options/.
- Tepat lima pilihan: 1 Soft Capsule; 2 Floating Note; 3 Split Send; 4 Edge Dock; 5 Inset Frame. Warna sama, variasi bentuk/struktur berbeda.
- Dua comparison close-up 1100x1232 (light/dark); sepuluh board detail; PDF sepuluh halaman; PDF comparison dua halaman; ZIP galeri/source yang dapat dibuka offline.
- Audit input final: 266 lulus, 20 peringatan disabled, nol gagal pada pemeriksaan yang diuji. Probe state interaktif 35/35 lulus dan audit kontras mencakup lima opsi, tujuh state, dua tema. Parent menjalankan ulang renderer final, verification passed true, issues kosong.
- User memilih opsi 4, Edge Dock, karena terasa lebih scalable. Ini keputusan desain composer, bukan izin implementasi native.
- Arah terpilih: input menyatu di tepi bawah layar dengan pembatas tonal tipis, lebar isi dimaksimalkan, Queue/Steer kontekstual, dan pertumbuhan multiline serta perpindahan keyboard dianimasikan halus tanpa menggeser posisi baca.
- Tradeoff untuk review lanjutan: dock mengurangi kesan mengambang, tetapi pertumbuhan tinggi, safe area, dan keyboard/IME tetap harus diuji di perangkat; scalable adalah alasan/desain yang dituju, bukan hasil uji native.
- Tidak menambahkan attachment/mic/fitur pesan baru; send hanya simulasi. Native keyboard/IME, TalkBack dan kontras semua kondisi belum diuji di perangkat.

## Goal
Refresh the Android app to feel cohesive and current without changing its core workflows. Prefer readable, compact, native Android interactions over decorative effects.

## Verified baseline
Inspection target: /root/zeron-android-painter, branch feat/android-native-transcript, local HEAD 44cb33a4. These are source findings, not screenshots of the installed APK.

- ui/SessionScreen.kt:69-110 owns the chat Scaffold, header and composer integration.
- ui/SessionScreen.kt:121-153 embeds TranscriptListView using AndroidView and reports viewport width. Preserve that rendering and lifecycle contract.
- ui/SessionScreen.kt:154-188 already provides a contextual Latest control. Restyle only; preserve follow-tail behaviour.
- ui/GlassInspired.kt:17-66 already provides opaque glass-inspired surfaces, 20dp corners, sheen, border and shadow. This is refinement, not a missing styling system.
- ui/SessionsScreen.kt:44-101 places version, Demo/Live, offline state and account menu in the header.
- ui/SessionsScreen.kt:119-161 renders sectioned session rows with title, time and dividers.

Paths above are relative to apps/android/app/src/main/java/sh/zeron/zerona/.

Open PR inspection found #25 composer/send, #26 native transcript, and #27 model label stacked on #25. Before implementation, re-check remote heads and dependencies. This draft is not permission to merge, rebase or combine those branches.

## Reference direction
Official reference: Material 3 in Compose, including Material 3 Expressive.
https://developer.android.com/develop/ui/compose/designsystems/material3
Accessibility reference: minimum interactive targets of 48dp; visible icons/pills can be smaller if their targets remain adequately sized and do not overlap.
https://developer.android.com/develop/ui/compose/accessibility/api-defaults

Use these as design references, not an automatic dependency-upgrade requirement. Installed Compose versions have not been audited.

## Options
1. Clean native + subtle glass (recommended): restrained tonal surfaces, compact typography, rounded composer, one accent colour, minimal sheen. Lowest disruption; keeps a recognisable Zerona character.
2. More expressive native: stronger colour grouping and shape hierarchy, selective motion. More personality, but can consume more space and distract from long conversations.
3. Minimal monochrome: mostly flat surfaces, fine separators and strong typography. Maximum focus, but less visual character.

## Proposed first-pass scope
1. Chat header: prioritise session title; keep secondary metadata subdued. Selected-model subtitle must account for the independent model-label work instead of overwriting it. Hide diagnostic information from the primary visual hierarchy without losing access to it.
2. Composer: refine existing opaque rounded panel; consistent padding and icon sizing; send action clear and easy to reach. Preserve draft, sending, queue/steer and failure behaviour. Delivery chips remain contextual while typing.
3. Session list: improve title/time hierarchy and section spacing, simplify header clutter, use consistent selected/pressed states. No new search, filters, session creation or navigation destinations in this refresh.
4. Transcript surroundings only: balance outer margins, header/composer separation and contextual Latest styling. No renderer, text-measurement or scrolling algorithm changes.
5. Apply shared colours, shape and spacing consistently to loading, empty, offline and error presentations. Preserve explicit errors and retry paths that exist; audit missing states before specifying new behaviour.

## Non-goals
- Backend/Rust/UniFFI changes; auth redesign; new messaging features.
- Replacing core-native transcript rendering with a Markdown parser.
- Real backdrop blur, new navigation architecture, extensive animation or broad dependency upgrades.
- Fixing independent scroll/rendering bugs under a cosmetic redesign.

## Numbered implementation insertion points
1. Shared shell tokens: inspect ui/theme/Theme.kt and ui/GlassInspired.kt before finalising colour and typography values.
2. Chat shell: ui/SessionScreen.kt, preserving AndroidView lifecycle and viewport reporting.
3. Composer: inspect ui/ComposerBar.kt plus its state contract before writing a scoped visual diff; this file was not deeply reviewed for this draft.
4. Session overview: ui/SessionsScreen.kt.
5. Palette parity: inspect transcript/Palette.kt against Compose tokens before changing shared backgrounds; do not assume the Canvas renderer inherits MaterialTheme.

## Delivery sequence
1. Choose visual direction and confirm the first screen, ideally with a screenshot of the installed build or a reference app.
2. Produce a low-fidelity before/after layout or mockup for that screen, including light/dark and keyboard-open states. User reviews visual direction before implementation.
3. Re-check PR heads and pick an isolated feature worktree based on the branch that owns the required UI. Record exact base and inherited changes.
4. Implement a narrowly scoped Android visual patch via cursor-me. No local Cargo/Gradle build on this VPS. Review complete diff and preserve existing interactions.
5. Build/test the exact candidate SHA through the fork's Android CI after implementation is requested. Obtain APK and verify on a real Android phone.
6. Broaden the same visual language to the second screen only after the first is accepted.

## Verification / acceptance
- Source review: Android-only expected diff; no backend behaviour changes and no accidental loss of model metadata or composer features.
- Exact-SHA Android app CI succeeds; report compilation separately from device acceptance.
- On-device before/after evidence: session list to chat to back; keyboard open/close; long titles; multiline drafts; sending/queued/failed; offline; loading and empty transcript.
- Exercise Latest, incoming streaming, history-reading position, long-message copy/link actions and image interactions to catch visual regressions. Preserve previously available functionality.
- Light and dark themes remain readable; enlarged system fonts do not clip important labels or overlap controls; TalkBack names remain meaningful; touch targets at least 48dp.
- Opaque composer and action surfaces prevent transcript bleed; no unnecessary full-screen blur or visible scrolling-performance regression.
- Model/host configuration and existing real-message execution requirements remain unchanged.

## Rollback
Keep cosmetic changes isolated in their own commit/PR; revert that patch and reinstall the preceding verified APK if needed. Do not revert unrelated active milestone work.

## Decisions (recorded)
- Visual direction: Calm v1 catalog (not Bold v2). User approved native implementation after mock review.
- Composer shell: **Edge Dock** (option 4) — opaque full-width dock, plain field, trailing send.
- State, density, and motion: shipped per Edge Dock spec (`animateContentSize` ~200ms on composer; Latest chip fade/slide ~200/160ms). Further motion changes are out of scope for this refresh.
- Todo due date remains unspecified; do not invent a date.

## Implementation evidence (Composer 2.5, worktree only)
- Shared tokens: `ZeronaColorTokens.kt`, `TranscriptColorTokens.kt`, `Contrast.kt`, M3 `Theme.kt`; transcript `Palette.kt` consumes shared tokens (action lilac `#5C45A8`, decorative `#7B5FD4` for quote bar only).
- Edge Dock: opaque full-width `SessionComposerBar`, tonal top divider, plain `BasicTextField` (+2dp horizontal inset so focus stroke does not clip glyphs), trailing send (48dp), contextual Queue/Steer; queued items use bounded preview (`MAX_QUEUED_PREVIEW_LINES` = 3) with **Send now** / **Remove** on `FlowRow` inside the same queue row `Column` (wraps at large fonts).
- Sessions list: one `LazyColumn` item per section header and per session (`key = row.id`); per-row `Surface` with first/middle/last corner radii for continuous tonal groups (no eager section `Column`).
- Native chrome: `values` + `values-night` themes/colors; `NativeChrome.kt` for dialogs/menus/selection/image preview tints.
- Motion shipped: `animateContentSize` (~200ms) on composer stack; `AnimatedVisibility` fade/slide (~200/160ms) on Latest. No transcript scroll/inset animation changes.
- VPS pure helpers: `GRADLE_USER_HOME=/root/.gradle python3 scripts/android/test-design-helpers.py --cache-dir /root/.hermes/profiles/girlfriend/cache/scratch/zerona-jvm` — **37** JVM tests (`ZeronaColorTokensTest`, `ContrastTest`, `ComposerPresentationTest`). Contrast regression captured RED then GREEN at `/root/.hermes/profiles/girlfriend/cache/scratch/zerona-final-contrast-{red,green}.log`. Compose compile/device UI unverified here; parent runs Android CI.
