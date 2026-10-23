#!/usr/bin/env python3
"""Compile and run pure Kotlin design-helper unit tests without Gradle."""

from __future__ import annotations

import argparse
import glob
import os
import shutil
import subprocess
import sys
import tempfile

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))


def default_cache_dir() -> str:
    env = os.environ.get("ZERONA_JVM_CACHE")
    if env:
        return env
    return os.path.join(os.path.expanduser("~"), ".cache", "zerona-jvm")


def default_gradle_home() -> str:
    return os.environ.get("GRADLE_USER_HOME", os.path.join(os.path.expanduser("~"), ".gradle"))


def compiler_glob(gradle_home: str) -> str:
    return os.path.join(
        gradle_home,
        "caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-compiler-embeddable/1.9.23/**/",
        "kotlin-compiler-embeddable-1.9.23.jar",
    )


def trove_glob(gradle_home: str) -> str:
    return os.path.join(
        gradle_home,
        "caches/modules-2/files-2.1/org.jetbrains.intellij.deps/trove4j/1.0.20200330/**/",
        "trove4j-1.0.20200330.jar",
    )

PRODUCTION_SOURCES = [
    "apps/android/app/src/main/java/sh/zeron/zerona/ui/theme/ZeronaColorTokens.kt",
    "apps/android/app/src/main/java/sh/zeron/zerona/ui/theme/TranscriptColorTokens.kt",
    "apps/android/app/src/main/java/sh/zeron/zerona/ui/theme/Contrast.kt",
    "apps/android/app/src/main/java/sh/zeron/zerona/ui/ComposerPresentation.kt",
]

TEST_SOURCES = [
    "apps/android/app/src/test/java/sh/zeron/zerona/ui/theme/ZeronaColorTokensTest.kt",
    "apps/android/app/src/test/java/sh/zeron/zerona/ui/theme/ContrastTest.kt",
    "apps/android/app/src/test/java/sh/zeron/zerona/ui/ComposerPresentationTest.kt",
]

TEST_CLASSES = [
    "sh.zeron.zerona.ui.theme.ZeronaColorTokensTest",
    "sh.zeron.zerona.ui.theme.ContrastTest",
    "sh.zeron.zerona.ui.ComposerPresentationTest",
]


def resolve_jar(glob_pattern: str, label: str, override: str | None = None) -> str:
    if override:
        if not os.path.isfile(override):
            raise SystemExit(f"{label} jar not found: {override}")
        return override
    matches = glob.glob(glob_pattern, recursive=True)
    if not matches:
        raise SystemExit(f"{label} jar not found (glob {glob_pattern})")
    return matches[0]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--cache-dir",
        default=default_cache_dir(),
        help="Directory with kotlin-stdlib, junit, hamcrest, annotations jars",
    )
    parser.add_argument("--compiler-classpath", help="kotlin-compiler-embeddable jar")
    parser.add_argument("--trove-classpath", help="trove4j jar for K2JVMCompiler")
    parser.add_argument("--stdlib", help="kotlin-stdlib jar")
    parser.add_argument("--kotlin-script-runtime", help="kotlin-script-runtime jar")
    parser.add_argument("--annotations", help="jetbrains annotations jar")
    parser.add_argument("--junit-classpath", nargs=2, metavar=("JUNIT", "HARMCREST"))
    parser.add_argument("--java", default=os.environ.get("JAVA", "/usr/bin/java"))
    args = parser.parse_args()

    cache = args.cache_dir
    gradle_home = default_gradle_home()

    stdlib = args.stdlib or os.path.join(cache, "kotlin-stdlib-1.9.23.jar")
    script_runtime = args.kotlin_script_runtime or os.path.join(
        cache,
        "kotlin-script-runtime-1.9.23.jar",
    )
    annotations = args.annotations or os.path.join(cache, "annotations-13.0.jar")
    junit = (args.junit_classpath or (None, None))[0] or os.path.join(cache, "junit-4.13.2.jar")
    hamcrest = (args.junit_classpath or (None, None))[1] or os.path.join(
        cache,
        "hamcrest-core-1.3.jar",
    )
    compiler = resolve_jar(compiler_glob(gradle_home), "kotlin compiler", args.compiler_classpath)
    trove = resolve_jar(trove_glob(gradle_home), "trove4j", args.trove_classpath)

    for path in [stdlib, script_runtime, annotations, junit, hamcrest, trove]:
        if not os.path.isfile(path):
            raise SystemExit(f"missing dependency jar: {path}")

    work = tempfile.mkdtemp(prefix="zerona-design-tests-")
    try:
        out = os.path.join(work, "classes")
        os.makedirs(out, exist_ok=True)

        sources = [os.path.join(REPO_ROOT, rel) for rel in PRODUCTION_SOURCES + TEST_SOURCES]
        for src in sources:
            if not os.path.isfile(src):
                raise SystemExit(f"missing source: {src}")

        compiler_cp = os.pathsep.join([compiler, stdlib, script_runtime, trove, annotations])
        compile_cmd = [
            args.java,
            "-cp",
            compiler_cp,
            "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
            "-no-stdlib",
            "-no-reflect",
            "-jvm-target",
            "17",
            "-classpath",
            os.pathsep.join([stdlib, junit, hamcrest]),
            "-d",
            out,
        ] + sources

        print("Compiling design helpers + tests…", file=sys.stderr)
        subprocess.check_call(compile_cmd)

        run_cp = os.pathsep.join([out, stdlib, junit, hamcrest])
        run_cmd = [args.java, "-cp", run_cp, "org.junit.runner.JUnitCore"] + TEST_CLASSES
        print("Running JUnit…", file=sys.stderr)
        subprocess.check_call(run_cmd)
    finally:
        shutil.rmtree(work, ignore_errors=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
