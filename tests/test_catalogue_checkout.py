# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Checksum-pinned CC0 catalogue bytes survive Windows and Unix checkout conventions."""
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
CATALOGUE = Path('data/music-metadata-catalogue')


class CatalogueCheckoutTest(unittest.TestCase):
    @unittest.skipUnless(shutil.which('git'), 'Git is required for checkout byte verification')
    def test_pinned_snapshot_bytes_survive_both_checkout_conventions(self):
        manifest = json.loads((ROOT / CATALOGUE / 'manifest.json').read_text(encoding='utf-8'))
        source = (ROOT / 'java/src/main/java/io/github/luckolite/interpreter/MusicMetadataCatalogueSnapshot.java').read_text(encoding='utf-8')
        manifest_sha = re.search(r'MANIFEST_SHA\s*=\s*"([0-9a-f]{64})"', source).group(1)
        hashes = {'manifest.json': manifest_sha}
        hashes.update({row['file']: row['sha256'] for row in manifest['assets']})
        with tempfile.TemporaryDirectory(prefix='catalogue-checkout-') as folder:
            temporary = Path(folder)
            repository = temporary / 'repo'
            repository.mkdir()
            def git(*arguments):
                return subprocess.run(['git', *arguments], cwd=repository, check=True,
                                      capture_output=True, text=True)
            git('init', '--quiet')
            # Reproduce the Windows add operation that previously normalized pinned CRLF bytes.
            git('config', 'core.autocrlf', 'true')
            shutil.copyfile(ROOT / '.gitattributes', repository / '.gitattributes')
            for name, expected in hashes.items():
                target = repository / CATALOGUE / name
                target.parent.mkdir(parents=True, exist_ok=True)
                data = (ROOT / CATALOGUE / name).read_bytes()
                self.assertEqual(expected, hashlib.sha256(data).hexdigest(), name)
                target.write_bytes(data)
            git('add', '--', '.gitattributes', CATALOGUE.as_posix())
            for mode in ('false', 'true'):
                exported = temporary / ('checkout-' + mode)
                exported.mkdir()
                git('-c', 'core.autocrlf=' + mode, 'checkout-index', '--all',
                    '--prefix=' + exported.as_posix() + '/')
                for name, expected in hashes.items():
                    with self.subTest(autocrlf=mode, asset=name):
                        self.assertEqual(expected, hashlib.sha256(
                            (exported / CATALOGUE / name).read_bytes()).hexdigest())


if __name__ == '__main__':
    unittest.main()
