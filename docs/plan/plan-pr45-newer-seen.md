# Plan: PR #45 newer-seen read matching

Branch: `fix/pr45-newer-seen` → push to PR #45 head `feat/unread-push-delay` at `e03f57430ba341077c08452ea24d7f18477f8b2a` baseline.

## Goal
For triggers with `message_id`, treat as read when any device `/seen` receipt has the same id **or** a strictly newer message (`message_at` > trigger message createdAt). The comparison baseline is the triggering transcript message’s timestamp (`message_created_at` on enqueue), **not** chat `lastMessageAt` / bumped `msg_at`.

## Changes
1. **`unread_triggers.message_created_at`**: set at enqueue via `resolveTriggerMessageCreatedAt` (`done` → session `updatedAt`; `input`/`failed` → same-batch chat `lastMessageAt` or session `updatedAt`). Never updated by `bumpTriggerMarkersFromChat`.
2. **`isTriggerRead`**: with id → `message_id` match OR `message_at > message_created_at` (fallback baseline `msg_at` when column is 0).
3. **`/seen` cleanup**: drop triggers with id when id matches or `message_created_at <` receipt `messageAt`.
4. **Test**: two `done` triggers in one chat; seen only on the second completion → no FCM push.
5. **PR body**: Carmen’s five checklist cases + new two-trigger case.

## Verification
- `cd edge && npm run test:workerd:unread-push`
- `cd edge && npm run test:unit`
