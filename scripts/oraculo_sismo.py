"""Simula el oráculo de eventos: envía un sismo firmado con HMAC al backend local (HU-MOV-10, ADR 0027).

Uso: python scripts/oraculo_sismo.py <ciudad> <magnitud> [puerto]
     python scripts/oraculo_sismo.py Bogotá 7.1

La llave es ORACLE_SECRET, de la variable de entorno o de backend/.env; nunca se imprime.
Con magnitud de 6,5 o más se crea y liquida un siniestro por póliza vigente de la ciudad.
"""
import hashlib
import hmac
import json
import os
import sys
import time
import urllib.error
import urllib.request
import uuid
from datetime import datetime, timezone
from pathlib import Path


def oracle_secret() -> str:
    value = os.environ.get("ORACLE_SECRET", "")
    env_file = Path(__file__).resolve().parents[1] / "backend" / ".env"
    if not value and env_file.exists():
        for line in env_file.read_text(encoding="utf-8").splitlines():
            if line.startswith("ORACLE_SECRET="):
                value = line.split("=", 1)[1].strip().strip('"')
    if not value:
        sys.exit("Falta ORACLE_SECRET: póngalo en backend/.env (mismo valor que usa el backend) y reinicie el backend.")
    return value


city = sys.argv[1] if len(sys.argv) > 1 else "Bogotá"
magnitude = float(sys.argv[2]) if len(sys.argv) > 2 else 7.0
port = sys.argv[3] if len(sys.argv) > 3 else "8000"
body = json.dumps({
    "event_id": f"sismo-{uuid.uuid4().hex[:12]}",
    "type": "SISMO",
    "magnitude": magnitude,
    "city": city,
    "occurred_at": datetime.now(timezone.utc).isoformat(),
}).encode()
stamp = str(int(time.time()))
signature = hmac.new(oracle_secret().encode(), stamp.encode() + b"." + body, hashlib.sha256).hexdigest()
request = urllib.request.Request(
    f"http://localhost:{port}/api/v1/oracle/events", data=body, method="POST",
    headers={"Content-Type": "application/json", "X-Oracle-Timestamp": stamp, "X-Oracle-Signature": signature},
)
try:
    with urllib.request.urlopen(request) as response:
        print(response.status, response.read().decode())
except urllib.error.HTTPError as error:
    print(error.code, error.read().decode())
