#!/usr/bin/env python3
"""Post-process UniFFI Kotlin bindings for Android (stdlib only)."""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

RUST_CLOSE_METHOD = re.compile(
    r"override fun `close`\(\)\s*=\s*\n"
    r"\s*callWithHandle \{\s*\n"
    r"\s*uniffiRustCall\(\) \{ _status ->\s*\n"
    r"\s*UniffiLib\.uniffi_zeron_mobile_fn_method_transcriptview_close\(",
    re.MULTILINE,
)

RUST_CLOSE_METHOD_PATCHED = re.compile(
    r"override fun `closeView`\(\)\s*=\s*\n"
    r"\s*callWithHandle \{\s*\n"
    r"\s*uniffiRustCall\(\) \{ _status ->\s*\n"
    r"\s*UniffiLib\.uniffi_zeron_mobile_fn_method_transcriptview_close\(",
    re.MULTILINE,
)

INTERFACE_CLOSE_DECL = "fun `close`()"
INTERFACE_CLOSE_VIEW_DECL = "fun `closeView`()"

CORE_EXCEPTION_START = "sealed class CoreException"
CORE_EXCEPTION_CONVERTER = "public object FfiConverterTypeCoreError"


def _extract_block(text: str, start_marker: str, end_marker: str) -> tuple[str, int, int]:
    start = text.find(start_marker)
    if start < 0:
        raise ValueError(f"expected marker not found: {start_marker!r}")
    end = text.find(end_marker, start + len(start_marker))
    if end < 0:
        raise ValueError(f"expected end marker not found after {start_marker!r}: {end_marker!r}")
    return text[start:end], start, end


def patch_transcript_view_interface(iface_block: str) -> tuple[str, int]:
    if INTERFACE_CLOSE_VIEW_DECL in iface_block:
        if INTERFACE_CLOSE_DECL in iface_block:
            raise ValueError(
                "TranscriptViewInterface has both `close` and `closeView`; binding shape unexpected"
            )
        return iface_block, 0
    if INTERFACE_CLOSE_DECL not in iface_block:
        raise ValueError(
            "TranscriptViewInterface: expected Rust method declaration "
            f"{INTERFACE_CLOSE_DECL!r} not found"
        )
    return iface_block.replace(INTERFACE_CLOSE_DECL, INTERFACE_CLOSE_VIEW_DECL, 1), 1


def patch_transcript_view_class(class_block: str) -> tuple[str, int]:
    if RUST_CLOSE_METHOD_PATCHED.search(class_block):
        if RUST_CLOSE_METHOD.search(class_block):
            raise ValueError(
                "TranscriptView class has both `close` and `closeView` Rust method overrides"
            )
        return class_block, 0
    match = RUST_CLOSE_METHOD.search(class_block)
    if not match:
        raise ValueError(
            "TranscriptView class: expected Rust close override calling "
            "uniffi_zeron_mobile_fn_method_transcriptview_close not found"
        )
    patched = (
        class_block[: match.start()]
        + class_block[match.start() : match.end()].replace(
            "override fun `close`()", "override fun `closeView`()", 1
        )
        + class_block[match.end() :]
    )
    return patched, 1


def patch_core_exception(exception_block: str) -> tuple[str, int]:
    count = 0
    if "val `message`: kotlin.String" in exception_block:
        exception_block, n = re.subn(
            r"val `message`: kotlin\.String",
            "val `detail`: kotlin.String",
            exception_block,
        )
        count += n
    if "get() = \"message=${ `message` }\"" in exception_block:
        exception_block, n = re.subn(
            r'get\(\) = "message=\$\{ `message` \}"',
            'get() = "message=${ `detail` }"',
            exception_block,
        )
        count += n
    if "val `message`: kotlin.String" in exception_block:
        raise ValueError(
            "CoreException: still contains val `message` after patch (unexpected variant shape)"
        )
    if count == 0 and "val `detail`: kotlin.String" not in exception_block:
        raise ValueError(
            "CoreException: expected val `message` or already-patched val `detail` not found"
        )
    return exception_block, count


def patch_core_error_converter(converter_block: str) -> tuple[str, int]:
    needle = "value.`message`"
    if needle not in converter_block:
        if "value.`detail`" in converter_block:
            return converter_block, 0
        raise ValueError(
            "FfiConverterTypeCoreError: expected value.`message` references not found"
        )
    return converter_block.replace(needle, "value.`detail`"), converter_block.count(needle)


def patch_file(path: Path) -> dict[str, int]:
    text = path.read_text(encoding="utf-8")
    counts: dict[str, int] = {
        "transcript_view_interface_close": 0,
        "transcript_view_class_close": 0,
        "core_exception_message": 0,
        "core_error_converter_message": 0,
    }

    iface_block, iface_start, iface_end = _extract_block(
        text,
        "public interface TranscriptViewInterface",
        "\n/**\n * One transcript's layout engine.\n */\nopen class TranscriptView",
    )
    iface_block, n = patch_transcript_view_interface(iface_block)
    counts["transcript_view_interface_close"] = n

    class_block, class_start, class_end = _extract_block(
        text,
        "open class TranscriptView",
        "\n\n/**\n * @suppress\n */\npublic object FfiConverterTypeTranscriptView",
    )
    class_block, n = patch_transcript_view_class(class_block)
    counts["transcript_view_class_close"] = n

    exc_block, exc_start, exc_end = _extract_block(
        text,
        CORE_EXCEPTION_START,
        f"\n\n/**\n * @suppress\n */\n{CORE_EXCEPTION_CONVERTER}",
    )
    exc_block, n = patch_core_exception(exc_block)
    counts["core_exception_message"] = n

    conv_block, conv_start, conv_end = _extract_block(
        text,
        CORE_EXCEPTION_CONVERTER,
        "\n\n/**\n * How the client authenticates against the edge.\n */\nsealed class Credentials",
    )
    conv_block, n = patch_core_error_converter(conv_block)
    counts["core_error_converter_message"] = n

    text = (
        text[:iface_start]
        + iface_block
        + text[iface_end:class_start]
        + class_block
        + text[class_end:exc_start]
        + exc_block
        + text[exc_end:conv_start]
        + conv_block
        + text[conv_end:]
    )

    path.write_text(text, encoding="utf-8")
    return counts


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Patch UniFFI zeron_core.kt for Android Kotlin compatibility.",
    )
    parser.add_argument(
        "kotlin_dir",
        nargs="?",
        default="target/android-core/kotlin",
        help="Kotlin bindings output directory (default: target/android-core/kotlin)",
    )
    args = parser.parse_args()

    kotlin_dir = Path(args.kotlin_dir)
    kt_path = kotlin_dir / "uniffi" / "zeron_core" / "zeron_core.kt"
    if not kt_path.is_file():
        print(f"error: bindings file not found: {kt_path}", file=sys.stderr)
        return 1

    try:
        counts = patch_file(kt_path)
    except ValueError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1

    total = sum(counts.values())
    print(f"patched {kt_path}")
    for key, n in counts.items():
        print(f"  {key}: {n}")
    print(f"  total replacements: {total}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
