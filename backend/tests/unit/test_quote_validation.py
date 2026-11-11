"""Pruebas unitarias para validación de cotización, consentimiento, edad y Open Data (RC-06, RC-07, RC-08, RC-25)."""
from datetime import date, datetime, timedelta, timezone
import pytest
from fastapi import HTTPException

from app.modules.rating.models import QuoteDB, QuoteRequest
from app.modules.rating.service import OpenDataProvider, RatingService, open_finance_score_provider
from app.modules.profile.models import ConsentDB, ConsentRequest
from app.modules.profile.service import profile_service

TODAY = date(2026, 11, 10)


class MockAsyncSession:
    """Mock ligero de AsyncSession para pruebas unitarias sin base de datos externa."""

    def __init__(self):
        self.added = []
        self.committed = False

    def add(self, obj):
        self.added.append(obj)

    async def commit(self):
        self.committed = True

    async def execute(self, stmt):
        class MockResult:
            def scalars(self):
                class MockScalars:
                    def first(self_inner):
                        return None
                    def all(self_inner):
                        return []
                return MockScalars()
        return MockResult()


@pytest.mark.unit
@pytest.mark.asyncio
async def test_quote_rejects_age_under_18():
    """Valida que una persona de 17 años sea rechazada con HTTP 422 (RC-07 / TC-16)."""
    service = RatingService(today=lambda: TODAY)
    req = QuoteRequest(
        document_type="CC",
        document_number="1020304050",
        birth_date=date(2009, 11, 11),  # 16-17 años
        insured_amount=100_000_000.0,
    )
    with pytest.raises(HTTPException) as exc_info:
        await service.calculate_quote(req)
    assert exc_info.value.status_code == 422
    assert "Debe tener entre 18 y 75 años" in exc_info.value.detail


@pytest.mark.unit
@pytest.mark.asyncio
async def test_quote_rejects_age_over_75():
    """Valida que una persona de 76 años sea rechazada con HTTP 422 (RC-07 / TC-16)."""
    service = RatingService(today=lambda: TODAY)
    req = QuoteRequest(
        document_type="CC",
        document_number="1020304050",
        birth_date=date(1950, 11, 9),  # 76 años
        insured_amount=100_000_000.0,
    )
    with pytest.raises(HTTPException) as exc_info:
        await service.calculate_quote(req)
    assert exc_info.value.status_code == 422
    assert "Debe tener entre 18 y 75 años" in exc_info.value.detail


@pytest.mark.unit
@pytest.mark.asyncio
async def test_quote_rejects_future_birth_date():
    """Valida que una fecha de nacimiento futura sea rechazada con HTTP 422 (RC-07 / TC-16)."""
    service = RatingService(today=lambda: TODAY)
    req = QuoteRequest(
        document_type="CC",
        document_number="1020304050",
        birth_date=date(2027, 1, 1),
        insured_amount=100_000_000.0,
    )
    with pytest.raises(HTTPException) as exc_info:
        await service.calculate_quote(req)
    assert exc_info.value.status_code == 422
    assert "no puede ser una fecha futura" in exc_info.value.detail


@pytest.mark.unit
@pytest.mark.asyncio
async def test_quote_accepts_valid_age():
    """Valida que una edad dentro del rango (36 años) se cotice exitosamente (RC-07 / TC-16)."""
    service = RatingService(today=lambda: TODAY)
    req = QuoteRequest(
        document_type="CC",
        document_number="1020304050",
        birth_date="1990-05-15",
        insured_amount=100_000_000.0,
    )
    resp = await service.calculate_quote(req)
    assert resp.status in ("active", "degraded")
    assert resp.document_number == "1020304050"


@pytest.mark.unit
@pytest.mark.asyncio
async def test_quote_persists_credit_fields():
    """Valida que la entidad financiera y el plazo se guarden con la cotización (RC-08 / TC-17)."""
    service = RatingService(today=lambda: TODAY)
    session = MockAsyncSession()

    # Pre-cargar consentimiento en memoria
    profile_service.register_consent(
        ConsentRequest(
            document_number="1020304050",
            otp_code="839201",
            terms_accepted=True,
        )
    )

    req = QuoteRequest(
        document_type="CC",
        document_number="1020304050",
        birth_date="1990-05-15",
        insured_amount=120_000_000.0,
        financial_entity="BANCOLOMBIA",
        term_months=240,
    )

    quote_resp = await service.create_quote(session=session, request=req, commit=True)
    assert quote_resp.document_number == "1020304050"

    # Verificar que el registro agregado a sesión contiene los campos
    saved_quote = next(obj for obj in session.added if isinstance(obj, QuoteDB))
    assert saved_quote.financial_entity == "BANCOLOMBIA"
    assert saved_quote.term_months == 240
    assert saved_quote.document_number == "1020304050"


@pytest.mark.unit
@pytest.mark.asyncio
async def test_quote_requires_consent_for_advisor():
    """Valida rechazo 422 si un asesor intenta cotizar sin consentimiento informado (RC-06 / TC-08)."""
    profile_service.clear()
    service = RatingService(today=lambda: TODAY)
    session = MockAsyncSession()

    req = QuoteRequest(
        document_type="CC",
        document_number="9999999999",  # Sin consentimiento
        birth_date="1990-05-15",
        insured_amount=80_000_000.0,
    )

    with pytest.raises(HTTPException) as exc_info:
        await service.create_quote(session=session, request=req, partner_id=None)
    assert exc_info.value.status_code == 422
    assert "El consentimiento informado del cliente es obligatorio" in exc_info.value.detail


@pytest.mark.unit
@pytest.mark.asyncio
async def test_open_data_provider_fallback():
    """Valida que el adaptador Open Data degrade suavemente si la URL falla o timeout (RC-25 / TC-15)."""
    provider = OpenDataProvider(base_url="https://invalid.example.test/resource.json", timeout=0.1)
    result = await provider.fetch_indicators("Bogota")
    assert result is None

    # Proveedor de score degrada limpiamente a puntaje neutro
    req = QuoteRequest(
        document_type="CC",
        document_number="1020304050",
        birth_date="1990-05-15",
        insured_amount=50_000_000.0,
    )
    score = await open_finance_score_provider(req, simulated_delay=0.0)
    assert score in (700, 720)
