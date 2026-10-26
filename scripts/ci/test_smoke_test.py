"""Pruebas de la prueba de humo del despliegue contra un servidor local (HOTFIX evidencias CI/CD)."""
import contextlib
import io
import json
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import smoke_test


class FakeSolventa(BaseHTTPRequestHandler):
    """Imita CloudFront con el portal y el backend; registra los métodos que recibe."""

    methods = []
    broken_health = False

    def _reply(self, status, body, content_type):
        data = body.encode()
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        FakeSolventa.methods.append(self.command)
        if self.path == "/":
            self._reply(200, "<!doctype html><title>Solventa</title><app-root></app-root>", "text/html")
        elif self.path == "/health" and not FakeSolventa.broken_health:
            self._reply(200, json.dumps({"status": "healthy"}), "application/json")
        elif self.path == "/api/v1/auth/otp":
            self._reply(405, json.dumps({"detail": "Method Not Allowed"}), "application/json")
        else:
            self._reply(503, "no disponible", "text/plain")

    def log_message(self, *args):
        pass


class SmokeTestTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.server = ThreadingHTTPServer(("127.0.0.1", 0), FakeSolventa)
        cls.url = f"http://127.0.0.1:{cls.server.server_address[1]}"
        threading.Thread(target=cls.server.serve_forever, daemon=True).start()

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()

    def setUp(self):
        FakeSolventa.methods = []
        FakeSolventa.broken_health = False

    def run_main(self, *args):
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            code = smoke_test.main([self.url, "--intentos", "1", "--espera", "0", *args])
        return code, out.getvalue()

    def test_ambiente_sano_pasa_las_tres_verificaciones(self):
        code, report = self.run_main()

        self.assertEqual(code, 0)
        self.assertIn("### ✅ Prueba de humo", report)
        self.assertEqual(report.count("| ✅ |"), 3)

    def test_solo_hace_peticiones_get(self):
        self.run_main()

        self.assertEqual(set(FakeSolventa.methods), {"GET"})

    def test_backend_caido_falla_y_lo_nombra(self):
        FakeSolventa.broken_health = True

        code, report = self.run_main()

        self.assertEqual(code, 1)
        self.assertIn("| Salud del backend | /health | 200 | 503 | ❌ |", report)

    def test_sin_respuesta_falla_despues_de_los_reintentos(self):
        waits = []
        results = smoke_test.run("http://127.0.0.1:1", 3, 2, sleep=waits.append)

        self.assertFalse(any(r.ok for r in results))
        self.assertEqual(waits, [2, 2] * len(smoke_test.CHECKS))
        self.assertIn("sin respuesta", smoke_test.render("http://127.0.0.1:1", results))

    def test_se_recupera_si_el_servicio_tarda_en_arrancar(self):
        calls = {"n": 0}

        def slow(url):
            calls["n"] += 1
            return (None, "") if calls["n"] == 1 else smoke_test.fetch(url)

        results = smoke_test.run(self.url, 3, 0, fetcher=slow, sleep=lambda _: None)

        self.assertTrue(all(r.ok for r in results))

    def test_html_del_portal_en_la_api_no_cuenta_como_backend(self):
        self.assertFalse(smoke_test._is_backend_json("<!doctype html><title>Solventa</title>"))
        self.assertFalse(smoke_test._is_healthy("<html></html>"))

    def test_rechaza_urls_sin_esquema(self):
        with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit):
            smoke_test.main(["d1bja1oqa4gm74.cloudfront.net"])


if __name__ == "__main__":
    unittest.main()
