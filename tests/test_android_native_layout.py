# Copyright 2026 Luckolite. Licensed under the Apache License, Version 2.0.
"""Synthetic ELF regression: 16 KB PT_LOAD alone does not prove RELRO compatibility."""
import importlib.util
import struct
import tempfile
import unittest
from pathlib import Path
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
if (ROOT / 'android_native_layout.py').is_file():
    SOURCE = ROOT / 'android_native_layout.py'
else:
    SOURCE = ROOT / 'scripts/android_native_layout.py'
SPEC = importlib.util.spec_from_file_location('android_native_layout', SOURCE)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)

def elf(alignment=16384, relro_end=0xC000, congruent=True, relro=True):
    data = bytearray(0x9000)
    ident = b'\x7fELF\x02\x01\x01' + b'\0' * 9
    count = 3 if relro else 2
    struct.pack_into('<16sHHIQQQIHHHHHH', data, 0, ident, 3, 183, 1, 0, 64, 0, 0, 64, 56, count, 0, 0, 0)
    segments = [(1, 5, 0, 0, 0, 0x4000, 0x4000, alignment),
        (1, 6, 0x4000, 0x8000 if congruent else 0x9000, 0, 0x1000, 0x4000, alignment)]
    if relro: segments.append((0x6474E552, 4, 0x4000, 0x8000, 0, 0x1000, relro_end - 0x8000, 1))
    for index, segment in enumerate(segments):
        struct.pack_into('<IIQQQQQQ', data, 64 + index * 56, *segment)
    return bytes(data)

class AndroidNativeLayoutTest(unittest.TestCase):
    def test_aligned_loads_and_relro(self):
        result = MODULE.inspect_elf(elf())
        self.assertEqual(result['gnu_relro'][0]['end'], 0xC000)
        self.assertEqual(len(result['loads']), 2)

    def test_16kb_loads_do_not_hide_4kb_relro_end(self):
        with self.assertRaisesRegex(ValueError, 'GNU_RELRO end'):
            MODULE.inspect_elf(elf(relro_end=0xB000))

    def test_reject_small_load_alignment(self):
        with self.assertRaisesRegex(ValueError, 'PT_LOAD'):
            MODULE.inspect_elf(elf(alignment=4096))

    def test_reject_incongruent_load_offsets(self):
        with self.assertRaisesRegex(ValueError, 'PT_LOAD'):
            MODULE.inspect_elf(elf(congruent=False))

    def test_relro_must_be_retained(self):
        with self.assertRaisesRegex(ValueError, 'protected GNU_RELRO'):
            MODULE.inspect_elf(elf(relro=False))

    def test_reject_truncated_program_headers(self):
        with self.assertRaisesRegex(ValueError, 'program-header'):
            MODULE.inspect_elf(elf()[:80])

    def test_archive_checks_selected_abi(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'synthetic.aar'
            with ZipFile(path, 'w') as archive:
                archive.writestr('jni/arm64-v8a/libsynthetic.so', elf())
                archive.writestr('jni/armeabi-v7a/libsynthetic.so', b'not an ELF64')
            self.assertEqual(len(MODULE.inspect_archive(path)), 1)

if __name__ == '__main__':
    unittest.main()
