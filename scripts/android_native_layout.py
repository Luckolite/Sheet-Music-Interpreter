# Copyright 2026 Luckolite. Licensed under the Apache License, Version 2.0.
"""Check Android ARM64 ELF load congruence and 16 KB GNU_RELRO boundaries."""
import argparse
import hashlib
import json
import struct
from pathlib import Path
from zipfile import ZipFile

PAGE = 16384

def inspect_elf(data, name='library'):
    if len(data) < 64 or data[:5] != b'\x7fELF\x02' or data[5] not in (1, 2):
        raise ValueError('Expected a complete ELF64 header: ' + name)
    endian = '<' if data[5] == 1 else '>'
    phoff = struct.unpack_from(endian + 'Q', data, 32)[0]
    size, count = struct.unpack_from(endian + 'HH', data, 54)
    if size < 56 or count == 0 or phoff + size * count > len(data):
        raise ValueError('Invalid ELF program-header table: ' + name)
    loads, relro = [], []
    for index in range(count):
        kind, flags, offset, address, physical, file_size, memory_size, alignment = struct.unpack_from(endian + 'IIQQQQQQ', data, phoff + index * size)
        if offset + file_size > len(data) or memory_size < file_size:
            raise ValueError('Invalid ELF segment bounds: ' + name)
        if kind == 1:
            if alignment < PAGE or (address - offset) % PAGE:
                raise ValueError('PT_LOAD is not compatible with 16 KB pages: ' + name)
            loads.append({'alignment': alignment, 'address': address, 'offset': offset})
        elif kind == 0x6474e552:
            end = address + memory_size
            if end % PAGE:
                raise ValueError('GNU_RELRO end is not 16 KB aligned: ' + name)
            relro.append({'address': address, 'memory_size': memory_size, 'end': end})
    if not loads or not relro:
        raise ValueError('Expected PT_LOAD and protected GNU_RELRO segments: ' + name)
    return {'library': name, 'sha256': hashlib.sha256(data).hexdigest(), 'loads': loads, 'gnu_relro': relro}

def inspect_archive(path, abi='arm64-v8a'):
    with ZipFile(path) as archive:
        names = [name for name in archive.namelist() if '/' + abi + '/' in name and name.endswith('.so')]
        if not names:
            raise ValueError('No ' + abi + ' native libraries in ' + path.name)
        return [inspect_elf(archive.read(name), name) for name in names]

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('artifact', type=Path)
    parser.add_argument('--abi', default='arm64-v8a')
    parser.add_argument('--report', type=Path)
    args = parser.parse_args()
    result = inspect_archive(args.artifact, args.abi) if args.artifact.suffix != '.so' else [inspect_elf(args.artifact.read_bytes(), args.artifact.name)]
    document = json.dumps(result, indent=2) + '\n'
    if args.report: args.report.write_text(document, encoding='utf-8')
    print('16 KB PT_LOAD and GNU_RELRO checks passed for', len(result), 'libraries.')

if __name__ == '__main__':
    main()
