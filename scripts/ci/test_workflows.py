"""Controles del pipeline verificados sobre los workflows y el documento del proceso (HOTFIX evidencias CI/CD)."""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CI = (ROOT / ".github/workflows/ci.yml").read_text(encoding="utf-8")
DEPLOY = (ROOT / ".github/workflows/deploy.yml").read_text(encoding="utf-8")
DOC = (ROOT / "docs/ci-cd.md").read_text(encoding="utf-8")

ALLOWED_ACTIONS = {
    "actions/checkout",
    "actions/setup-python",
    "actions/setup-node",
    "actions/setup-java",
    "actions/cache",
    "actions/upload-artifact",
    "actions/download-artifact",
    "snok/install-poetry",
    "gradle/actions/setup-gradle",
    "hashicorp/setup-terraform",
    "SonarSource/sonarqube-scan-action",
    "aws-actions/configure-aws-credentials",
    "aws-actions/amazon-ecr-login",
}


def job(workflow: str, name: str) -> str:
    """Texto de un trabajo: desde su clave hasta la siguiente clave de trabajo."""
    match = re.search(rf"^  {name}:\n(.*?)(?=^  [a-z][\w-]*:\n|\Z)", workflow, re.S | re.M)
    if match is None:
        raise AssertionError(f"no existe el trabajo {name}")
    return match.group(1)


class WorkflowControlsTest(unittest.TestCase):

    def test_ci_y_cd_tienen_token_de_solo_lectura(self):
        for workflow in (CI, DEPLOY):
            self.assertRegex(workflow, r"(?m)^permissions:\n  contents: read$")
            self.assertNotRegex(workflow, r"(?m)^\s+\w[\w-]*: write$")

    def test_solo_se_usan_acciones_conocidas(self):
        used = set(re.findall(r"uses: ([\w./-]+)@", CI + DEPLOY))

        self.assertEqual(used - ALLOWED_ACTIONS, set())

    def test_los_trabajos_de_pruebas_no_reciben_secretos(self):
        for name in ("backend-ci", "web-ci", "mobile-ci", "sdd-gate"):
            self.assertNotIn("secrets.", job(CI, name), name)

    def test_cada_componente_publica_resumen_y_reporte_junit(self):
        for name, artifact in (("backend-ci", "junit-backend"), ("web-ci", "junit-web"), ("mobile-ci", "junit-movil")):
            text = job(CI, name)
            self.assertIn("scripts/ci/junit_summary.py", text, name)
            self.assertIn("GITHUB_STEP_SUMMARY", text, name)
            self.assertIn(f"name: {artifact}", text, name)
            self.assertEqual(text.count("if: always()"), 2, name)

    def test_el_despliegue_solo_corre_desde_main_con_el_ambiente_produccion(self):
        infra = job(DEPLOY, "deploy-infra")

        self.assertIn("if: github.ref == 'refs/heads/main'", infra)
        self.assertIn("environment: produccion", infra)
        self.assertIn("needs: deploy-infra", job(DEPLOY, "deploy-backend"))
        self.assertIn("needs: deploy-infra", job(DEPLOY, "deploy-web"))

    def test_la_prueba_de_humo_va_al_final_y_sin_secretos(self):
        smoke = job(DEPLOY, "smoke-test")

        self.assertIn("needs: [deploy-infra, deploy-backend, deploy-web]", smoke)
        self.assertIn("scripts/ci/smoke_test.py", smoke)
        self.assertNotIn("secrets.", smoke)
        self.assertIn("aws ecs wait services-stable", job(DEPLOY, "deploy-backend"))

    def test_el_documento_describe_el_proceso_y_las_condiciones(self):
        for heading in (
            "## Recorrido de un cambio",
            "## Ramas y versiones",
            "## Integración continua",
            "## Condiciones para fusionar",
            "## Despliegue continuo",
            "## Apagado y recreación de la infraestructura",
            "## Evidencias",
        ):
            self.assertIn(heading, DOC)
        self.assertIn("produccion", DOC)


if __name__ == "__main__":
    unittest.main()
