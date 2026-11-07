# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Published package metadata and the runtime version must describe one release."""
import re
import unittest
from pathlib import Path

import sheet_music_recognition


class VersionTest(unittest.TestCase):
    def test_runtime_matches_package_metadata(self):
        metadata = (Path(__file__).resolve().parents[1] / "pyproject.toml").read_text(
            encoding="utf-8"
        )
        project = re.search(r"(?ms)^\[project\]\s*(.*?)(?=^\[|\Z)", metadata)
        self.assertIsNotNone(project)
        version = re.search(r'^version\s*=\s*"([^"]+)"', project.group(1), re.MULTILINE)
        self.assertIsNotNone(version)
        self.assertEqual(version.group(1), sheet_music_recognition.__version__)
