# Plan: Rora PR #45 newer-seen suppression

Request: Rora (authorized). Repo: `pancaprima/zerona`, PR **#45** (draft), head branch **`feat/unread-push-delay`**.

## Reconciliation (2026-10-27)

| Item | Value |
|------|--------|
| PR head SHA (remote) | `da2ffe46ab1fdb9a46a1605d3bf29f06017f1e82` |
| Worktree branch | `fix/pr45-newer-seen` (aligned to PR head before doc commit) |
| Divergence | Local `1bad56cc` was same tree as `da2ffe46` (amended committer metadata only); reset to PR head — safe to proceed |

**Do not merge**; keep PR draft.

## Requirement

For unread triggers with a stored `message_id`, `/seen` suppresses deferred FCM push when any device receipt:

1. Matches the trigger’s `message_id` exactly, **or**
2. Reports a **strictly newer** on-screen message: `message_at >` the trigger’s transcript timestamp captured at enqueue (`message_created_at` via `resolveTriggerMessageCreatedAt`), **never** chat `lastMessageAt` or marker-bumped `msg_at`.

Regression: two `done` triggers in one chat; user sees only the **second** completion → **no push** (first trigger still unread by id/timestamp rules).

Preserve: middle/progress seen does **not** suppress completion push; all prior workerd tests remain green.

## Implementation (already on PR head)

1. **Schema**: `unread_triggers.message_created_at` (migration in `RegistryRoom` constructor).
2. **Enqueue**: `resolveTriggerMessageCreatedAt` — `done` → session `updatedAt`; other categories → same-batch chat `lastMessageAt` or session `updatedAt`. Not updated by `bumpTriggerMarkersFromChat`.
3. **`isTriggerRead`**: with id → `message_id = ? OR message_at > readBaseline` where `readBaseline = message_created_at` (fallback `msg_at` when 0).
4. **`POST /seen`**: delete triggers with id when id matches or `message_created_at <` receipt `messageAt`.

## Tests

- Existing Carmen / Rora coverage unchanged in `edge/test/workerd/unread-push.workerd.test.ts`.
- **New**: `suppresses push when two done triggers exist and only the newer completion was seen`.

## Carmen’s five named scenarios (PR checklist)

| # | Scenario | Test name |
|---|----------|-----------|
| 1 | Read on desktop before 5 min → no push | `does not push when desktop read receipt arrives before the delay` |
| 2 | Read on phone before 5 min → no push | `does not push when phone read receipt arrives before the delay` |
| 3 | Unread on both at due time → one push | `sends exactly one deferred push per chat when still unread on both devices` |
| 4 | Several messages/triggers → one push | `coalesces several triggers into one summary push` |
| 5 | Daily cleanup/backup still runs | `runs the daily backup job when due without dropping deferred triggers` |

**Plus newer-seen scenario (this task):** `suppresses push when two done triggers exist and only the newer completion was seen`.

## Verification commands

```bash
cd edge && npm run test:workerd:unread-push   # 22/22
cd edge && npm run test:unit                 # 156/156
```

CI status: not claimed green until checks run on the pushed commit SHA.

## Allowed change surface

- `edge/src/registry-room.ts`
- `edge/test/workerd/unread-push.workerd.test.ts`
- `docs/plan/plan-rora-pr45-newer-seen.md`
- PR #45 description via `gh`
