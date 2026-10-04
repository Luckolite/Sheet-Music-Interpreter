#!/usr/bin/env python3
# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Compile and optionally test pinned segmentation with caller-supplied ONNX artifacts."""
import argparse
import hashlib
import os
from pathlib import Path
import subprocess
from build_java import ROOT, jdk_tool

ORT_SHA256 = "749793ebed63743fec853d093da7987a86ea5cd592d54fba898cd3233100c381"
MODEL_SHA256 = "e436efe12ddc598add9540378d6772622a2ad9d7bdb1f9d4c0ab87e3144402a7"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--onnx-jar", type=Path, required=True)
    parser.add_argument("--model", type=Path, help="Pinned v3 ONNX model; supplying it runs generated parity controls")
    parser.add_argument("--java", type=Path, help="Runtime executable; defaults to the build JDK")
    args = parser.parse_args()
    for path, expected in [(args.onnx_jar, ORT_SHA256), (args.model, MODEL_SHA256)]:
        if path is not None and (not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != expected):
            parser.error("Required pinned artifact is missing or has the wrong checksum: " + str(path))
    if args.java is not None and not args.java.is_file():
        parser.error("Java runtime executable is missing: " + str(args.java))
    if not (ROOT / "build/classes/io/github/luckolite/interpreter/ExactWhiteTileInput.class").is_file():
        parser.error("Run scripts/build_java.py first")
    classes = ROOT / "build/segmentation-classes"
    classes.mkdir(parents=True, exist_ok=True)
    classpath = os.pathsep.join(map(str, [ROOT / "build/classes", args.onnx_jar.resolve()]))
    sources = sorted((ROOT / "optional/segmentation/java").rglob("*.java"))
    sources += sorted((ROOT / "optional/segmentation/test").rglob("*.java"))
    subprocess.run([jdk_tool("javac"), "--release", "17", "-encoding", "UTF-8", "-cp", classpath,
                    "-d", str(classes), *map(str, sources)], check=True)
    classpath = str(classes) + os.pathsep + classpath
    print("Segmentation classpath: " + classpath, flush=True)
    subprocess.run([str(args.java.resolve()) if args.java else jdk_tool("java"),
                    "-cp", classpath, "io.github.luckolite.interpreter.NativeSegmentationTileStartsParity"],
                   check=True, timeout=30)
    if args.model is not None:
        subprocess.run([str(args.java.resolve()) if args.java else jdk_tool("java"),
                        "-cp", classpath, "io.github.luckolite.interpreter.NativeSegmentationThreadParity",
                        str(args.model.resolve())], check=True, timeout=300)


if __name__ == "__main__":
    main()
