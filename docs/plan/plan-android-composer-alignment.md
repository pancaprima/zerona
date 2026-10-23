# Android composer draft ↔ send vertical alignment

## Problem (screenshot `img_6235b1b453c6.jpeg`)
Placeholder "Message" sits high in the draft area while the 48dp send control is optically lower. Row uses `Alignment.Bottom`; draft used asymmetric `padding(vertical = 6.dp)` outside the text decoration with no min height tied to `SEND_TOUCH_TARGET_DP`.

## Approach
1. **Match min touch height**: `defaultMinSize(minHeight = SEND_TOUCH_TARGET_DP)` on the draft decoration shell (inside focus border), not a fixed `height`.
2. **Symmetric vertical inset**: Derive `verticalPad = max(0, (48dp − lineHeight) / 2)` from `fieldTextStyle.lineHeight` and `LocalDensity` so single-line placeholder and typed text center with the send button at any `fontScale`.
3. **Modifier order**: `weight` → focus `border` (outer) → `BasicTextField`; padding lives in `decorationBox` so border wraps the full min-height field and focus ring does not clip text.
4. **Multiline**: `decorationBox` uses `TopStart`; min height only applies when short; row stays `Bottom` so send pins to the last line as the draft grows up to `MAX_DRAFT_LINES`.
5. **Demo locked**: Unchanged from prior UI — disabled `OutlinedTextField` only (no send in branch; send-button alignment N/A). `OutlinedTextField` public API on compose-bom 2024.12.01 does not take `contentPadding`.

## Out of scope
Rust/mobile plan, callbacks, queue/state, gradle/CI/device verification.

## Verification (pending device)
- Empty single-line placeholder aligned with send.
- Typed single line centered; multiline growth; IME + `fontScale` > 1; focused border without clip.
