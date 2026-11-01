"""Carátula en PDF y adaptador de S3 sin red (HU-WEB-16: TC-01, TC-02, TC-06 y TC-08)."""
from datetime import datetime, timezone
from types import SimpleNamespace
from urllib.parse import parse_qs, urlparse

import pytest
from botocore.stub import Stubber

from app.core.storage import S3DocumentStorage, StorageUnavailableError
from app.modules.policy.document import caratula_key, render_caratula
from tests.pdf_text import pdf_text

pytestmark = pytest.mark.unit

COVERAGES = [
    {"name": "Muerte por cualquier causa", "limit": 200000000.0, "deductible": 0.0, "included": True},
    {"name": "Incapacidad total y permanente", "limit": 200000000.0, "deductible": 500000.0, "included": True},
    {"name": "Enfermedades graves", "limit": 100000000.0, "deductible": 0.0, "included": False},
]


def policy(**changes) -> SimpleNamespace:
    values = dict(
        policy_number="POL-2026-00001",
        status="ACTIVE",
        conditions={"tier": "premium", "monthly_premium": 100000.0, "annual_premium": 1200000.0, "coverages": COVERAGES},
        issued_at=datetime(2026, 10, 21, 17, 0, tzinfo=timezone.utc),
        valid_until=datetime(2027, 10, 21, 17, 0, tzinfo=timezone.utc),
        holder_name="Carolina Prueba Gómez",
        holder_document_type="CC",
        holder_email="carolina@example.com",
        holder_phone="3001112233",
        insured_document="1020304050",
        beneficiaries=[{"name": "Beneficiaria Prueba", "document": "9988776655"}],
    )
    values.update(changes)
    return SimpleNamespace(**values)


def render(**changes) -> str:
    return pdf_text(render_caratula(policy(**changes), None, datetime(2026, 11, 1, 13, 5, tzinfo=timezone.utc)))


def test_tc_02_la_caratula_trae_numero_titular_plan_coberturas_vigencia_y_prima():
    text = render()

    for expected in [
        "Carátula de la póliza POL-2026-00001",
        "Plan: Premium",
        "Vigencia: 21/10/2026 - 21/10/2027",
        "Socio: Portal Solventa",
        "Suma asegurada: $ 200.000.000 COP",
        "Nombre: Carolina Prueba Gómez",
        "Muerte por cualquier causa: $ 200.000.000 COP",
        "Incapacidad total y permanente: $ 200.000.000 COP, deducible $ 500.000 COP",
        "Prima mensual: $ 100.000 COP",
        "Prima anual: $ 1.200.000 COP",
        "Generada el 01/11/2026 a las 08:05 (hora de Colombia)",
    ]:
        assert expected in text, expected
    assert "Enfermedades graves" not in text


def test_tc_02_la_caratula_no_trae_el_estado_porque_se_genera_una_sola_vez():
    assert "Estado" not in render(status="ISSUED")
    assert "Pendiente" not in render(status="ISSUED")


def test_tc_02_el_documento_va_enmascarado_y_sin_contacto_ni_beneficiarios():
    text = render()

    assert "Documento: CC ******4050" in text
    for hidden in ["1020304050", "carolina@example.com", "3001112233", "Beneficiaria", "9988776655"]:
        assert hidden not in text


def test_tc_02_sin_titular_registrado_lo_dice_y_enmascara_el_documento():
    text = render(holder_name=None, holder_document_type=None, holder_email=None, holder_phone=None)

    assert "Nombre: Sin nombre registrado" in text
    assert "Documento: ******4050" in text


def test_tc_02_el_nombre_con_html_o_fuera_de_latin1_se_escribe_como_texto():
    text = render(holder_name="Ana <script>alert(1)</script> 漢字")

    assert "Nombre: Ana <script>alert(1)</script> ??" in text


def test_tc_06_la_clave_sale_del_numero_de_poliza():
    assert caratula_key("POL-2026-00001") == "caratulas/POL-2026-00001.pdf"


@pytest.fixture
def aws_storage(monkeypatch):
    """Cliente contra AWS con credenciales ficticias: firmar no usa la red."""
    monkeypatch.setenv("AWS_ACCESS_KEY_ID", "test")
    monkeypatch.setenv("AWS_SECRET_ACCESS_KEY", "test")
    return S3DocumentStorage("solventa-documents-prod", "us-east-1")


async def test_tc_01_el_enlace_es_https_de_esa_clave_vence_en_15_minutos_y_descarga_el_archivo(aws_storage):
    url = await aws_storage.download_url("caratulas/POL-2026-00001.pdf", "caratula-POL-2026-00001.pdf", 900)

    parsed = urlparse(url)
    query = parse_qs(parsed.query)
    assert parsed.scheme == "https"
    assert parsed.path.endswith("/caratulas/POL-2026-00001.pdf")
    assert query["X-Amz-Expires"] == ["900"]
    assert query["X-Amz-Algorithm"] == ["AWS4-HMAC-SHA256"]
    assert query["response-content-disposition"] == ['attachment; filename="caratula-POL-2026-00001.pdf"']
    assert query["response-content-type"] == ["application/pdf"]


async def test_tc_01_un_enlace_sin_https_contra_aws_se_rechaza(aws_storage, monkeypatch):
    monkeypatch.setattr(aws_storage.client, "generate_presigned_url", lambda *args, **kwargs: "http://inseguro.example/x")

    with pytest.raises(StorageUnavailableError):
        await aws_storage.download_url("caratulas/POL-2026-00001.pdf", "x.pdf", 900)


async def test_tc_08_un_error_de_s3_al_guardar_se_vuelve_almacenamiento_no_disponible(aws_storage):
    with Stubber(aws_storage.client) as stub:
        stub.add_client_error("put_object", service_error_code="AccessDenied", http_status_code=403)
        with pytest.raises(StorageUnavailableError):
            await aws_storage.put_pdf("caratulas/POL-2026-00001.pdf", b"%PDF")


async def test_tc_09_guardar_deja_el_cifrado_a_la_llave_kms_del_bucket(aws_storage):
    with Stubber(aws_storage.client) as stub:
        stub.add_response(
            "put_object",
            {},
            {
                "Bucket": "solventa-documents-prod",
                "Key": "caratulas/POL-2026-00001.pdf",
                "Body": b"%PDF",
                "ContentType": "application/pdf",
            },
        )
        await aws_storage.put_pdf("caratulas/POL-2026-00001.pdf", b"%PDF")
        stub.assert_no_pending_responses()


async def test_tc_08_sin_credenciales_firmar_falla_como_almacenamiento_no_disponible(monkeypatch, tmp_path):
    for name in ["AWS_ACCESS_KEY_ID", "AWS_SECRET_ACCESS_KEY", "AWS_SESSION_TOKEN", "AWS_PROFILE"]:
        monkeypatch.delenv(name, raising=False)
    monkeypatch.setenv("AWS_SHARED_CREDENTIALS_FILE", str(tmp_path / "no-existe"))
    monkeypatch.setenv("AWS_CONFIG_FILE", str(tmp_path / "no-existe"))
    monkeypatch.setenv("AWS_EC2_METADATA_DISABLED", "true")
    storage = S3DocumentStorage("solventa-documents-prod", "us-east-1")

    with pytest.raises(StorageUnavailableError):
        await storage.download_url("caratulas/POL-2026-00001.pdf", "x.pdf", 900)
