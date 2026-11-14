"""Set the Android SDK's wallet hub in the packaged service startup script."""

import io
from pathlib import Path
import sys
import tarfile
import zipfile


def patch_headers(source):
    marker = "    async def close(self):\n"
    save_method = """    async def save(self):
        # Persist public, validated blockchain headers before Android can kill
        # the service without a graceful stop. Never touch wallet/key files.
        if self.io is None or self.path == ':memory:':
            return
        snapshot = self.io.getvalue()
        def _save():
            temporary = self.path + '.saving'
            try:
                with open(temporary, 'wb') as header_file:
                    header_file.write(snapshot)
                    header_file.flush()
                    os.fsync(header_file.fileno())
                os.replace(temporary, self.path)
            finally:
                if os.path.exists(temporary):
                    os.remove(temporary)
        await asyncio.get_event_loop().run_in_executor(None, _save)

"""
    if source.count(marker) != 1:
        raise ValueError("Unexpected header persistence implementation")
    return source.replace(marker, save_method + marker)


def patch_ledger(source):
    marker = "        await self.update_headers()\n"
    if source.count(marker) != 1:
        raise ValueError("Unexpected initial header synchronization implementation")
    return source.replace(marker, marker + "        try:\n            await self.headers.save()\n        except OSError:\n            log.warning('Could not checkpoint public blockchain headers')\n")


sdk_archive, output = map(Path, sys.argv[1:])
service_name = "lbrynetservice.py"
marker = b"        blob_lru_cache_size=32,"
server_setting = b"        api='127.0.0.1:5379',\n        streaming_server='127.0.0.1:5380',\n        lbryum_servers=[('a-hub1.odysee.com', 50001)],\n"

with zipfile.ZipFile(sdk_archive) as sdk:
    with sdk.open("assets/private.mp3") as compressed:
        with tarfile.open(fileobj=compressed, mode="r:gz") as package:
            service = package.extractfile(service_name).read()
            if service.count(marker) != 1 or b"lbryum_servers=" in service:
                raise ValueError("Unexpected LBRY SDK service configuration")
            updated_service = service.replace(marker, server_setting + marker)

            output.parent.mkdir(parents=True, exist_ok=True)
            temporary = output.with_suffix(".tmp")
            with tarfile.open(temporary, mode="w:gz") as patched:
                for entry in package:
                    if entry.name == service_name:
                        entry.size = len(updated_service)
                        patched.addfile(entry, io.BytesIO(updated_service))
                    elif entry.name in (
                            'crystax_python/site-packages/lbry/wallet/header.py',
                            'crystax_python/site-packages/lbry/wallet/ledger.py'):
                        source = package.extractfile(entry).read().decode('utf-8')
                        patch = patch_headers if entry.name.endswith('/header.py') else patch_ledger
                        updated = patch(source).encode('utf-8')
                        entry.size = len(updated)
                        patched.addfile(entry, io.BytesIO(updated))
                    else:
                        patched.addfile(entry, package.extractfile(entry) if entry.isfile() else None)
            temporary.replace(output)
