# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original cache-epoch contract; recognition revisions do not change Guide290 bytes."""
from pathlib import Path
import re
import unittest
from unittest.mock import patch

from sheet_interpreter import guide_writer as wire


class RecognitionEpochContractTest(unittest.TestCase):
    def test_writer_matches_reviewed_java_cache_identity(self):
        root = Path(__file__).resolve().parents[1]
        source = (root / "java/src/main/java/io/github/luckolite/interpreter/"
                  "GuideCacheIdentity.java").read_text(encoding="utf-8")
        revision = int(re.search(r"RECOGNITION_REVISION\s*=\s*(\d+)", source)[1])
        self.assertEqual(100, revision)
        self.assertEqual(revision, wire.RECOGNITION_REVISION)
        self.assertEqual(292, wire.GUIDE_VERSION)

    def test_recognition_epoch_does_not_relabel_wire_records(self):
        original = dict(measures=[dict(left=.1, right=.9, top=.2, bottom=.3)],
                        notes=[], firstMeasureNumber=1, keyChanges=[], tempoChanges=[],
                        meterChanges=[], rests=[], techniqueChanges=[], dynamicChanges=[])
        current = wire.encode(original, 290)
        for historical_revision in (72, 73, 80, 81, 82):
            with self.subTest(revision=historical_revision):
                with patch.object(wire, "RECOGNITION_REVISION", historical_revision):
                    self.assertEqual(current, wire.encode(original, 290))


if __name__ == "__main__":
    unittest.main()
