"""Test public-header checkpointing using synthetic bytes and temporary files only."""
import ast
import asyncio
import io
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

source = ast.parse(Path(__file__).with_name('patch-android-sdk.py').read_text())
functions = ast.Module(body=[n for n in source.body if isinstance(n, ast.FunctionDef)], type_ignores=[])
namespace = {}
exec(compile(functions, '<patch functions>', 'exec'), namespace)
header = namespace['patch_headers']('class Headers:\n    async def close(self):\n        pass\n')
runtime = {'asyncio': asyncio, 'os': os}
exec(header, runtime)

class CheckpointTest(unittest.TestCase):
    def test_success_replaces_public_headers_and_removes_temporary(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / 'headers'
            target.write_bytes(b'old-long-data')
            h = runtime['Headers']()
            h.path, h.io = str(target), io.BytesIO(b'new')
            asyncio.run(h.save())
            self.assertEqual(target.read_bytes(), b'new')
            self.assertFalse(Path(str(target) + '.saving').exists())

    def test_failed_replace_preserves_previous_checkpoint(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / 'headers'
            target.write_bytes(b'previous')
            h = runtime['Headers']()
            h.path, h.io = str(target), io.BytesIO(b'updated')
            with patch.object(os, 'replace', side_effect=OSError('simulated')):
                with self.assertRaises(OSError):
                    asyncio.run(h.save())
            self.assertEqual(target.read_bytes(), b'previous')
            self.assertFalse(Path(str(target) + '.saving').exists())

    def test_memory_only_does_not_create_files(self):
        h = runtime['Headers']()
        h.path, h.io = ':memory:', io.BytesIO(b'public headers')
        with patch('builtins.open', side_effect=AssertionError('Unexpected file access')):
            asyncio.run(h.save())

    def test_patch_requires_known_initial_sync(self):
        with self.assertRaises(ValueError):
            namespace['patch_ledger']('unexpected SDK')
        result = namespace['patch_ledger']('        await self.update_headers()\n')
        self.assertLess(result.index('update_headers'), result.index('headers.save'))
        self.assertIn('except OSError:', result)

if __name__ == '__main__':
    unittest.main()
