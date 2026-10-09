#!/usr/bin/env python3
# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Check full OCR session compatibility and opt-in recognizer-only parity."""
import argparse
import os
from pathlib import Path
import subprocess
import sys

from build_java import ROOT, jdk_tool


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--onnx-jar", type=Path, required=True)
    parser.add_argument("--detector", type=Path, required=True, action="append",
                        help="Pinned detector file; repeat to test both supported artifacts")
    parser.add_argument("--recognizer", type=Path, required=True)
    parser.add_argument("--java", type=Path, help="Java runtime executable; defaults to the build JDK")
    args = parser.parse_args()
    paths = [args.onnx_jar, args.recognizer, Path(str(args.recognizer) + ".dictionary"), *args.detector]
    for path in paths:
        if not path.is_file():
            parser.error("Required local file is missing: " + str(path))
    if args.java is not None and not args.java.is_file():
        parser.error("Java runtime executable is missing: " + str(args.java))
    if not (ROOT / "build/classes").is_dir():
        parser.error("Run scripts/build_java.py first")
    onnx_jar = args.onnx_jar.resolve()
    subprocess.run([sys.executable, str(ROOT / "scripts/build_portable_ocr.py"),
                    "--onnx-jar", str(onnx_jar)], check=True)
    sources = sorted((ROOT / "optional/ocr/test").rglob("*.java"))
    if not sources:
        parser.error("Optional OCR regression sources are missing")
    classes = ROOT / "build/ocr-test-classes"
    classes.mkdir(parents=True, exist_ok=True)
    classpath = os.pathsep.join(map(str, [ROOT / "build/ocr-classes", ROOT / "build/classes", onnx_jar]))
    subprocess.run([jdk_tool("javac"), "--release", "17", "-encoding", "UTF-8", "-cp", classpath,
                    "-d", str(classes), *map(str, sources)], check=True)
    java_runtime = str(args.java.resolve()) if args.java else jdk_tool("java")
    for detector in args.detector:
        subprocess.run([java_runtime, "-cp", str(classes) + os.pathsep + classpath,
                        "io.github.luckolite.interpreter.OnnxOcrSessionParity",
                        str(detector.resolve()), str(args.recognizer.resolve())], check=True, timeout=300)
    subprocess.run([java_runtime, "-cp", str(classes) + os.pathsep + classpath,
                    "io.github.luckolite.interpreter.OnnxRecognizerOnlyParity",
                    str(args.detector[0].resolve()), str(args.recognizer.resolve())], check=True, timeout=300)


if __name__ == "__main__":
    main()
