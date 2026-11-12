"""Extract the pinned upstream runtime, replacing its service Java sources locally."""
import hashlib
import io
from pathlib import Path
import sys
import zipfile
source, target = map(Path, sys.argv[1:])
if hashlib.sha256(source.read_bytes()).hexdigest() != '812ec2eb3516e295eab04f5de7f887e6f634139e2a3c76260b2722c5ba34c8d4':
    raise ValueError("Unexpected upstream SDK archive checksum")
with zipfile.ZipFile(source) as aar:
    target.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(io.BytesIO(aar.read('classes.jar'))) as classes:
        with zipfile.ZipFile(target / 'runtime.jar', 'w') as runtime:
            for item in classes.infolist():
                if not item.filename.startswith('io/lbry/lbrysdk/'):
                    runtime.writestr(item, classes.read(item))
    for item in aar.infolist():
        if item.filename.startswith(('jni/', 'res/', 'assets/')) and not item.is_dir():
            # The app supplies the patched private.mp3 archive.
            if item.filename == 'assets/private.mp3':
                continue
            output = target / item.filename
            if not output.resolve().is_relative_to(target.resolve()):
                raise ValueError('Unsafe archive path')
            output.parent.mkdir(parents=True, exist_ok=True)
            output.write_bytes(aar.read(item))
