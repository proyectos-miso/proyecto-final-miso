"""Resume reportes JUnit en Markdown para el resumen de una ejecución de GitHub Actions.

Uso: python scripts/ci/junit_summary.py "<Componente>" <reporte.xml> [<reporte.xml> ...]

Solo lee nombres de pruebas y mensajes de falla; nunca falla la ejecución por sí mismo, porque
el paso de pruebas ya la marca en rojo.
"""
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from pathlib import Path
from typing import List

MAX_MESSAGE = 200


@dataclass
class Failure:
    name: str
    message: str


@dataclass
class Summary:
    passed: int = 0
    failed: int = 0
    skipped: int = 0
    failures: List[Failure] = field(default_factory=list)

    @property
    def total(self) -> int:
        return self.passed + self.failed + self.skipped


def _short(text: str) -> str:
    line = " ".join((text or "").split())
    return line if len(line) <= MAX_MESSAGE else line[: MAX_MESSAGE - 1] + "…"


def summarize(paths: List[Path]) -> Summary:
    summary = Summary()
    for path in paths:
        for case in ET.parse(path).getroot().iter("testcase"):
            problem = case.find("failure")
            if problem is None:
                problem = case.find("error")
            if problem is not None:
                summary.failed += 1
                name = ".".join(part for part in (case.get("classname"), case.get("name")) if part)
                summary.failures.append(Failure(name, _short(problem.get("message") or problem.text or "")))
            elif case.find("skipped") is not None:
                summary.skipped += 1
            else:
                summary.passed += 1
    return summary


def render(component: str, summary: Summary, found: bool) -> str:
    if not found:
        return f"### {component}\n\n⚠️ No se generó el reporte de pruebas: revise el paso de pruebas.\n"
    icon = "❌" if summary.failed else "✅"
    lines = [
        f"### {icon} {component}",
        "",
        "| Total | Aprobadas | Fallidas | Omitidas |",
        "|---|---|---|---|",
        f"| {summary.total} | {summary.passed} | {summary.failed} | {summary.skipped} |",
    ]
    if summary.failures:
        lines += ["", "| Prueba fallida | Mensaje |", "|---|---|"]
        lines += [f"| {f.name.replace('|', '/')} | {f.message.replace('|', '/')} |" for f in summary.failures]
    return "\n".join(lines) + "\n"


def main(argv: List[str]) -> int:
    if len(argv) < 2:
        print(__doc__.strip(), file=sys.stderr)
        return 2
    component, patterns = argv[0], argv[1:]
    paths = [p for p in (Path(x) for x in patterns) if p.is_file()]
    print(render(component, summarize(paths), found=bool(paths)))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
