# Chat transcript UI polish

## Goal

Improve the desktop chat transcript experience: quieter send feedback on the user bubble, motion for new messages, an indeterminate replay loading bar under the titlebar, and an icon-only jump-to-latest control—without changing transcript data flow or commit semantics.

## Scope

| Requirement | Implementation |
|-------------|----------------|
| Sending status beside user bubble | `Transcript::render_pending_send_icon` + `pending_send_bridge_active`; suppress working-trailer copy during pure send bridge |
| Assistant entrance | `motion::assistant_message_entrance_paint` via `apply_message_row_entrance` on markdown rows |
| User entrance | `motion::user_message_entrance_paint`; honors `reduce_motion` |
| Replay loading bar | `loaders::transcript_loading_bar` in `shell.rs` when `Transcript::replay_loading` |
| Jump to latest | Icon-only `jump_pill` in `shell.rs` (stacked `ALT_ARROW_DOWN`), `aria-label` + tooltip |

Out of scope: composer status strip, mobile clients, new icon assets, transcript engine/RPC changes.

## Insertion points

- `crates/ui/src/motion.rs` — entrance paint helpers and unit tests
- `crates/ui/src/loaders.rs` — `transcript_loading_bar`
- `crates/ui/src/transcript.rs` — `row_entrances` bookkeeping, row paint, send icon, `replay_loading`
- `crates/ui/src/shell.rs` — loading bar overlay, jump pill
- `crates/ui/src/rail.rs` — unchanged (rail only)

## Verification

```bash
cargo test -p zeron-ui --lib message_entrances_start_offset_and_settle_at_rest
cargo test -p zeron-ui --lib message_row_entrance_snaps_when_reduced
cargo test -p zeron-ui --lib transcript_load_bar_never_fills_the_track
git diff --check
```

Manual (desktop): send a message, receive a reply, switch chats during replay, toggle reduced motion, and scroll up to verify the icon-only jump control. Device feel remains unverified until exercised in the app.

## Rollback

Restore the four changed Rust source files with `git restore crates/ui/src/loaders.rs crates/ui/src/motion.rs crates/ui/src/transcript.rs crates/ui/src/shell.rs`, and remove this plan file if desired. No migrations or config flags.
