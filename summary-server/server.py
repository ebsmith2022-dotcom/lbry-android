"""Owner-only test server. Bound to loopback; reach from Android with adb reverse."""
import argparse
import json
import threading
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from engine import LocalEngine, valid_claim


class Jobs:
    def __init__(self, engine):
        self.engine = engine
        self.lock = threading.Lock()
        self.jobs = {}
        self.busy = False

    def submit(self, uri):
        if not valid_claim(uri):
            raise ValueError('Invalid video address.')
        with self.lock:
            now = time.monotonic()
            self.jobs = {k: v for k, v in self.jobs.items() if now - v['created'] < 1800}
            for key, job in self.jobs.items():
                if job['uri'] == uri and job['status'] != 'error':
                    return key
            if self.busy:
                raise RuntimeError('Another video is being summarized. Try again when it finishes.')
            if len(self.jobs) >= 8:
                oldest = min(self.jobs, key=lambda key: self.jobs[key]['created'])
                del self.jobs[oldest]
            key = uuid.uuid4().hex
            self.jobs[key] = {'status': 'working', 'stage': 'Queued', 'uri': uri, 'created': now}
            self.busy = True
            threading.Thread(target=self.work, args=(key, uri), daemon=True).start()
            return key

    def work(self, key, uri):
        def progress(stage):
            with self.lock:
                if key in self.jobs:
                    self.jobs[key]['stage'] = stage
        try:
            result = self.engine.run(uri, progress)
            with self.lock:
                if key in self.jobs:
                    self.jobs[key].update(status='done', result=result)
        except Exception as error:
            # Do not expose URLs, traces, file paths, or credentials in responses/logs.
            detail = str(error) if isinstance(error, ValueError) else 'Local processing failed. Try a shorter video with clear speech.'
            with self.lock:
                if key in self.jobs:
                    self.jobs[key].update(status='error', error=detail)
        finally:
            with self.lock:
                self.busy = False

    def get(self, key):
        with self.lock:
            row = self.jobs.get(key)
            if not row or time.monotonic() - row['created'] >= 1800:
                return None
            return {k: v for k, v in row.items() if k not in ('uri', 'created')}


def handler(jobs):
    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *args):
            pass

        def respond(self, code, body):
            data = json.dumps(body).encode()
            self.send_response(code)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Cache-Control', 'no-store')
            self.send_header('Content-Length', str(len(data)))
            self.end_headers()
            self.wfile.write(data)

        def allowed(self):
            return self.headers.get('Host') in ('127.0.0.1:8766', 'localhost:8766') and not self.headers.get('Origin') and self.headers.get('X-LBRY-Local-Test') == '1'

        def do_GET(self):
            if not self.allowed():
                return self.respond(403, {'error': 'Local app requests only.'})
            if self.path == '/health':
                return self.respond(200, {'ready': True, 'local': True})
            if self.path.startswith('/jobs/'):
                row = jobs.get(self.path[6:])
                return self.respond(200 if row else 404, row or {'error': 'Summary expired. Try again.'})
            self.respond(404, {'error': 'Not found.'})

        def do_POST(self):
            if not self.allowed():
                return self.respond(403, {'error': 'Local app requests only.'})
            if self.path != '/jobs':
                return self.respond(404, {'error': 'Not found.'})
            try:
                count = int(self.headers.get('Content-Length', '0'))
                if not 0 < count <= 1024:
                    return self.respond(413, {'error': 'Request too large.'})
                self.connection.settimeout(5)
                data = json.loads(self.rfile.read(count))
                if not isinstance(data, dict):
                    raise ValueError('Invalid request.')
                key = jobs.submit(data.get('uri'))
                self.respond(202, {'job': key})
            except (ValueError, json.JSONDecodeError):
                self.respond(400, {'error': 'Invalid video address.'})
            except RuntimeError as error:
                self.respond(429, {'error': str(error)})
    return Handler


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--cache', required=True)
    args = parser.parse_args()
    engine = LocalEngine(args.cache)
    engine.load(offline=True)
    server = ThreadingHTTPServer(('127.0.0.1', 8766), handler(Jobs(engine)))
    print('Local AI summary server ready on 127.0.0.1:8766', flush=True)
    server.serve_forever()
