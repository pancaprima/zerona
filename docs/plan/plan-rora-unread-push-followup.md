# Plan: Rora follow-up — unread push message_id + CI (PR #45)

Request source: Rora (grokbot), user-delegated for personal repo `pancaprima/zerona`.

## Identity
- PR: **#45** (draft), branch **`feat/unread-push-delay`**
- Worktree: `/root/zerona`
- Base commit for this follow-up: **`d63f818a1d3abe36e985c3f6e2d7f9f9bbf231ab`** (`fix(ui): resolve seen report build errors`)
- **Do not merge**; local edits only unless Prima commits separately.

## Problem
Deferred FCM unread pushes store `message_id` for read matching. Enqueue used `parseLastMessageId(chatRow)` whenever a chat row existed, including rows loaded from SQLite that were **not** updated in the triggering push batch — so `message_id` could be a stale `lastMessageId` (e.g. `prev-msg`) while the run completion event was `lastCompletedTurn` (`turn-1`). A desktop `/seen` for the old message then incorrectly suppressed the push.

## Fix (edge)
- `resolveTriggerMessageId(category, session, chat, chatUpdatedInBatch)` in `edge/src/registry-room.ts`:
  - **Same batch** (`touched.has('chats …')`): use `lastMessageId` from the chat row merged in that batch (the message event bundled with the trigger).
  - **Otherwise, `done`**: use `lastCompletedTurn` from the session row (the triggering completion id).
  - **Otherwise**: empty `message_id` → alarm uses strict `message_at > msg_at` fallback (input/failed without a chat marker in batch).
- `enqueueUnreadTrigger` calls `resolveTriggerMessageId` instead of gating only on `chatUpdatedInBatch` for id selection while still passing the loaded chat row for `msg_at`.

## Tests (`edge/test/workerd/unread-push.workerd.test.ts`)
- Carmen regression: after `triggerDone` with no chat upsert, assert `message_id === 'turn-1'` even when chat row still has `prev-msg`; later marker bump to `completion-msg`; push still sends when only the old message was seen.
- New case: chat row **never** advances before alarm; `message_id` stays `turn-1`; push still delivers (proves stale chat `lastMessageId` does not drive read matching).
- Helper `triggerMsgId()` for SQL assertions.

## Docs
- `docs/plan/plan-unread-push-delay.md`: decisions §3–§4 describe `msg_at`, `message_id`, and read matching (id match or empty-id timestamp rule).

## CI (d63f818 failures)
- `crates/client/src/live/urls.rs`: unit test `messageAt` JSON assert uses `1_700_000_000_000i64` so `serde_json` does not overflow default integer type on Linux `ios-core`.
- `.github/workflows/macos.yml`: `mkdir -p "$HOME/.cargo/bin"` before extracting `cargo-nextest` (tar `-C` requires the directory).

## Out of scope (this pass)
- Desktop phase 2b read receipts, merge, commit, push, cargo/rust builds on VPS.
- Parallel plan files (`plan-pr45-rora-followup.md`, etc.) left untouched.

## Verification (when CI/local available)
- `cd edge && npm run test:workerd:unread-push`
- macOS workflow: `ios-core` + `macos-native` nextest install step
