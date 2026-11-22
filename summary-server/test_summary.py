import unittest
import threading
from engine import valid_claim, public_url, free_video
from server import Jobs

URI = 'lbry://test#' + 'a' * 40

class SummaryTest(unittest.TestCase):
    def test_rejects_nonpublic_and_injected_urls(self):
        self.assertTrue(valid_claim(URI))
        for uri in [None, 'http://127.0.0.1', URI + '/extra', 'lbry://x?token=x#' + 'a'*40]:
            self.assertFalse(valid_claim(uri))
        self.assertTrue(public_url('https://player.odycdn.com/api/video'))
        for url in ['file:///etc/passwd', 'https://127.0.0.1/x', 'https://player.odycdn.com@evil.com',
                    'https://player.odycdn.com.evil.com', 'http://player.odycdn.com',
                    'https://user:pass@player.odycdn.com', 'https://player.odycdn.com:444/x']:
            self.assertFalse(public_url(url))

    def test_requires_known_free_video(self):
        value = {'source': {'media_type': 'video/mp4'}}
        self.assertTrue(free_video({'value': value}))
        for amount in ['1', '-1', 'NaN', 'unknown']:
            value['fee'] = {'amount': amount}
            self.assertFalse(free_video({'value': value}))
        value['fee'] = {'amount': '0'}
        self.assertTrue(free_video({'value': value}))
        self.assertFalse(free_video({}))

    def test_one_worker_and_duplicate_job(self):
        ready, release = threading.Event(), threading.Event()
        class Fake:
            def run(self, uri, progress):
                ready.set(); release.wait(2)
                return {'summary': 'Test'}
        jobs = Jobs(Fake())
        key = jobs.submit(URI)
        try:
            self.assertTrue(ready.wait(1))
            self.assertEqual(key, jobs.submit(URI))
            with self.assertRaises(RuntimeError):
                jobs.submit('lbry://another#' + 'b'*40)
            self.assertNotIn('uri', jobs.get(key))
        finally:
            release.set()

if __name__ == '__main__': unittest.main()
