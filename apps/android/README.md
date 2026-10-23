# Zerona for Android

Kotlin + Jetpack Compose shell around the same Rust mobile core as iOS (`crates/mobile`).
The session screen uses a **native transcript painter** (`LayoutFrame.display` / `RowDisplay`)
hosted in `AndroidView`, with M3b composer send/queue/steer and demo or live sign-in.

## CI build

The **Android app** workflow builds `libzeron_mobile.so`, UniFFI Kotlin bindings, copies
Geist fonts from `crates/ui/assets/fonts`, and runs Gradle `assembleDebug`. Download the
`zerona-android-debug` artifact from Actions.

## Sideload

```sh
adb install -r app-debug.apk
```

## Local Gradle (after `scripts/android/build-core.sh`)

```sh
gradle -p apps/android :app:assembleDebug \
  -PzeronCoreDir=../../target/android-core \
  -PzeronAbis=arm64-v8a
```

## Unit tests (JVM)

```sh
gradle -p apps/android :app:testDebugUnitTest \
  -PzeronCoreDir=../../target/android-core
```

Pure helpers (UTF-16 slice/`isValidRange`, per-scalar advance aggregation, link hit-test, anchor/follow, cache keys, streaming prefixes, viewport scale, image reference/decode planning, message action/copy/link menu models, design tokens/contrast/composer presentation) live under `app/src/test` (117 JVM `@Test` method annotations in 13 classes — static inventory counted by `selfcheck-native-transcript.sh`, not executed native/device tests). `assembleDebug` also runs `testDebugUnitTest` (see `app/build.gradle.kts`). Static wiring checks: `apps/android/scripts/selfcheck-native-transcript.sh` (no Gradle).

### Design refresh helpers (no Gradle)

Calm v1 tokens (`ui/theme/ZeronaColorTokens.kt`, `Contrast.kt`), composer policy (`ui/ComposerPresentation.kt`), and matching unit tests compile on the VPS without the Android SDK:

```sh
python3 scripts/android/test-design-helpers.py
```

Optional: `--cache-dir` (or `ZERONA_JVM_CACHE`, default `~/.cache/zerona-jvm`), `GRADLE_USER_HOME` (default `~/.gradle`) for the Kotlin compiler glob, `--compiler-classpath`, `--trove-classpath`, `--stdlib`, `--annotations`, `--junit-classpath`. `PaletteBackgroundTest` and full Compose UI still require `:app:testDebugUnitTest` on CI.

## Manual transcript checks (device)

1. **Demo → Transcript lab** — lazy `layoutFixtureMarkdown()` via `setDebugEntries` (widgets from laid-out fixture only). Includes a **user attachment list** demo row, a **Markdown `![]()`** assistant row (`LAB_DEMO_IMAGE_REFERENCE`, lab gradient — not `readAttachment`), and a plain `[.png](…)` link on that row for Copy link (links are not promoted to image widgets). Use top-bar **Replay streaming** for scripted fixture prefixes (`a-stream`); composer locked; live host tool streams need a real session.
2. **Demo session** — open any demo chat; scroll a long reply; confirm no duplicate full messages per row.
3. **Font scale** — system display size 85% / 130%; relayout matches paint.
4. **Code block** — horizontal scroll inside row; copy button copies payload.
5. **Links & images** — tap http/https open in browser; `javascript:` ignored. Core emits **image widgets** only for Markdown `![]()` with local absolute or `pending://` references (not `https`, relatives, or plain `[label](path)` links). Long-press for **Copy message**, **Select text** (Activity-scoped selectable `TextView` with handles — not cross-row Canvas selection), **Copy link**, and **View source** when whole-message text differs from the visible block and row `copyText` differs from painted text (`messageText` / per-row `copyText` slices — not guaranteed full original bytes).
6. **Tools** — disclosure, toggle, working/spinner rows in **Transcript lab** (fixture markdown) or a **live session** with tool rows (not every demo chat).
7. **Composer** — send, queue/steer chips while typing during a turn, rotation, switch session.
8. **Scroll-to-latest** — long transcript scrolled far up shows bottom **Latest** control; tap jumps to bottom; new messages while reading history stay put; after jump, streaming follows tail; keyboard open/close and rotation; short transcript hides control.

Visual golden comparison is not automated on Android CI yet (`assembleDebug` only).

## Known gaps

- Markdown `![]()` with remote/relative/`file:` sources stay links (by design). Device verification pending for host `readAttachment` thumbnails and lab Markdown image row. Agent `Visual`: placeholder; `setVisualSource` wired for future scripts.
- Real host attachment thumbnails: manual device test pending (signed-in session).
- Math glyphs on API &lt; 31: labeled placeholder (real glyphs need API 31+ `drawGlyphs`).
- Tool rail / shimmer simplified vs iOS.
