#!/bin/bash
# Build the Rust mobile core (crates/mobile) for Android and generate its
# Kotlin bindings — the same library the iOS app links.
#
#   scripts/android/build-core.sh [out_dir]
#
# Kotlin generation needs only the host toolchain. The .so build needs the
# Android NDK + cargo-ndk (`cargo install cargo-ndk`,
# `rustup target add aarch64-linux-android x86_64-linux-android`); it is
# skipped with a note when they are missing. CI runs this script in
# .github/workflows/android-core.yml (with REQUIRE_SO=1 so the .so build cannot be skipped).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="${1:-$ROOT/target/android-core}"
mkdir -p "$OUT/kotlin" "$OUT/jniLibs"
cd "$ROOT"

cargo build --locked -p zeron-mobile --lib --profile mobile
cargo build --locked -p zeron-mobile --bin uniffi-bindgen --features bindgen --profile mobile
HOST_LIB="$ROOT/target/mobile/libzeron_mobile.$([[ "$(uname)" == Darwin ]] && echo dylib || echo so)"
"$ROOT/target/mobile/uniffi-bindgen" generate --library "$HOST_LIB" --language kotlin \
  --no-format --out-dir "$OUT/kotlin"
python3 "$ROOT/scripts/android/patch-kotlin-bindings.py" "$OUT/kotlin"
echo "kotlin bindings: $OUT/kotlin"

if command -v cargo-ndk >/dev/null && [[ -n "${ANDROID_NDK_HOME:-}" ]]; then
  ANDROID_ABIS="${ANDROID_ABIS:-arm64-v8a x86_64}"
  ndk_targets=()
  for abi in $ANDROID_ABIS; do
    ndk_targets+=(-t "$abi")
  done
  cargo ndk "${ndk_targets[@]}" -o "$OUT/jniLibs" \
    build --locked -p zeron-mobile --lib --profile mobile

  abi_to_triple() {
    case "$1" in
      arm64-v8a) echo aarch64-linux-android ;;
      x86_64) echo x86_64-linux-android ;;
      armeabi-v7a) echo arm-linux-androideabi ;;
      x86) echo i686-linux-android ;;
      *)
        echo "error: unknown Android ABI: $1" >&2
        return 1
        ;;
    esac
  }

  find_libcxx_shared() {
    local triple="$1"
    local matches=()
    while IFS= read -r -d '' path; do
      matches+=("$path")
    done < <(find "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt" -path "*/sysroot/usr/lib/$triple/libc++_shared.so" -type f -print0 2>/dev/null)
    if (( ${#matches[@]} == 0 )); then
      echo "error: libc++_shared.so not found for triple $triple under $ANDROID_NDK_HOME/toolchains/llvm/prebuilt" >&2
      exit 1
    fi
    if (( ${#matches[@]} > 1 )); then
      echo "error: multiple libc++_shared.so for triple $triple:" >&2
      printf '  %s\n' "${matches[@]}" >&2
      exit 1
    fi
    echo "${matches[0]}"
  }

  readelf_cmd() {
    if command -v llvm-readelf >/dev/null 2>&1; then
      echo llvm-readelf
    elif command -v readelf >/dev/null 2>&1; then
      echo readelf
    fi
  }

  for abi in $ANDROID_ABIS; do
    triple="$(abi_to_triple "$abi")"
    src="$(find_libcxx_shared "$triple")"
    dest_dir="$OUT/jniLibs/$abi"
    mkdir -p "$dest_dir"
    cp -p "$src" "$dest_dir/libc++_shared.so"
    echo "copied libc++_shared.so ($triple) -> $dest_dir/libc++_shared.so"

    mobile_so="$dest_dir/libzeron_mobile.so"
    if [[ ! -f "$mobile_so" ]]; then
      echo "error: expected $mobile_so after cargo-ndk build" >&2
      exit 1
    fi

    re="$(readelf_cmd || true)"
    if [[ -n "$re" ]]; then
      if "$re" -d "$mobile_so" 2>/dev/null | grep -q 'Shared library: \[libc++_shared\.so\]'; then
        if [[ ! -f "$dest_dir/libc++_shared.so" ]]; then
          echo "error: $mobile_so needs libc++_shared.so but $dest_dir/libc++_shared.so is missing" >&2
          exit 1
        fi
      fi
    fi
  done

  echo "jniLibs: $OUT/jniLibs (ABIs: $ANDROID_ABIS)"
else
  if [[ "${REQUIRE_SO:-}" == "1" ]]; then
    echo "error: cargo-ndk / ANDROID_NDK_HOME required but missing (REQUIRE_SO=1)" >&2
    exit 1
  fi
  echo "note: cargo-ndk / ANDROID_NDK_HOME not found — skipped the .so build"
fi
