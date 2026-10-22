#!/usr/bin/env bash
# Static checks for native transcript Kotlin (no Gradle / no device).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MAIN="$ROOT/app/src/main/java/sh/zeron/zerona"
TEST="$ROOT/app/src/test/java"
fail() {
  echo "selfcheck-native-transcript: $*" >&2
  exit 1
}

test_count="$(grep -rh '@Test' "$TEST" --include='*.kt' | wc -l | tr -d ' ')"
class_count="$(find "$TEST" -name '*Test.kt' | wc -l | tr -d ' ')"
echo "JVM tests: ${test_count} @Test methods in ${class_count} classes"

doc_readme="$ROOT/README.md"
doc_plan="$ROOT/../../docs/plan/plan-android-native-transcript.md"
for doc in "$doc_readme" "$doc_plan"; do
  if [[ -f "$doc" ]]; then
    if ! grep -q "${test_count}.*@Test\|@Test.*${test_count}\|${test_count} JVM" "$doc" 2>/dev/null; then
      if ! grep -q "${test_count}" "$doc"; then
        fail "doc $(basename "$doc") should mention test count ${test_count}"
      fi
    fi
  fi
done

# Pure helpers must not reference android.* (JVM unit tests run without Robolectric).
pure="$MAIN/transcript/ImageAttachmentSupport.kt"
if grep -q 'import android\.' "$pure" || grep -q 'android\.graphics' "$pure"; then
  fail "$pure must stay Android-free for JVM tests"
fi
if ! grep -q 'data class ViewportClip' "$pure"; then
  fail "ViewportClip data class missing"
fi

# TranscriptActions implementors: required overrides present.
vm="$MAIN/ui/SessionViewModel.kt"
for sym in toggleRow toggleDetail openUrl imageLoader showImagePreview; do
  grep -q "override fun $sym" "$vm" || fail "SessionViewModel missing override fun $sym"
done

# Session-owned preview (no static ImageViewerDialog object).
if grep -q 'object ImageViewerDialog' "$MAIN/transcript/ImageViewerDialog.kt"; then
  fail "ImageViewerDialog must not be a static object"
fi
if ! grep -q 'class ImagePreviewController' "$MAIN/transcript/ImageViewerDialog.kt"; then
  fail "ImagePreviewController missing"
fi
loader="$MAIN/transcript/SessionImageLoader.kt"
grep -q 'previewController' "$loader" || fail "SessionImageLoader must own previewController"
grep -q 'ImagePreviewController' "$loader" || fail "SessionImageLoader must construct ImagePreviewController"

# RowView delegates preview without allowLabFixture pre-check.
row="$MAIN/transcript/RowView.kt"
if grep -q 'allowLabFixture = false' "$row"; then
  fail "RowView must not prevalidate preview refs with allowLabFixture=false"
fi
grep -q 'showImagePreview(context, previewRef' "$row" || fail "RowView must delegate preview to TranscriptActions"

# Clip helper wired in list view.
grep -q 'clip\.left' "$MAIN/transcript/TranscriptListView.kt" || fail "TranscriptListView must use ViewportClip fields"

echo "selfcheck-native-transcript: OK"
