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

Pure helpers (UTF-16 slice/`isValidRange`, per-scalar advance aggregation, link hit-test, anchor/follow, cache keys, streaming prefixes) live under `app/src/test` (18 JVM tests in 6 classes). `assembleDebug` also runs `testDebugUnitTest` (see `app/build.gradle.kts`).

## Manual transcript checks (device)

1. **Demo → Transcript lab** — lazy `layoutFixtureMarkdown()` via `setDebugEntries` (widgets from laid-out fixture only). Use top-bar **Replay streaming** for scripted fixture prefixes (`a-stream`); composer locked; live host tool streams need a real session.
2. **Demo session** — open any demo chat; scroll a long reply; confirm no duplicate full messages per row.
3. **Font scale** — system display size 85% / 130%; relayout matches paint.
4. **Code block** — horizontal scroll inside row; copy button copies payload.
5. **Links** — http/https open in browser; `javascript:` ignored.
6. **Tools** — disclosure, toggle, working/spinner rows in **Transcript lab** (fixture markdown) or a **live session** with tool rows (not every demo chat).
7. **Composer** — send, queue/steer chips while typing during a turn, rotation, switch session.

Visual golden comparison is not automated on Android CI yet (`assembleDebug` only).

## Known gaps

- Agent `Visual` and remote images: placeholder only.
- Math glyphs on API &lt; 31: labeled placeholder (real glyphs need API 31+ `drawGlyphs`).
- Tool rail / shimmer simplified vs iOS.
