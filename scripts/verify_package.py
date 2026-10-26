#!/usr/bin/env python3
# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Check public assets, source boundary and license material before packaging."""

import hashlib
import json
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def is_assistant_artifact(relative):
    """Keep local agent instructions and session state out of public packages."""
    parts = Path(relative.replace("\\", "/")).parts
    folders = {".codex", ".claude", ".agents", "scratch", "tmp"}
    names = {
        "agents.md",
        "claude.md",
        "gemini.md",
        "skill.md",
        "memory.md",
        "memory_summary.md",
        "resume-latest.json",
    }
    return any(part.lower() in folders for part in parts[:-1]) or (
        bool(parts) and parts[-1].lower() in names
    )


def is_optional_android_adapter(relative):
    """Only the separately built, tracked Android adapter source tree is optional."""
    parts = relative.replace("\\", "/").split("/")
    return (
        len(parts) >= 10
        and parts[:2] == ["android-adapters", "src"]
        and parts[2] in {"main", "androidTest"}
        and parts[3:9] == ["java", "io", "github", "luckolite", "interpreter", "android"]
        and all(re.fullmatch(r"[A-Za-z_$][A-Za-z0-9_$]*", part)
                for part in parts[9:-1])
        and re.fullmatch(r"[A-Za-z_$][A-Za-z0-9_$]*\.java", parts[-1]) is not None
    )


def require_java_dependency_boundary(relative, text):
    if "android." in text and not is_optional_android_adapter(relative):
        raise SystemExit("Android dependency in standalone Java: " + relative)


def main():
    tracked = (
        subprocess.check_output(["git", "ls-files", "-z"], cwd=ROOT)
        .decode()
        .split("\0")
    )
    tracked = [p for p in tracked if p]
    if not tracked:
        raise SystemExit(
            "Stage the intended public files before running this verification"
        )
    banned = re.compile(
        r"(^|/)(?:\.venv[^/]*|build|dist|tmp|\.signing|backups|diagnostic-export)(/|$)|\.(?:apk|keystore|jks|bundle|page\.gz)$",
        re.I,
    )
    credential = re.compile(
        r"(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,}|-----BEGIN (?:RSA |OPENSSH |EC )?PRIVATE KEY-----)"
    )
    for relative in tracked:
        if banned.search(relative) or is_assistant_artifact(relative):
            raise SystemExit("Private/build artifact in public staging: " + relative)
        path = ROOT / relative
        if path.stat().st_size >= 100 * 1024 * 1024:
            raise SystemExit("Oversize Git object: " + relative)
        if path.suffix in (".py", ".java", ".json", ".md", ".toml", ".yml"):
            text = path.read_text(encoding="utf-8")
            if credential.search(text) or re.search(r"[A-Za-z]:[\\/]+Users[\\/]", text):
                raise SystemExit(
                    "Credential or personal machine path found: " + relative
                )
            if path.suffix == ".java":
                require_java_dependency_boundary(relative, text)
    artifacts = json.loads((ROOT / "models/artifacts.json").read_text())
    for name, expected in artifacts.items():
        path = ROOT / "models" / name
        if hashlib.sha256(path.read_bytes()).hexdigest() != expected["sha256"]:
            raise SystemExit("Model checksum mismatch: " + name)
    for name in (
        "LICENSE",
        "NOTICE",
        "THIRD_PARTY_NOTICES.md",
        "models/MODEL_CARD.md",
        "training/fonts/NOTO-SERIF-OFL.txt",
        "training/fonts/BRAVURA-OFL.txt",
        "training/fonts/LELAND-OFL.txt",
    ):
        if name not in tracked:
            raise SystemExit("Required public license/document is not staged: " + name)
    print(
        "Verified",
        len(tracked),
        "public files, independent model checksums and license notices",
    )


if __name__ == "__main__":
    main()
