# Plan: PR #45 Rora follow-up (trigger message id)

Request source: Rora (grokbot), personal repo `pancaprima/zerona`.

## Identity
- PR: **#45** (draft), branch **`feat/unread-push-delay`**
- Worktree: `/root/zerona`, reference commit **`14a41c0868e82ca98bffcc1b134bc0af0969b4f3`** (original HEAD when CI was last checked)
- **Do not merge**; keep PR **#45** in **draft**

## User instruction (workflow)
1. On branch **`feat/unread-push-delay`**, **prepare a commit** with the follow-up changes (edge, tests, docs in scope below).
2. **Push** the branch to update PR **#45**.
3. Leave PR **#45** as **draft**; **do not merge**.

## Scope (this follow-up)
1. **Edge** (`registry-room.ts`): `resolveTriggerMessageId` records ids from the session/chat ops in the **triggering** batch (`done` → `lastCompletedTurn`; `input`/`failed` → same-batch chat `lastMessageId`). `isTriggerRead` and `/seen` cleanup use **exact** `message_id` match when set (no `message_at > msg_at` substitute). `bumpTriggerMarkersFromChat` may advance `msg_at` from later chat rows but must not replace `message_id` on `done` triggers (avoids stale `lastMessageId` on an unchanged chat row).
2. **Tests** (`unread-push.workerd.test.ts`): UUID-shaped transcript entry ids; Carmen-style progress-vs-completion id; exact-id suppress; chat row never advances before alarm; stale chat-only update must not clobber done `message_id`.
3. **Docs** (`plan-unread-push-delay.md`): decisions §3–§4 — read matching by message id; remove timestamp-only read when ids differ.

## Out of scope here
- CI / macOS workflow / Rust client changes (handled elsewhere on the branch or in CI; not part of this edge-only pass).
- Untracked sibling plans (`plan-alarm-double-push-test-pr45.md`, `plan-unread-push-delay-read-by-message-id.md`, etc.) unchanged.
- This agent session: **no** code edits beyond plan docs unless separately requested; **no** commit, **no** push (see Status).

## Status
- Implementation and workerd coverage: **done locally** (uncommitted in this worktree at last check).
- **This plan file update** is **not** committed or pushed from this session; **CI not re-run** here.
- Pending per user instruction: commit + push on **`feat/unread-push-delay`** (to be done by Prima or a follow-up session), then leave PR draft.

## Verification (local, already executed)
- `cd edge && npm run test:workerd:unread-push` — **21/21** pass
- Edge unit tests — **156/156** pass

## CI (original HEAD `14a41c08`, separate from local follow-up)
- CI for **`14a41c0868e82ca98bffcc1b134bc0af0969b4f3`** was still **running / failing** at last check (not invalidated or re-run by this doc edit).
- **ios-core**: failed — *Generated Swift bindings are current*
- **macos-native**: in progress at last check
