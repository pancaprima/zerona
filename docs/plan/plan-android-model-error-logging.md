# Plan: Android session model-label refresh error logging

Status: **implemented** on `fix/android-model-label-error-logging` (stacked on `origin/main` after PR #27 @ `40c454f`).

## Problem
`SessionViewModel.refreshModelLabel()` loads `sessionRow` on a background dispatcher to populate the read-only top-bar subtitle (PR #27). Failures were caught and ignored with an empty `catch (_: Exception)`, so model-load / FFI errors were invisible in logcat with no UX change (subtitle stays absent or stale).

## Goals
- Log failures at warn level for debugging without changing user-visible behavior (no error banner, no crash).
- Do **not** log exception messages, chat ids, model ids, or catalog strings (host `detail` fields may contain sensitive text).

## Design
- **Pure helper:** `formatModelLabelRefreshFailureKind(Throwable)` in `ModelLabelPresentation.kt` — exception type only (`RuntimeException`, nested `Outer.Inner`, expected `CoreException.*` shapes from UniFFI).
- **Call site:** `SessionViewModel.refreshModelLabel()` — `Log.w(SESSION_VIEW_MODEL_LOG_TAG, "Session model label refresh failed (<kind>)")` without passing the throwable as a log cause (avoids printing `Throwable.message`).
- **Preserved:** `CancellationException` rethrown; `client == null` / `isLab` early return; stale-result guard via `handle == null`; no new state fields.

## Allowlist
- `apps/android/app/src/main/java/sh/zeron/zerona/ui/SessionViewModel.kt`
- `apps/android/app/src/main/java/sh/zeron/zerona/ui/ModelLabelPresentation.kt`
- `apps/android/app/src/test/java/sh/zeron/zerona/ui/ModelLabelPresentationTest.kt`
- This document.

## Out of scope
- Surfacing label errors in UI, retries, Rust/FFI changes, broader empty-catch audits elsewhere.

## Verification
| Layer | Command / note |
|--------|----------------|
| Pure JVM | `python3 scripts/android/test-design-helpers.py` (`ModelLabelPresentationTest`, including failure-kind cases) |
| Gradle | `./gradlew :app:testDebugUnitTest` (CI-equivalent; optional on integrator host) |
| Device | Manual: force `sessionRow` failure → expect single warn line in logcat, subtitle unchanged |

## Relation to PR #27
Builds on the read-only model label feature; only observability for the existing refresh path.
