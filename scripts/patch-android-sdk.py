"""Set the Android SDK's wallet hub in the packaged service startup script."""

import io
from pathlib import Path
import sys
import tarfile
import zipfile


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
                    else:
                        patched.addfile(entry, package.extractfile(entry) if entry.isfile() else None)
            temporary.replace(output)
