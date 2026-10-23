# Android chat transcript horizontal gutter (PR30)

## User-reported symptom

Screenshot evidence (`img_1149c3217440.jpeg`): transcript prose sits noticeably farther from the left edge than the composer “Message” placeholder, while heading grouping and composer vertical alignment are otherwise acceptable. Header back/title use intentional Material spacing and are not expected to share one global left edge with message text.

## Hypothesis (duplicate outer gutter)

At `@ f68a667f`, `SessionScreen` wrapped the native `AndroidView` transcript in `BoxWithConstraints` with **Compose `padding(horizontal = 16.dp)`**, then passed `maxWidth` as the viewport width to `viewModel.setViewport` and `host.layoutWidthDp`.

Core `place_row` already applies `MARGIN_X = 18` dp on each side (`crates/mobile/src/layout/rows.rs`). That stacks an extra Compose outer inset on top of the core margin:

| Layer | Transcript text left (approx @ scale 1) | Composer text left (unchanged) |
|-------|-------------------------------------------|--------------------------------|
| Before fix | 16 (Compose) + 18 (core) + 16 (card inner) ≈ **50** | 14 (bar) + 4 (field) ≈ **18** |
| After fix | 18 (core) + 16 (card inner) ≈ **34** | 14 + 4 ≈ **18** |

The mismatch is **duplicate outer gutter** (Compose 16 + core 18), not a per-row seam inside native layout. No change to core constants, drawing translation, or composer geometry.

## Width contract (after fix)

- **Viewport width** = measured transcript host width in logical dp: `BoxWithConstraints.maxWidth` inside scaffold content padding only (no extra horizontal Compose inset on the transcript).
- **`viewModel.setViewport(viewportWidthDp, textScale)`** and **`host.layoutWidthDp`** both use that same value in `LaunchedEffect` / `AndroidView` factory and `update`.
- **`textScale`** = `TranscriptViewportScale.viewportTextScale(configuration.fontScale)`; density remains on the native view (`TranscriptListView` px layout), not double-applied in Compose.

## Fix (SessionScreen.kt only)

Remove redundant `.padding(horizontal = 16.dp)` from the transcript `BoxWithConstraints` so core owns the single outer horizontal margin.

## Preserved (unchanged in this PR)

- Native clip (`clipToBounds` on host, row clipping in `TranscriptListView` / `RowView`).
- Scaffold safe-area `padding(padding)` on the column.
- Opaque composer / insets (`SessionComposerBar`, `ComposerBar.kt`).
- “Latest” overlay, heading groups, link/copy selection hit testing.
- `SessionsScreen`, composer vertical alignment, header native spacing.

## Verification

| Check | Status |
|-------|--------|
| `git diff --check` | Run on PR branch |
| `GRADLE_USER_HOME=/root/.gradle python3 scripts/android/test-design-helpers.py --cache-dir /root/.hermes/profiles/girlfriend/cache/scratch/zerona-jvm` | Run on PR branch |
| On-device / native pixel gutter vs composer | **Pending** — design helpers + layout math only; no emulator run in this change |

## If evidence contradicts hypothesis

If a future native capture shows ~18 dp transcript text inset with outer card flush to screen edge, investigate per-row placement or card segment seams in core/native rather than re-adding Compose horizontal padding.
