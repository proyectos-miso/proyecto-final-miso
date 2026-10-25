"""Pruebas del resumen de reportes JUnit del CI (HOTFIX evidencias CI/CD)."""
import contextlib
import io
import tempfile
import unittest
from pathlib import Path

import junit_summary

PYTEST_REPORT = """<?xml version="1.0" encoding="utf-8"?>
<testsuites><testsuite name="pytest" tests="4">
  <testcase classname="tests.test_a" name="test_ok"/>
  <testcase classname="tests.test_a" name="test_falla"><failure message="assert 1 == 2">detalle</failure></testcase>
  <testcase classname="tests.test_a" name="test_error"><error message="">Traceback largo</error></testcase>
  <testcase classname="tests.test_a" name="test_omitida"><skipped message="sin base"/></testcase>
</testsuite></testsuites>"""

GRADLE_REPORT = """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.solventa.app.ui.auth.AuthViewModelTest" tests="2">
  <testcase name="entra con huella" classname="com.solventa.app.ui.auth.AuthViewModelTest"/>
  <testcase name="bloqueo | cuenta regresiva" classname="com.solventa.app.ui.auth.AuthViewModelTest">
    <failure message="expected:&lt;900&gt; but was:&lt;0&gt;"/>
  </testcase>
</testsuite>"""


class JunitSummaryTest(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.dir = Path(self.tmp.name)

    def tearDown(self):
        self.tmp.cleanup()

    def write(self, name: str, content: str) -> Path:
        path = self.dir / name
        path.write_text(content, encoding="utf-8")
        return path

    def test_cuenta_aprobadas_fallidas_errores_y_omitidas(self):
        summary = junit_summary.summarize([self.write("pytest.xml", PYTEST_REPORT)])

        self.assertEqual((summary.total, summary.passed, summary.failed, summary.skipped), (4, 1, 2, 1))
        self.assertEqual(
            [f.name for f in summary.failures], ["tests.test_a.test_falla", "tests.test_a.test_error"]
        )
        self.assertEqual(summary.failures[1].message, "Traceback largo")

    def test_suma_varios_reportes_con_raiz_testsuite(self):
        summary = junit_summary.summarize(
            [self.write("a.xml", PYTEST_REPORT), self.write("b.xml", GRADLE_REPORT)]
        )

        self.assertEqual((summary.total, summary.failed), (6, 3))

    def test_resumen_en_markdown_escapa_barras_de_tabla(self):
        text = junit_summary.render("Móvil", junit_summary.summarize([self.write("b.xml", GRADLE_REPORT)]), True)

        self.assertIn("### ❌ Móvil", text)
        self.assertIn("| 2 | 1 | 1 | 0 |", text)
        self.assertIn("bloqueo / cuenta regresiva", text)

    def test_mensajes_largos_se_recortan(self):
        long_report = PYTEST_REPORT.replace("assert 1 == 2", "x" * 500)

        summary = junit_summary.summarize([self.write("p.xml", long_report)])

        self.assertEqual(len(summary.failures[0].message), junit_summary.MAX_MESSAGE)

    def test_todo_aprobado_muestra_marca_verde(self):
        text = junit_summary.render("Web", junit_summary.Summary(passed=91), True)

        self.assertIn("### ✅ Web", text)
        self.assertNotIn("Prueba fallida", text)

    def test_sin_reportes_avisa_sin_fallar(self):
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            code = junit_summary.main(["Backend", str(self.dir / "no-existe.xml")])

        self.assertEqual(code, 0)
        self.assertIn("No se generó el reporte", out.getvalue())

    def test_sin_argumentos_muestra_el_uso(self):
        with contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(junit_summary.main([]), 2)


if __name__ == "__main__":
    unittest.main()
