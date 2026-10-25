"""Prueba de humo después del despliegue: solo peticiones GET, sin credenciales ni datos.

Uso: python scripts/ci/smoke_test.py https://<distribucion>.cloudfront.net [--intentos N] [--espera S]

Verifica que el portal responde, que el backend está sano y que /api/* llega al backend. Imprime un
resumen en Markdown y termina con código 1 si alguna verificación falla.
"""
import argparse
import json
import sys
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from typing import Callable, List, Optional, Tuple

TIMEOUT_SECONDS = 10


@dataclass
class Check:
    name: str
    path: str
    expected_status: int
    validate: Callable[[str], bool]


@dataclass
class Result:
    check: Check
    status: Optional[int]
    ok: bool


def _is_portal(body: str) -> bool:
    return "<app-root" in body or "<title>" in body


def _is_healthy(body: str) -> bool:
    try:
        return json.loads(body).get("status") == "healthy"
    except ValueError:
        return False


def _is_backend_json(body: str) -> bool:
    try:
        return "detail" in json.loads(body)
    except ValueError:
        return False


# GET sobre una ruta que solo acepta POST: el backend responde 405 en JSON sin ejecutar nada.
CHECKS = [
    Check("Portal web", "/", 200, _is_portal),
    Check("Salud del backend", "/health", 200, _is_healthy),
    Check("La API llega al backend", "/api/v1/auth/otp", 405, _is_backend_json),
]


def fetch(url: str) -> Tuple[Optional[int], str]:
    request = urllib.request.Request(url, method="GET", headers={"User-Agent": "solventa-smoke-test"})
    try:
        with urllib.request.urlopen(request, timeout=TIMEOUT_SECONDS) as response:
            return response.status, response.read(65536).decode("utf-8", "replace")
    except urllib.error.HTTPError as error:
        return error.code, error.read(65536).decode("utf-8", "replace")
    except (urllib.error.URLError, TimeoutError, OSError):
        return None, ""


def run(base_url: str, attempts: int, wait: float, fetcher=fetch, sleep=time.sleep) -> List[Result]:
    base = base_url.rstrip("/")
    results = []
    for check in CHECKS:
        status, ok = None, False
        for attempt in range(attempts):
            status, body = fetcher(base + check.path)
            ok = status == check.expected_status and check.validate(body)
            if ok:
                break
            if attempt < attempts - 1:
                sleep(wait)
        results.append(Result(check, status, ok))
    return results


def render(base_url: str, results: List[Result]) -> str:
    icon = "✅" if all(r.ok for r in results) else "❌"
    lines = [
        f"### {icon} Prueba de humo",
        "",
        f"Ambiente: {base_url}",
        "",
        "| Verificación | Ruta | Esperado | Obtenido | Resultado |",
        "|---|---|---|---|---|",
    ]
    for r in results:
        got = r.status if r.status is not None else "sin respuesta"
        lines.append(f"| {r.check.name} | {r.check.path} | {r.check.expected_status} | {got} | {'✅' if r.ok else '❌'} |")
    return "\n".join(lines) + "\n"


def main(argv: List[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("base_url")
    parser.add_argument("--intentos", type=int, default=10)
    parser.add_argument("--espera", type=float, default=15)
    args = parser.parse_args(argv)
    if not args.base_url.startswith(("https://", "http://")):
        parser.error("la URL debe empezar por https:// o http://")
    results = run(args.base_url, args.intentos, args.espera)
    print(render(args.base_url, results))
    return 0 if all(r.ok for r in results) else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
