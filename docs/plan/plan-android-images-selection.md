# Finish Android Markdown images and text selection

Status: implementation in branch; pending parent review, CI (`ios-core` layout regressions + Android workflow), and device acceptance.

## Decisions
- User confirmed Latest works and requested remaining image/selection fixes.
- Keep core-native display-list rendering, compact type, opaque glass-inspired composer and Latest.
- Core `InlineStyle.image` local absolute/pending references produce existing `WidgetKind.Image`; ordinary Markdown links stay links. No URL/file-type guessing, parser replacement, backend jail expansion, or Android direct file/HTTP reads.
- Attachment loader keeps host ownership, encoded/decode caps, lifecycle cancellation and visible error/retry.
- Long press offers Copy message, Select text and Copy link. Select text opens an Activity-scoped native selectable TextView panel with Android handles; this is not direct cross-row Canvas selection. Whole-message text fetched lazily using current frame/messageText keyed by row.
- Core shared layout changes may affect iOS appearance but FFI contract stays unchanged. Source screenshot syntax unknown; support actual Markdown image AST and expose original text/link through actions for diagnostics.

## Scope
- **Approved Rust (narrow):** `crates/mobile/src/layout/markdown.rs` and `tests.rs` only; FFI unchanged. **Out of scope:** `crates/markdown` parser/mend (display mend contract is tested, not modified).
- Android transcript action/menu/view lifecycle wiring and tests, README, Transcript lab Markdown image row.
- No engine/client path permissions, networking, auth, or generated binding modifications.

**Historical:** earlier plan text listed “no Rust changes” / `crates/**` forbidden — that applied before this images phase.

## Verification
- Parent review entire diff, binding signatures and ownership, static Android selfcheck.
- Android workflow: compile/package and JVM tests.
- Existing macOS workflow ios-core job executes zeron-mobile layout regressions when mobile sources change; verify job registration and actual tests in logs.
- Rust: **15** layout regressions in `tests.rs` (local/pending/mixed images, plain links, external/unsafe refs, fenced code, streaming incomplete destinations vs complete widget, narrow/same-line non-overlap, `copy_text` preservation). Count by `fn markdown_` / `embeddable_image` tests in that file.
- Device: thumbnail/real host read/error, menu on body and link, selected substring copy and original link, selection scroll, close/rotate/switch session, Latest and typing-chip regressions.

## Known boundaries
- **Widget emission:** local absolute / `pending://` Markdown image AST only. **Link fallback:** `https`/`http`, `file:`, `data:`, `javascript:`, relative paths, overlong sources, plain `[text](path)` links (including `.png` suffix) — never auto-promoted to widgets.
- External HTTPS image fetching, relative-path cwd resolution and out-of-jail screenshots are not solved by widget emission. Keep explicit visible fallback; do not substitute demo pixels for remote failures.
- Streaming: `IncrementalParser::display_tree` mends hanging `](url` to `zeron:pending-link` — incomplete host paths must not become `WidgetKind.Image` (layout test + `crates/markdown` mend tests). **Risk if mend/parser contract changes without mobile follow-up:** premature or wrong image references in streaming rows.
- **View source / copy:** row `copy_text` is per-block trimmed source from layout (`text_blocks` slice); whole-message menu text may join blocks — do not promise exact original message bytes when blocks normalize.
- Native selection panel offers partial copy, not in-place cross-row Canvas selection.

## Rollback
Revert these changes only; previous ed11bf04/7def0ba8 UI and Latest fixes remain.
