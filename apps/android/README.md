# Zerona for Android (M1)

Kotlin + Jetpack Compose shell around the same Rust mobile core as iOS (`crates/mobile`).
M1 runs **demo mode only**: offline workspace, session list, and a simple transcript
preview (row text from the layout engine). Full display-list painting is M2.

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

## M1 limitations

- Demo credentials only (no sign-in / live sync).
- Transcript is plain text per layout row, not the Rust painter.
- No composer send, voice, or push.
