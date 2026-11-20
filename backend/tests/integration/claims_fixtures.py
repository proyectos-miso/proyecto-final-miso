"""Datos de prueba de siniestros, fotos en S3 emulado y beneficiarios (HU-WEB-15)."""
import hashlib
import os
import uuid
from datetime import timedelta

from app.core.storage import S3DocumentStorage
from app.modules.claims.service import NewPhoto, claim_service
from tests.integration.policy_search_fixtures import NOW

FLOCI = os.environ.get("FLOCI_ENDPOINT_URL", "http://localhost:4566")
TWO_BENEFICIARIES = [
    {"name": "Lucía Prueba Gómez", "document_type": "CC", "document_number": "52000111", "relationship": "CONYUGE",
     "percentage": 60},
    {"name": "Mateo Prueba Gómez", "document_type": "TI", "document_number": "1100220033", "relationship": "HIJO",
     "percentage": 40},
]
JPEG = b"\xff\xd8\xff\xe0" + b"foto de prueba del siniestro" * 40 + b"\xff\xd9"


async def bucket() -> S3DocumentStorage:
    storage = S3DocumentStorage(f"siniestros-prueba-{uuid.uuid4().hex[:8]}", "us-east-1", FLOCI)
    await storage.ensure_bucket()
    return storage


async def photo(storage: S3DocumentStorage, claim_number: str, position: int, altered: bool = False,
                mock: bool = False) -> NewPhoto:
    """Guarda la foto en S3; si `altered`, lo guardado no coincide con la huella que registró el teléfono."""
    key = f"siniestros/{claim_number}/{position}.jpg"
    await storage.put_bytes(key, JPEG + (b"cambiada" if altered else b""), "image/jpeg")
    return NewPhoto(
        photo_id=str(uuid.uuid4()), position=position, s3_key=key, sha256=hashlib.sha256(JPEG).hexdigest(),
        latitude=4.6097, longitude=-74.0817, accuracy_m=12.5, mock_location=mock,
        captured_at=NOW - timedelta(hours=2), trusted_time_source=None if mock else "GNSS",
    )


async def report(session, claim_number: str, policy_number: str, minutes: int = 0, **fields):
    return await claim_service.register_claim(
        session, claim_number, policy_number, NOW + timedelta(minutes=minutes), **fields
    )
