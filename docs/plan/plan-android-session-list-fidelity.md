# Android session list fidelity (expressive study 01)

Status: **IMPLEMENTED (native verify pending)** — scoped follow-up to open PR29 on `feat/android-design-refresh` (clean HEAD `e082a114`). **LEFT SESSIONS screen only.** Chat/transcript/composer/shared theme are **out of scope** and must remain byte-identical.

## Decision log (provenance)

| Pass | Authority | Outcome |
|------|-----------|---------|
| **board02 flat catalog** (prior) | `sessions-shell-live` / v1 catalog CSS | Flat list, uppercase section labels, horizontal title/time, full-width dividers, compact TopAppBar chips. Implemented at `e082a114` then **superseded** by user approval. |
| **expressive study 01** (current) | User image + “oke gas” | Pale-lavender grouped cards, custom header (“Your sessions.”), brand row + account control, status line from Live/Demo/Offline only — **not** mock “Connected to MacBook”. |

User override: flat board02 reference is **historical only**; do not regress to flat dividers or default TopAppBar for sessions.

## Reference (untrusted except user image)

| Asset | Path |
|-------|------|
| **Authoritative target (sessions left phone)** | `/root/.hermes/profiles/girlfriend/cache/images/img_021d84b76eb9.png` |
| Repo copy (optional) | `docs/design/zerona-android/session-list/study01.png` |
| Expressive study HTML (layout hints) | `/root/.hermes/profiles/girlfriend/cache/scratch/zerona-expressive-mock/index.html` |
| v2 scratch (not target) | `/root/.hermes/profiles/girlfriend/cache/scratch/zerona-expressive-mock-v2/index.html` |
| Prior board02 / catalog (superseded) | v1 catalog HTML/CSS under girlfriend cache `doc_df30c6f2…` / `doc_f21fc2b…` |

## Scope

| Allowed | Forbidden |
|---------|-----------|
| `apps/android/app/src/main/java/sh/zeron/zerona/ui/SessionsScreen.kt` | Chat (`SessionScreen`, `ComposerBar`, transcript, shared theme/tokens), `AppModel`, Rust, Gradle, other UI files |
| This plan; optional `docs/design/zerona-android/session-list/study01.png` | Cargo/Gradle builds on VPS; commits/push/CI; executing uploaded scripts |

**Preserve:** real `state.sections` PROJECT grouping and order, stable row/section keys, per-row `LazyColumn` virtualization (no eager `Column` of all sessions), `onSessionClick`, demo Transcript lab entry, account menu (core version disabled item, sign-in/sign-out), Live/Demo/Offline semantics.

**Do not:** Today/Yesterday/EARLIER labels, fake rows, fake persistent selection, hardcoded host names, new search/create/filter/nav, phone frame or fake system status bar.

## Implementation contract (`SessionsScreen.kt` only)

1. **Screen chrome** — Off-white / calm lilac screen background via `MaterialTheme.colorScheme` (local to this composable tree; no shared theme edits). No `Scaffold` + `TopAppBar`. **Edge-to-edge:** `MainActivity.enableEdgeToEdge()` + `ZeronaApp` `Surface`/`NavHost` do not apply list insets; root `LazyColumn` uses `safeDrawingPadding()` so status bar, nav bar, and display cutouts stay clear without touching chat.
2. **Brand row** — Small decorative mark (`ZeronaDecorMark`) + `ZERONA` (uppercase, bold, letter-spaced) in a `weight(1f)` row so the account control stays on-screen at large font scale. Top-right: soft lilac rounded-square **account** control (person icon, `contentDescription`, ≥48dp touch target, ~40dp visual), same dropdown actions as before.
3. **Hero** — `Your sessions.` ~28–32sp bold; ~16dp vertical rhythm; **status line** with dot + label derived only from `isDemo` / `isOffline` / Live (safe strings: Demo, Offline, Live). ~24dp gap before first section.
4. **Sections** — Real `section.header` as small uppercase muted labels (locale uppercasing for display only).
5. **Groups** — Pale lavender continuous cards (~20dp outer radius), no row dividers. Each virtualized row paints top/middle/bottom (or single) shape so the group reads as one card.
6. **Rows** — Min 48dp; decorative project marker dot (deterministic accent per `section.id`, lilac/peach/mint palette); semibold title (max 2 lines) + right-aligned time (`widthIn` min ~48dp, max ~80dp). Press: transient ~16dp lavender fill + ripple; dark theme uses contrasting press fill (no light selected fill with unreadable text).
7. **Scroll** — Custom header (brand, hero, status) is a single stable `LazyColumn` item (`sessions-list-header`); list body shares one scroll with section/session keys unchanged.
8. **Demo lab** — Separate rounded lavender card above sections (unchanged callbacks).
9. **Colors** — Group fill visibly lavender on light; dark uses `surfaceContainer` / scheme roles. Header/list tokens not pushed into shared theme.

## Acceptance

**Source implementation (this pass)**

- [x] Sessions match study 01 left phone: custom header, grouped lavender cards, dots, no flat dividers.
- [x] Status line uses Live/Demo/Offline only; no invented connection copy.
- [x] PROJECT sections, keys, and per-row virtualization unchanged.
- [x] Account menu, lab entry, sign-in/out, diagnostics item preserved.
- [x] Chat/transcript/composer files unchanged vs `e082a114`.
- [x] Sessions root applies `safeDrawingPadding()` (fix round: Scaffold removal had left content under system bars).
- [x] Header scrolls with list via keyed `sessions-list-header` item (fix round: large font / landscape).
- [x] Brand row `weight(1f)` + time `widthIn` max 80dp (fix round).

**Compiled / device evidence (pending)**

- [ ] Android CI compile and on-device visual, press states, large font scale.
- [ ] Parent commit/push and PR29 CI as applicable.

## Risks

- **Large font scale:** two-line titles + reserved time width may crowd; title `weight(1f)`, time min/max band, brand row `weight(1f)`, and full-screen scroll (header in list) mitigate; very long localized time strings ellipsize within 80dp.
- **Locale uppercasing:** display-only on section headers; keys unchanged.
- **Press vs group fill:** dark mode must keep readable `onSurface` on pressed rows.
- **Edge-to-edge:** only sessions list applies `safeDrawingPadding()`; chat route still relies on parent chrome — intentional scope split.
- **Header in LazyColumn:** account `DropdownMenu` anchor remains in header item; recomposition when menu opens is unchanged from prior header placement.

## Verification (agent pass)

- `GRADLE_USER_HOME=/root/.gradle python3 scripts/android/test-design-helpers.py --cache-dir /root/.hermes/profiles/girlfriend/cache/scratch/zerona-jvm`: **37/37** helper tests passed (not Compose layout).
- `git diff --exit-code e082a114 -- SessionScreen.kt ComposerBar.kt apps/.../transcript/`: exit 0 (chat path unchanged).
- **Fix round (insets + scroll):** `grep enableEdgeToEdge MainActivity.kt` confirms edge-to-edge; no `safeDrawing`/`Scaffold` in chat `SessionScreen.kt`; sessions root `LazyColumn` + `safeDrawingPadding()` only in `SessionsScreen.kt`.
- `git diff --check`: passed (fix round).
- Changed files: `SessionsScreen.kt`, this plan, `docs/design/zerona-android/session-list/study01.png` (reference copy).
- Checked boxes above distinguish **source** from **device/CI** evidence.
