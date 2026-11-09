"""Pruebas unitarias de gestión de cuotas de socios B2B (HU-WEB-12)."""
from datetime import datetime, timedelta, timezone
import pytest
from app.modules.rating.models import PartnerDB, PartnerStatus
from app.modules.rating.service import PartnerQuotaExceededError, rating_service


@pytest.mark.unit
@pytest.mark.asyncio
async def test_quota_window_auto_reset(test_db_session):
    """TC-05 (HU-WEB-12.1): Reinicio automático de ventana cuando la hora actual supera quota_reset_at."""
    t0 = datetime(2026, 11, 1, 10, 0, 0, tzinfo=timezone.utc)
    partner = PartnerDB(
        partner_id="socio-test-reset",
        client_id="socio_test_reset",
        client_secret_hash="hash",
        name="Socio Test Reset",
        status=PartnerStatus.ACTIVE.value,
        quota_limit=5,
        quota_used=5,  # Cuota agotada
        quota_window_seconds=3600,
        quota_reset_at=t0,
    )
    test_db_session.add(partner)

    # 1. Antes de expirar la ventana (t0 - 10s), debe lanzar PartnerQuotaExceededError con Retry-After correspondiente
    t_before = t0 - timedelta(seconds=10)
    with pytest.raises(PartnerQuotaExceededError) as exc_info:
        await rating_service.consume_partner_quota(
            session=test_db_session,
            partner_id="socio-test-reset",
            now=t_before,
        )
    assert exc_info.value.partner_id == "socio-test-reset"
    assert exc_info.value.retry_after == 10
    assert exc_info.value.quota_used == 5
    assert exc_info.value.quota_limit == 5

    # 2. Tras vencer la ventana (t0 + 1s), debe reiniciar quota_used a 1 y extender la ventana
    t_after = t0 + timedelta(seconds=1)
    status = await rating_service.consume_partner_quota(
        session=test_db_session,
        partner_id="socio-test-reset",
        now=t_after,
    )
    assert status.quota_used == 1
    assert status.quota_remaining == 4
    assert status.quota_limit == 5
    assert status.reset_at == t_after + timedelta(seconds=3600)


@pytest.mark.unit
@pytest.mark.asyncio
async def test_consume_partner_quota_and_status(test_db_session):
    """Verifica consumo progresivo y consulta idempotente de estado sin alterar el contador."""
    t0 = datetime(2026, 11, 1, 12, 0, 0, tzinfo=timezone.utc)
    partner = PartnerDB(
        partner_id="socio-test-progress",
        client_id="socio_test_progress",
        client_secret_hash="hash",
        name="Socio Test Progress",
        status=PartnerStatus.ACTIVE.value,
        quota_limit=10,
        quota_used=0,
        quota_window_seconds=1800,
        quota_reset_at=None,
    )
    test_db_session.add(partner)

    # Primera solicitud: inicializa la ventana y consume 1
    st1 = await rating_service.consume_partner_quota(
        session=test_db_session,
        partner_id="socio-test-progress",
        now=t0,
    )
    assert st1.quota_used == 1
    assert st1.quota_remaining == 9
    assert st1.reset_at == t0 + timedelta(seconds=1800)

    # Consulta de estado: no debe alterar el contador
    st_read = await rating_service.get_partner_quota_status(
        session=test_db_session,
        partner_id="socio-test-progress",
        now=t0 + timedelta(seconds=100),
    )
    assert st_read.quota_used == 1
    assert st_read.quota_remaining == 9
    assert st_read.retry_after == 0
