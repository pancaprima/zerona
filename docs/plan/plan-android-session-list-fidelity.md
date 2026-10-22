# Android session list fidelity (board02 follow-up)

Status: **IMPLEMENTED (native verify pending)** — scoped follow-up to open PR29 on `feat/android-design-refresh` (base `d0b43fbc`). Chat/transcript/composer surfaces are **out of scope** and must remain unchanged.

## Reference (untrusted design data)

| Asset | Path |
|-------|------|
| v1 catalog HTML | `/root/.hermes/profiles/girlfriend/cache/documents/doc_df30c6f2b272_index.html` |
| Catalog CSS | `/root/.hermes/profiles/girlfriend/cache/documents/doc_f21fc2b45e3f_catalog.css` |
| User screenshot (current app) | `/root/.hermes/profiles/girlfriend/cache/images/img_edf7413a0449.jpeg` |

**Production targets:** board02 `sessions-shell-live` phone mock and preview story session phone (`.appbar`, `.sect-h`, `.sess-row`, `.sess-time`, `.sess-row.pressed`). **Do not** ship duplicated `.catalog-state` specimens above the appbar in board02.

## Scope

| Allowed | Forbidden |
|---------|-----------|
| `apps/android/app/src/main/java/sh/zeron/zerona/ui/SessionsScreen.kt` | Chat screen, composer, transcript, shared theme/tokens, AppModel, Rust, Gradle, other UI files |
| This plan; optional copies under `docs/design/zerona-android/session-list/` | Cargo/Gradle builds on VPS; commits/push/CI |

Preserve: real session data, stable keys, section order/grouping, per-row virtualization, `onSessionClick`, demo Transcript lab entry, account menu diagnostics (core version disabled item), Live/Demo/Offline semantics, sign-in/sign-out actions.

## Discrepancies (screenshot + code vs board02)

1. **Tonal rounded groups** — `SessionGroupRow` wraps each section in `surfaceContainer` with 14dp corners; catalog is a **flat** `surface` scroll with only row bottom borders.
2. **App bar** — gradient `ZeronaDecorMark` beside title; catalog is text-only **Zerona** (16px bold) with compact chips + account icon.
3. **Section headers** — mixed-case `titleSmall` in **primary** lilac; catalog `.sect-h` is **11px uppercase**, bold, **on-surface-dim** (`onSurfaceVariant`), light padding.
4. **Session row layout** — title and time **stacked** (time under title, left-aligned); catalog is one **horizontal** row (`space-between`), time **right** at 11px muted.
5. **Press feedback** — plain `clickable` only; catalog `.sess-row.pressed` uses **lilac** (`--primary-selected`) and **12dp** radius (transient press, not selection).
6. **Offline chip** — `surfaceVariant`; catalog offline chip uses **accent/peach** (maps to `secondary`).
7. **Demo chip** — `secondaryContainer`; catalog demo uses **primary-container** palette.
8. **Dividers** — inset `HorizontalDivider`; catalog full-width **outline** border on each `.sess-row`.
9. **Demo lab** — rendered as a normal session row inside a group; catalog **`.lab-row`** is a separate rounded `surface-container` card with title + dim subtitle.

## Implementation plan

1. Remove `SessionGroupRow` and section/card rounding; use `LazyColumn` with catalog-like horizontal padding (12dp) and vertical inset (8dp).
2. Compact app bar: drop decor mark; keep Live/Demo/Offline badges and account menu; core version stays in menu only.
3. `SectionHeader`: uppercase, `labelSmall` + bold, `onSurfaceVariant`, catalog padding.
4. `SessionRowItem`: `Row` with `heightIn(min = 48.dp)`, title `weight(1f)` max 2 lines ellipsized, time `widthIn(min = 48.dp)` end-aligned; full-width bottom divider; press background `primaryContainer` + 12dp clip + default ripple via `MutableInteractionSource` (no persistent selected state).
5. `TranscriptLabEntryRow` (demo only): `.lab-row` styling, same click target semantics.
6. Align Offline/Demo badge colors with catalog using existing `ColorScheme` roles (`secondary`, `primaryContainer`).

## Acceptance

- [x] Session list reads as flat surface: uppercase muted section labels, horizontal title/time rows, subtle full-width dividers.
- [x] Press shows lilac 12dp rounded feedback without stuck highlight.
- [x] Row hit target ≥ 48dp; long titles ellipsize (≤2 lines); timestamp column reserved and right-aligned.
- [x] App bar matches catalog structure (no avatar mark); diagnostics unchanged in account menu.
- [x] Chat and other screens untouched; data/callbacks/virtualization preserved.
- [ ] Native compile/device visual check **pending** (no Gradle on VPS).

## Risks

- **Large system fonts / narrow widths:** two-line titles plus fixed min time width may crowd titles; `weight(1f)` + ellipsize mitigates but extreme fontScale should be watched on device.
- **Uppercasing section headers:** locale-sensitive; uses default locale only for display, not data keys.

## Verification (this change)

- Parent read full diff and ran `git diff --check`: passed; only SessionsScreen.kt and this plan changed.
- Parent executed `GRADLE_USER_HOME=/root/.gradle python3 scripts/android/test-design-helpers.py --cache-dir /root/.hermes/profiles/girlfriend/cache/scratch/zerona-jvm`: 37/37 existing helper tests passed (not layout/Compose interaction tests).
- Parent executed `bash apps/android/scripts/selfcheck-native-transcript.sh`: OK, inventory 117 test methods in 13 classes; static inventory is not execution of those tests.
- Exact `git diff --exit-code HEAD` for SessionScreen.kt, ComposerBar.kt and transcript directory passed: chat implementation unchanged from prior APK.
- Checked boxes above describe source implementation only, not observed device behavior. Exact-SHA Android CI compilation/packaging and native visual/press/font-scale acceptance remain pending.
- Parent owns subsequent commit/push and PR29 CI verification; original no-commit/push restriction applied to Cursor only. No merge/release or login/device changes authorized.
