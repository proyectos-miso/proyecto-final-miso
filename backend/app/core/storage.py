"""Documentos en S3 con enlaces prefirmados (HU-WEB-16, ADR 0023); Floci en local y en el CI."""
import asyncio
from functools import lru_cache
from typing import Optional, Protocol

import boto3
from botocore.config import Config
from botocore.exceptions import BotoCoreError, ClientError

from app.core.config import settings


class StorageUnavailableError(Exception):
    """El almacenamiento no respondió o rechazó la operación; el mensaje no lleva el detalle de AWS."""


class DocumentStorage(Protocol):
    async def put_pdf(self, key: str, data: bytes) -> None: ...

    async def download_url(self, key: str, filename: str, expires_in: int) -> str: ...


class S3DocumentStorage:
    """El bucket cifra con su llave KMS por defecto; el rol del backend solo toca caratulas/ (Terraform)."""

    def __init__(
        self, bucket: str, region: str, endpoint_url: Optional[str] = None, public_endpoint_url: Optional[str] = None
    ):
        self.bucket = bucket
        # Sin endpoint propio el cliente usa el de AWS, que siempre es HTTPS.
        self.require_https = endpoint_url is None
        self.client = self._client(region, endpoint_url)
        # Con docker compose el backend llega a Floci por su nombre en la red, que el navegador no resuelve:
        # el enlace se firma con la dirección pública del emulador.
        self.signer = self._client(region, public_endpoint_url) if public_endpoint_url else self.client

    @staticmethod
    def _client(region: str, endpoint_url: Optional[str]):
        # Con el emulador, credenciales de prueba: no hacen falta las de AWS ni se usan las reales.
        emulator = {"aws_access_key_id": "test", "aws_secret_access_key": "test"} if endpoint_url else {}
        # Sesión propia: la global de boto3 guarda en caché las credenciales que encontró primero.
        return boto3.session.Session().client(
            "s3",
            region_name=region,
            endpoint_url=endpoint_url,
            config=Config(
                # Con S3 sin responder el analista espera a lo sumo unos 7 s el aviso de "en generación".
                connect_timeout=2,
                read_timeout=2,
                retries={"total_max_attempts": 2, "mode": "standard"},
                signature_version="s3v4",
                s3={"addressing_style": "path" if endpoint_url else "auto"},
            ),
            **emulator,
        )

    async def ensure_bucket(self) -> None:
        """Solo con el emulador: en AWS el bucket lo crea Terraform y el rol no puede crearlo."""
        try:
            await asyncio.to_thread(self.client.head_bucket, Bucket=self.bucket)
        except ClientError:
            await asyncio.to_thread(self.client.create_bucket, Bucket=self.bucket)

    async def put_pdf(self, key: str, data: bytes) -> None:
        try:
            await asyncio.to_thread(
                self.client.put_object, Bucket=self.bucket, Key=key, Body=data, ContentType="application/pdf"
            )
        except (BotoCoreError, ClientError) as exc:
            raise StorageUnavailableError("No se pudo guardar el documento.") from exc

    async def download_url(self, key: str, filename: str, expires_in: int) -> str:
        """Enlace de solo lectura de esa clave, que descarga el archivo con su nombre."""
        try:
            url = await asyncio.to_thread(
                self.signer.generate_presigned_url,
                "get_object",
                Params={
                    "Bucket": self.bucket,
                    "Key": key,
                    "ResponseContentType": "application/pdf",
                    "ResponseContentDisposition": f'attachment; filename="{filename}"',
                },
                ExpiresIn=expires_in,
            )
        except (BotoCoreError, ClientError) as exc:
            raise StorageUnavailableError("No se pudo firmar el enlace.") from exc
        if self.require_https and not url.startswith("https://"):
            raise StorageUnavailableError("El enlace no usa HTTPS.")
        return url


@lru_cache
def get_document_storage() -> S3DocumentStorage:
    return S3DocumentStorage(
        settings.S3_BUCKET_POLICIES, settings.AWS_REGION, settings.AWS_ENDPOINT_URL, settings.S3_PUBLIC_ENDPOINT_URL
    )


async def ensure_emulator_bucket() -> None:
    """Al arrancar en local o con docker compose (Floci): crea el bucket de documentos si falta."""
    if settings.AWS_ENDPOINT_URL:
        await get_document_storage().ensure_bucket()
