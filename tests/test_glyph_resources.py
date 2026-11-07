"""Original packaging checks for the licensed single-glyph recognition resources."""
import importlib.util
import json
import os
import shutil
import subprocess
import tempfile
import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("glyph_build_java", ROOT / "scripts/build_java.py")
build_java = importlib.util.module_from_spec(spec)
spec.loader.exec_module(build_java)
RESOURCE = Path("java/src/main/resources")
GLYPHS = Path("io/github/luckolite/interpreter/glyphs")


class GlyphResourceTest(unittest.TestCase):
    @unittest.skipUnless(shutil.which("git"), "Git is needed to test checkout byte preservation")
    def test_windows_autocrlf_checkout_preserves_all_manifest_bytes(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder) / "repo"
            root.mkdir()
            shutil.copyfile(ROOT / ".gitattributes", root / ".gitattributes")
            shutil.copytree(ROOT / RESOURCE, root / RESOURCE)
            env = os.environ.copy()
            # Never inherit a maintainer's alternate staging index into this fixture.
            env["GIT_INDEX_FILE"] = str(root / ".git/index")
            def git(*args):
                subprocess.run(["git", "-C", str(root), "-c", "core.autocrlf=true",
                                "-c", "core.safecrlf=false", *args], env=env,
                               check=True, capture_output=True)
            git("init")
            git("add", "--", ".gitattributes", RESOURCE.as_posix())
            checked = Path(folder) / "checked"
            git("checkout-index", "--all", "--prefix=" + checked.as_posix() + "/")
            original = {path.relative_to(ROOT / RESOURCE).as_posix(): data
                        for path, data in build_java.resource_snapshots()}
            actual = {path.relative_to(checked / RESOURCE).as_posix(): data
                      for path, data in build_java.resource_snapshots(checked)}
            self.assertEqual(original, actual)

    def test_manifest_accounts_for_exactly_twenty_seven_templates_and_two_notices(self):
        rows = build_java.resource_snapshots()
        self.assertEqual(27, sum(path.suffix == ".png" for path, _ in rows))
        self.assertEqual(2, sum(path.name.endswith("-OFL.txt") for path, _ in rows))
        self.assertEqual(1, sum(path.name == "manifest.json" for path, _ in rows))

    def test_changed_glyph_is_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            shutil.copytree(ROOT / RESOURCE, root / RESOURCE)
            path = root / RESOURCE / GLYPHS / "dynamic_templates/p.png"
            path.write_bytes(path.read_bytes() + b"changed")
            with self.assertRaisesRegex(ValueError, "checksum mismatch"):
                build_java.resource_snapshots(root)

    def test_unreviewed_extra_file_is_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            shutil.copytree(ROOT / RESOURCE, root / RESOURCE)
            (root / RESOURCE / GLYPHS / "extra.png").write_bytes(b"unreviewed")
            with self.assertRaisesRegex(ValueError, "account for every"):
                build_java.resource_snapshots(root)

    def test_jar_contains_verified_bytes_and_notices(self):
        jar = ROOT / "src/sheet_music_recognition/interpreter.jar"
        if not jar.is_file():
            self.skipTest("Build the standalone JAR before checking packaged resources")
        with zipfile.ZipFile(jar) as bundle:
            for path, data in build_java.resource_snapshots():
                self.assertEqual(data, bundle.read(path.relative_to(ROOT / RESOURCE).as_posix()))


if __name__ == "__main__":
    unittest.main()
