# Plan: on-demand mid-turn steering for Hermes ("Steer now")

## Problem
Hermes advertises no `_session/steering` ACP extension, so Zerona marks it
`TurnBoundary`: a message typed mid-turn waits in the queue and is sent as the
next prompt. Hermes itself does support live steering through the `/steer <text>`
slash command (`agent.steer()`, text lands as a user row after the current tool
batch, no interrupt).

## Verified on the wire (2026-10-12, real hermes-acp, scratch probe)
While `session/prompt` #1 was mid-tool (`sleep 20`), a second `session/prompt`
with text `/steer <guidance>`:
- returned `{"stopReason":"end_turn"}` immediately (prompt #1 kept running);
- emitted one `agent_message_chunk` with text `⏩ Steer queued for the active turn: <preview>`;
- the model acted on the guidance after the tool batch (file created, mentioned in final reply).
Idle or absent turn: server runs the text as a plain prompt (no steer), or answers
`No active turn - queued for the next turn.`

## UX (what the user wants)
- Typing while Hermes works behaves like today: the message goes to the visible
  queue and is sent at the next turn boundary (no behavior change).
- Each queued TEXT row for Hermes shows a "Steer now" button (tooltip:
  "Steer now (keeps current work running)"). Clicking it injects the row into the
  live turn via `/steer`, instead of waiting.
- Rows with attachments keep the existing "Send now (interrupt)" action
  (`/steer` is text-only).
- Other harnesses unchanged.

## Design
1. proto: add `on_demand_steer: bool` (serde default false, camelCase) to the harness
   descriptor next to `supports_steering`/`steering_mode`. Hermes keeps
   `SteeringMode::TurnBoundary` so queue/defer logic stays as is.
2. harness: `SteerMessage` gets `immediate: bool` (default false; `text()` keeps false).
   `AcpHarnessSpec` gets `slash_steer: bool` (Hermes true; others false).
   In the ACP run loop, an `immediate` steer on a `slash_steer` agent while `turn.is_some()`:
   - send `session/prompt` with text `/steer <text>` as a SIDE request (do not replace `turn`);
   - its `end_turn` response is NOT a turn Done; swallow it;
   - drop the `⏩ Steer queued...` / `No active turn...` agent_message_chunk so it never reaches the transcript;
   - on success emit the same `Steered` boundary the extension path emits;
   - on error/"No active turn": fall back to `queued_steers` (turn-boundary delivery).
   Non-immediate steers keep current turn-boundary behavior.
3. engine: `steer_queued_now` — when the chat's live run is a `on_demand_steer` harness
   with a turn in flight and the row has no attachments, take the row, write the user
   message, and call `steer_at` with `immediate=true` (new parameter), instead of the
   "send next" reorder. Idle turn: falls through to NextTurn as today.
   Registry: Hermes descriptor (static + `AcpHarness::hermes()` mirror, keep them identical) `on_demand_steer: true`.
4. ui: `QueuePrimaryAction` gets `SteerNow`. `available_queue_primary_action` returns it
   for text rows when the selected harness has `on_demand_steer`; tooltip/label as above.
   Click still calls `STEER_QUEUED_MESSAGE_NOW` (no new RPC), the engine decides.
   Mobile/other clients: unknown field defaults false, no change.

## Risks
- Race: if the turn ends right as `/steer` is sent, Hermes runs the text as a normal
  prompt (untracked turn) or drops a steer that lands after the final assistant message
  (`pending_steer` leftover is not re-queued by the ACP adapter). Mitigation: only send
  while `turn.is_some()`; document, do not over-engineer.
- Steered boundary is emitted on ack, not on real injection (Hermes gives no signal).

## Verification
- Unit/mock tests in `crates/harness/tests/acp.rs` (mock ACP agent that mimics the
  `/steer` ack + chunk): side request does not end the turn, ack chunk filtered,
  fallback on "No active turn". Engine/UI unit tests for the action resolution.
- VPS cannot do a full build; run a single `cargo check -p zeron-harness` in scratch
  target dir only if disk allows, otherwise CI on the fork is the compile check
  (macOS runner catches UI compile errors).
- Live: `cargo run -p zeron-harness --example steering_probe -- hermes` on a real build.
