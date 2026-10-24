# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original binary fixture manifests use the same relative paths on every OS."""
from pathlib import Path, PurePosixPath
import unittest


class SourceBeamManifestPathsTest(unittest.TestCase):
    def test_every_binary_reference_is_portable_and_present(self):
        root = Path(__file__).resolve().parents[1] / 'java/src/test/resources/source-beams'
        references = 0
        for manifest in sorted(root.rglob('*.tsv')):
            for number, line in enumerate(manifest.read_text(encoding='utf-8').splitlines(), 1):
                for value in line.split('\t'):
                    if not value.endswith('.bin'):
                        continue
                    with self.subTest(manifest=str(manifest.relative_to(root)), line=number, path=value):
                        self.assertNotIn('\\', value)
                        self.assertFalse(PurePosixPath(value).is_absolute())
                        self.assertTrue((manifest.parent / PurePosixPath(value)).is_file())
                    references += 1
        self.assertGreater(references, 0)


if __name__ == '__main__':
    unittest.main()
