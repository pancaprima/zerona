# Android chat study01 (right CHAT screen)

Status: **IMPLEMENTED (opt-in patch)** on branch `feat/android-chat-study01` at base `261d50b48d9f253cf264497d25062bfc3598bfc8`. **Parent CI / device gates remain** (`cargo test -p zeron-mobile`, `scripts/*/build-core.sh`, Gradle unit tests, APK visual).

## Dependency

| Item | Value |
|------|--------|
| Branch | `feat/android-chat-study01` |
| Base SHA | `261d50b4` — `feat(android): style sessions after expressive study 01` |
| Open dependency | PR29 `feat/android-design-refresh` **not merged**; do not assume its composer/theme deltas |
| Prior analysis | `/root/.hermes/profiles/girlfriend/cache/scratch/zerona-chat-study01-analysis.json` (leads only) |

## Visual target

- Reference: `docs/design/zerona-android/chat-study01.png` (copy of study01 **right CHAT**).
- **In scope:** compact header (back + real session title only), lilac user bubble, white rounded assistant **prose** cards with thin border (one card per eligible top-level markdown block row), subdued tool panels, dark aubergine fenced code blocks **outside** prose cards, board-aligned transcript background (`SessionScreen` + `Palette.background` — unchanged near-black dark / aubergine-tinted light board).
- **Out of scope / unchanged:** Sessions list (left phone), Edge Dock composer paradigm (`ComposerBar.kt`), fake model subtitle, overflow menu, PR27 model metadata wiring, scroll/anchor/follow-tail/Latest logic, `SessionViewModel` / `AppModel` / auth / FFI client, global Compose `Theme.kt` / `ZeronaColorTokens` scheme.

## Explicit decision: Android-only layout opt-in (default legacy)

Cross-platform unconditional prose cards were **rejected**. Geometry and `ProseCard` / `ProseCardBorder` paint roles are emitted only when a transcript opts in.

| Surface | Behavior |
|---------|----------|
| **Default** | `AssistantMarkdownStyle::Legacy` — byte/geometry identical to pre-study layout; **no** `ProseCard` roles. |
| **Android session chat** | `TranscriptView::set_assistant_markdown_style(Study01ProseCard)` in `TranscriptListView.bind()` before viewport measure. |
| **iOS / desktop** | No call sites; layout unchanged. `Palette.swift` maps new `ColorRole` cases for compile after bindings regen. |

### Opt-in API and lifecycle

- **Entrypoint:** `TranscriptView::set_assistant_markdown_style(AssistantMarkdownStyle)` (additive; `set_viewport` unchanged).
- **Invalidation:** Style change clears height memo; next pass bumps layout `revision`. Row **`version`** (placement + `RowDisplay` + Android `ModelKey`) XORs a style discriminator so draw caches invalidate without changing row `key` / identity. Retained older `LayoutFrame` arcs still paint with their snapshotted `assistant_markdown`; repeated same-style setter is a no-op.
- **Frame paint:** `LayoutFrame` stores the style used at measure time so `LayoutFrame::display()` matches heights.

### What gets a prose card (bounded by block architecture)

One row = one top-level markdown block (`RowKind::Markdown`), not whole messages and not wrapped lines.

**Card eligible (`block_supports_prose_card`):** `Text`, `Heading`, `Quote`, `List` — **limitation:** multiple blocks in one assistant message ⇒ multiple cards (not one bubble per message).

**Not card-wrapped:** `Code`, `Math`, `Table`, `Rule` — fenced code stays dark panel on board background. Tools, user bubbles, chips, standalone image rows: unchanged.

## Approved renderer scope (option 1 — real layout)

1. **Core:** `place_prose_block` in `crates/mobile/src/layout/rows.rs` when style is `Study01ProseCard` and block is prose-eligible.
2. **Paint roles:** `ColorRole::ProseCard`, `ColorRole::ProseCardBorder` (enum tail in `display.rs`).
3. **Android paint:** `TranscriptColorTokens` scoped white / lifted dark card fills + hairline borders; `transcript/Palette.kt` maps roles only (tool/agent washes in `Palette.kt`).
4. **Compose chrome:** `SessionScreen.kt` — tighter title, transcript host `Palette.background`.

## Files touched (this patch; dirty worktree)

| File | Role |
|------|------|
| `crates/mobile/src/layout/mod.rs` | Opt-in worker msg, `LayoutFrame` style snapshot, style-aware row `version` |
| `crates/mobile/src/layout/display.rs` | `AssistantMarkdownStyle`, `ProseCard` roles |
| `crates/mobile/src/layout/rows.rs` | `place_prose_block`, conditional `place_row` |
| `crates/mobile/src/layout/tests.rs` | Legacy default + study01 regression (version/cache, geometry, streaming, table/tools/copy) |
| `apps/android/.../TranscriptListView.kt` | Opt-in before attach |
| `apps/android/.../TranscriptColorTokens.kt` | Scoped card/border ARGB |
| `apps/android/.../transcript/Palette.kt` | Role mapping |
| `apps/android/.../SessionScreen.kt` | Header + board background |
| `apps/android/.../ContrastTest.kt` | Body/secondary/link AA on card; hairline visibility (not AA body on border) |
| `apps/android/.../TranscriptColorTokensProseCardTest.kt` | Token semantics |
| `apps/ios/Zeron/Design/Palette.swift` | Compile-only `proseCard` / `proseCardBorder` colors |
| `docs/plan/plan-android-chat-study01.md` | This plan |
| `docs/design/zerona-android/chat-study01.png` | Reference asset |

**Not touched:** `SessionsScreen.kt`, `ComposerBar.kt`, `SessionViewModel.kt`, `AppModel.kt`, `Theme.kt`, scroll/anchor internals.

## Tests and CI registration

| Test | Where it runs |
|------|----------------|
| `legacy_default_assistant_rows_emit_no_prose_card_roles` | `cargo test -p zeron-mobile` (`.github/workflows/macos.yml`) |
| `assistant_markdown_row_emits_prose_card_with_padding` | same |
| `assistant_blocks_get_separate_prose_cards_not_user_bubbles` | same (user row with opt-in enabled) |
| `study01_code_block_row_is_not_wrapped_in_prose_card` | same |
| `assistant_prose_card_style_change_remeasures_row_heights` | same |
| `assistant_prose_card_style_change_bumps_display_version_and_retains_legacy_frame` | same (**new**; unexecuted on VPS) |
| `study01_prose_cards_measure_matches_display_at_narrow_width_and_scale` | same (**new**) |
| `study01_prose_card_links_and_inline_images_stay_inside_card_padding` | same (**new**) |
| `study01_streaming_prose_card_reuses_stable_prefix_versions` | same (**new**) |
| `study01_table_and_tool_rows_emit_no_prose_card` | same (**new**) |
| `study01_prose_card_preserves_copy_text_and_code_scroller_geometry` | same (**new**) |
| `proseCardBodyTextPassesAaInBothThemes`, `proseCardSecondaryAndLinkForegroundPassAaOnCardFill`, `proseCardBorderIsVisibleButNotAaBodyTextOnFill` | `:app:testDebugUnitTest` |
| `TranscriptColorTokensProseCardTest` (`proseCardFillContrastsWithBoardBackground`, `proseCardBorderIsScopedHairlineNotOutlineVariant`) | `scripts/android/test-design-helpers.py` + `:app:testDebugUnitTest` |
| `python3 scripts/android/test-design-helpers.py` | Cheap JVM: design tokens, contrast (incl. prose card AA), composer presentation, `TranscriptColorTokensProseCardTest`; needs populated `--cache-dir` (kotlin-stdlib etc.) |

**VPS verification (2026-10-23):** `GRADLE_USER_HOME=/root/.gradle python3 scripts/android/test-design-helpers.py --cache-dir /root/.hermes/profiles/girlfriend/cache/scratch/zerona-jvm` — **42** JUnit methods OK (`ZeronaColorTokensTest`, `ContrastTest`, `TranscriptColorTokensProseCardTest`, `ComposerPresentationTest`). `apps/android/scripts/selfcheck-native-transcript.sh` — OK (**122** `@Test` in **14** classes; `apps/android/README.md` + `plan-android-native-transcript.md` inventory aligned). Rust `cargo test -p zeron-mobile` and Gradle `:app:testDebugUnitTest` **not** run on VPS (parent gates).

## iOS bindings gate

- **Workflow:** `scripts/ios/build-core.sh` runs `uniffi-bindgen` and copies to `apps/ios/Zeron/Core/Generated/zeron_core.swift` (see script header).
- **Current gap:** committed `zeron_core.swift` ends at `ColorRole.diffHunk` — **no** `proseCard` / `proseCardBorder` or `AssistantMarkdownStyle`. `Palette.swift` already switches those cases ⇒ **iOS compile fails until regen** (`scripts/ios/build-core.sh` after core build).
- **Android:** Kotlin UniFFI stubs come from `scripts/android/build-core.sh` on CI/dev machines (not vendored in repo).

## Rollback

1. Revert commits on `feat/android-chat-study01` or reset to `261d50b4`.
2. Rebuild mobile core from pre-change SHA if shipped; enum tail is forward-only when bindings match.

## Remaining parent gates

- `cargo test -p zeron-mobile` (all new layout regressions).
- `scripts/android/build-core.sh` + `gradle -p apps/android :app:testDebugUnitTest`.
- Device/emulator parity vs `chat-study01.png` (white cards on board, code stage, tools).
- Live tail / `ModelKey` cache under style toggle on device.
- Populate JVM cache or use Gradle once, then optional `test-design-helpers.py` for `ContrastTest`.

## Commands parent should run

```bash
git diff --check
cargo test -p zeron-mobile
scripts/android/build-core.sh
scripts/ios/build-core.sh   # if iOS CI included
gradle -p apps/android :app:testDebugUnitTest :app:assembleDebug
GRADLE_USER_HOME=/root/.gradle python3 scripts/android/test-design-helpers.py --cache-dir "$ZERONA_JVM_CACHE"
apps/android/scripts/selfcheck-native-transcript.sh
```
