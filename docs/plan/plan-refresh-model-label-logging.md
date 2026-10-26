# Plan: `refreshModelLabel` error logging

Status: **implemented** on `fix/refresh-model-label-logging` (based on `origin/main`).

## Scope

Carmen review (read-only session model subtitle, PR #27 / `plan-android-model-label.md`): `SessionViewModel.refreshModelLabel()` loads `sessionRow` on `Dispatchers.Default`. Non-cancellation failures were caught with an empty `catch (_: Exception)`, so model-load / FFI errors never appeared in logcat while the UI stayed unchanged (subtitle missing or stale).

**In scope**

- Warn-level logcat when `sessionRow` / label formatting fails.
- Pure JVM helper for a safe log line (exception type only).
- Unit tests for the helper.

**Out of scope**

- UI error surfaces, retries, Rust/UniFFI changes, other empty-catch audits.

**Allowlist**

- `apps/android/app/src/main/java/sh/zeron/zerona/ui/SessionViewModel.kt`
- `apps/android/app/src/main/java/sh/zeron/zerona/ui/ModelLabelPresentation.kt`
- `apps/android/app/src/test/java/sh/zeron/zerona/ui/ModelLabelPresentationTest.kt`
- This document.

## Implementation

- **`formatModelLabelRefreshFailureKind(Throwable)`** in `ModelLabelPresentation.kt` — returns simple class name, or `Enclosing.Inner` for nested types; never reads `message` or session/model fields.
- **`refreshModelLabel()`** — on failure after rethrowing `CancellationException`, `Log.w(SESSION_VIEW_MODEL_LOG_TAG, "Session model label refresh failed (<kind>)")` without passing the throwable as the log cause (avoids printing `Throwable.message`, which may include host `detail` text).
- **Unchanged behavior:** `isLab` / `client == null` early return; cancel prior refresh job; stale guard `handle == null`; no new state fields.

## Tests

| Layer | Command | Expectation |
|--------|---------|-------------|
| Pure JVM | `python3 scripts/android/test-design-helpers.py` | `ModelLabelPresentationTest` passes, including failure-kind cases |
| Gradle (optional / CI) | `cd apps/android && ./gradlew :app:testDebugUnitTest` | Same tests via Android unit test task when Rust core artifacts exist |

## Risks

- **Privacy:** Logging full exception messages or chat ids could leak host or config text; mitigated by type-only message and no throwable attachment.
- **Noise:** Transient FFI failures may produce warn lines without user-visible impact; acceptable for a best-effort subtitle path.
- **Stale subtitle:** Logging does not fix missing labels after failure; operators use logcat to diagnose only.
