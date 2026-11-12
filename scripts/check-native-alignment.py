"""Check packaged ELF LOAD segments, including the bundled Python extensions.

Usage: python3 scripts/check-native-alignment.py candidate.apk
This checks ELF layout, not runtime behavior or APK ZIP alignment.
"""
import io
from pathlib import Path
import struct
import sys
import tarfile
import zipfile


def alignment_errors(name, data):
    if data[:4] != b'\x7fELF':
        return None
    if data[4:6] != b'\x02\x01':
        return [name + ': expected little-endian 64-bit ELF']
    offset = struct.unpack_from('<Q', data, 32)[0]
    size, count = struct.unpack_from('<HH', data, 54)
    errors = []
    for index in range(count):
        segment = struct.unpack_from('<IIQQQQQQ', data, offset + index * size)
        if segment[0] == 1 and (segment[7] < 16384 or (segment[2] - segment[3]) % 16384):
            errors.append(name + ': LOAD segment incompatible with 16 KB pages')
    return errors


def inspect(path):
    count, errors = 0, []
    with zipfile.ZipFile(path) as archive:
        def check(name, data):
            nonlocal count
            result = alignment_errors(name, data)
            if result is not None:
                count += 1
                errors.extend(result)
        for name in archive.namelist():
            if name.endswith('.so'):
                check(name, archive.read(name))
            if name.endswith('assets/private.mp3'):
                with tarfile.open(fileobj=io.BytesIO(archive.read(name)), mode='r:gz') as package:
                    for entry in package:
                        if entry.isfile() and '.so' in entry.name:
                            check(entry.name, package.extractfile(entry).read())
    if not count:
        raise ValueError('No native libraries found')
    print(f'Checked {count} native ELF files; {len(errors)} alignment failures')
    for error in errors:
        print(error)
    return not errors


if __name__ == '__main__':
    sys.exit(0 if inspect(Path(sys.argv[1])) else 1)
