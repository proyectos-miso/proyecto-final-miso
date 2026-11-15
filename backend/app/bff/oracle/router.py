"""Eventos del oráculo: solo con firma HMAC válida y reciente; el mismo evento no crea siniestros dos veces (RC-26)."""
from fastapi import APIRouter, Depends, Header, HTTPException, Request, Response, status
from pydantic import ValidationError
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db_session
from app.modules.claims.oracle import InvalidSignatureError, OracleDisabledError, OracleEvent, OracleResult, oracle

router = APIRouter(prefix="/oracle", tags=["Oráculo de eventos"])

MAX_BODY = 4_096


@router.post("/events", response_model=OracleResult, status_code=status.HTTP_201_CREATED)
async def receive_event(
    request: Request,
    response: Response,
    timestamp: str = Header(..., alias="X-Oracle-Timestamp", max_length=20),
    signature: str = Header(..., alias="X-Oracle-Signature", max_length=128),
    db: AsyncSession = Depends(get_db_session),
):
    """Crea un siniestro paramétrico con radicado para cada póliza afectada y la regla lo liquida (AC-05, AC-06)."""
    body = await request.body()
    if len(body) > MAX_BODY:
        raise HTTPException(status_code=status.HTTP_413_CONTENT_TOO_LARGE, detail="El evento es demasiado grande.")
    try:
        oracle.verify(timestamp, signature, body)
    except OracleDisabledError:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="El canal del oráculo no está habilitado.")
    except InvalidSignatureError:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Firma del evento inválida o vencida.")
    try:
        event = OracleEvent.model_validate_json(body)
    except ValidationError:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_CONTENT, detail="El evento no tiene el formato esperado.")
    result = await oracle.receive(db, event)
    if result.duplicate:
        response.status_code = status.HTTP_200_OK
    return result
