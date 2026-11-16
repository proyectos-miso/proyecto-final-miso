"""Prestadores cercanos y solicitudes de asistencia del asegurado (mejora FE-MOV-07).

Las coordenadas del asegurado solo sirven para ordenar y asignar dentro de la petición; no se guardan ni salen en
eventos ni registros. La distancia se calcula con haversine en memoria porque el catálogo es pequeño.
"""
import math
import uuid
from datetime import datetime, timedelta, timezone
from typing import Callable, List, Optional, Tuple

from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.outbox import OutboxMessageDB
from app.modules.assistance.models import (
    AssistanceReceipt, AssistanceRequest, AssistanceRequestDB, Provider, ProviderDB,
)
from app.modules.policy.models import PolicyDB

MAX_PROVIDERS = 20
SPEED_KMH = 30
DISPATCH_MINUTES = 15
ETA_WITHOUT_LOCATION = 45
MAX_REQUESTS_PER_HOUR = 5
EARTH_RADIUS_KM = 6371.0

# Catálogo de demostración: nombres, direcciones y teléfonos ficticios (rango 555), solo fuera de producción.
DEFAULT_PROVIDERS = [
    ("PRV-BOG-01", "Grúas Sabana Norte", "GRUA", ["GRUA", "ASISTENCIA_VIAL"], "BOGOTA", "Calle 100 # 15-20", "+576015550101", 4.6867, -74.0480),
    ("PRV-BOG-02", "Taller Ruedas del Centro", "TALLER", ["MECANICA"], "BOGOTA", "Carrera 7 # 22-10", "+576015550102", 4.6097, -74.0705),
    ("PRV-BOG-03", "Clínica Prueba Chapinero", "CLINICA", ["ATENCION_MEDICA"], "BOGOTA", "Calle 63 # 9-40", "+576015550103", 4.6486, -74.0628),
    ("PRV-BOG-04", "Cerrajería Llave Maestra", "CERRAJERIA", ["CERRAJERIA"], "BOGOTA", "Avenida 68 # 40-12", "+576015550104", 4.6370, -74.0960),
    ("PRV-BOG-05", "Plomería Tubo Azul", "PLOMERIA", ["PLOMERIA"], "BOGOTA", "Calle 13 # 68-30", "+576015550105", 4.6290, -74.1210),
    ("PRV-MED-01", "Grúas Valle de Aburrá", "GRUA", ["GRUA", "ASISTENCIA_VIAL"], "MEDELLIN", "Calle 10 # 43-50", "+576045550201", 6.2088, -75.5677),
    ("PRV-MED-02", "Taller Montaña Motor", "TALLER", ["MECANICA"], "MEDELLIN", "Carrera 52 # 49-15", "+576045550202", 6.2476, -75.5658),
    ("PRV-MED-03", "Clínica Prueba Laureles", "CLINICA", ["ATENCION_MEDICA"], "MEDELLIN", "Circular 1 # 70-25", "+576045550203", 6.2450, -75.5900),
    ("PRV-MED-04", "Plomería y Cerrajería El Poblado", "PLOMERIA", ["PLOMERIA", "CERRAJERIA"], "MEDELLIN", "Carrera 43A # 1-50", "+576045550204", 6.2010, -75.5720),
    ("PRV-CAL-01", "Asistencia Vial Pacífico", "ASISTENCIA_VIAL", ["ASISTENCIA_VIAL", "GRUA"], "CALI", "Calle 5 # 38-20", "+576025550301", 3.4225, -76.5440),
    ("PRV-CAL-02", "Taller Salsa Motor", "TALLER", ["MECANICA"], "CALI", "Avenida 6N # 23-40", "+576025550302", 3.4600, -76.5300),
    ("PRV-CAL-03", "Clínica Prueba San Fernando", "CLINICA", ["ATENCION_MEDICA"], "CALI", "Calle 5B # 36-10", "+576025550303", 3.4290, -76.5410),
    ("PRV-BAQ-01", "Grúas Costa Caribe", "GRUA", ["GRUA", "ASISTENCIA_VIAL"], "BARRANQUILLA", "Calle 72 # 46-30", "+576055550401", 10.9990, -74.8060),
    ("PRV-BAQ-02", "Cerrajería Brisa", "CERRAJERIA", ["CERRAJERIA"], "BARRANQUILLA", "Carrera 53 # 80-15", "+576055550402", 11.0050, -74.8100),
    ("PRV-BAQ-03", "Clínica Prueba del Río", "CLINICA", ["ATENCION_MEDICA"], "BARRANQUILLA", "Calle 84 # 50-20", "+576055550403", 11.0080, -74.8150),
    ("PRV-BGA-01", "Asistencia Vial Chicamocha", "ASISTENCIA_VIAL", ["ASISTENCIA_VIAL", "GRUA", "MECANICA"], "BUCARAMANGA", "Carrera 27 # 36-14", "+576075550501", 7.1190, -73.1220),
    ("PRV-BGA-02", "Plomería Ciudad Bonita", "PLOMERIA", ["PLOMERIA"], "BUCARAMANGA", "Calle 45 # 30-12", "+576075550502", 7.1150, -73.1150),
    ("PRV-BGA-03", "Clínica Prueba Cabecera", "CLINICA", ["ATENCION_MEDICA"], "BUCARAMANGA", "Carrera 35 # 48-20", "+576075550503", 7.1130, -73.1080),
]


class PolicyNotAvailableError(Exception):
    pass


class NoProviderError(Exception):
    pass


class TooManyRequestsError(Exception):
    pass


def haversine_km(lat1: float, lng1: float, lat2: float, lng2: float) -> float:
    p1, p2 = math.radians(lat1), math.radians(lat2)
    a = math.sin((p2 - p1) / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(math.radians(lng2 - lng1) / 2) ** 2
    return 2 * EARTH_RADIUS_KM * math.asin(math.sqrt(a))


def provider_view(row: ProviderDB, distance_km: Optional[float] = None) -> Provider:
    return Provider(
        provider_id=row.provider_id, name=row.name, kind=row.kind, services=list(row.services), city_code=row.city_code,
        address=row.address, phone=row.phone, lat=row.lat, lng=row.lng,
        distance_km=None if distance_km is None else round(distance_km, 1),
    )


def eta_minutes(distance_km: Optional[float]) -> int:
    """Distancia a 30 km/h más 15 minutos de despacho; sin ubicación, 45 minutos."""
    if distance_km is None:
        return ETA_WITHOUT_LOCATION
    return math.ceil(distance_km / SPEED_KMH * 60) + DISPATCH_MINUTES


class AssistanceService:
    def __init__(self, now: Callable[[], datetime] = lambda: datetime.now(timezone.utc)):
        self.now = now

    async def seed_providers(self, session: AsyncSession) -> None:
        """Catálogo ficticio de prestadores; no se repite si ya existe. Requiere las ciudades sembradas."""
        if await session.scalar(select(ProviderDB).limit(1)) is not None:
            return
        session.add_all([
            ProviderDB(provider_id=i, name=n, kind=k, services=s, city_code=c, address=a, phone=p, lat=la, lng=ln, active=True)
            for i, n, k, s, c, a, p, la, ln in DEFAULT_PROVIDERS
        ])
        await session.commit()

    async def nearby(
        self, session: AsyncSession, lat: Optional[float], lng: Optional[float], city: Optional[str],
        service: Optional[str], limit: int = MAX_PROVIDERS,
    ) -> List[Tuple[ProviderDB, Optional[float]]]:
        """Por distancia si hay coordenadas; si no, los de la ciudad por nombre. Solo activos y con el servicio."""
        stmt = select(ProviderDB).where(ProviderDB.active.is_(True))
        if city:
            stmt = stmt.where(ProviderDB.city_code == city)
        rows = [p for p in await session.scalars(stmt.order_by(ProviderDB.name)) if service is None or service in p.services]
        if lat is None or lng is None:
            return [(p, None) for p in rows[:limit]]
        ranked = sorted(((p, haversine_km(lat, lng, p.lat, p.lng)) for p in rows), key=lambda pair: pair[1])
        return ranked[:limit]

    async def request(
        self, session: AsyncSession, user_id: str, document_number: str, request: AssistanceRequest
    ) -> AssistanceReceipt:
        """Registra la solicitud con el prestador activo más cercano que ofrece el servicio; no hace commit."""
        now = self.now()
        policy = await session.scalar(select(PolicyDB).where(
            PolicyDB.policy_number == request.policy_number,
            PolicyDB.insured_document == document_number,
            PolicyDB.status == "ACTIVE",
            PolicyDB.valid_until > now,
        ))
        if policy is None:
            raise PolicyNotAvailableError()
        recent = await session.scalar(select(func.count()).select_from(AssistanceRequestDB).where(
            AssistanceRequestDB.user_id == user_id, AssistanceRequestDB.created_at > now - timedelta(hours=1)
        ))
        if recent >= MAX_REQUESTS_PER_HOUR:
            raise TooManyRequestsError()
        city = None if request.lat is not None else request.city
        candidates = await self.nearby(session, request.lat, request.lng, city, request.service, limit=1)
        if not candidates:
            raise NoProviderError()
        provider, distance = candidates[0]
        row = AssistanceRequestDB(
            request_id=str(uuid.uuid4()), user_id=user_id, policy_number=policy.policy_number, service=request.service,
            provider_id=provider.provider_id, city_code=provider.city_code, description=request.description,
            distance_km=None if distance is None else round(distance, 1), eta_minutes=eta_minutes(distance),
            status="ASSIGNED", created_at=now,
        )
        session.add(row)
        # Solo identificadores: ni ubicación, ni descripción, ni datos del asegurado.
        session.add(OutboxMessageDB(aggregate_id=row.request_id, event_type="AssistanceRequested", payload={
            "request_id": row.request_id, "provider_id": provider.provider_id, "service": request.service,
        }))
        return self.receipt(row, provider)

    async def requests_of(self, session: AsyncSession, user_id: str, limit: int = 50) -> List[AssistanceReceipt]:
        rows = await session.execute(
            select(AssistanceRequestDB, ProviderDB)
            .join(ProviderDB, ProviderDB.provider_id == AssistanceRequestDB.provider_id)
            .where(AssistanceRequestDB.user_id == user_id)
            .order_by(AssistanceRequestDB.created_at.desc())
            .limit(limit)
        )
        return [self.receipt(request, provider) for request, provider in rows.all()]

    @staticmethod
    def receipt(row: AssistanceRequestDB, provider: ProviderDB) -> AssistanceReceipt:
        return AssistanceReceipt(
            request_id=row.request_id, policy_number=row.policy_number, service=row.service, status=row.status,
            provider=provider_view(provider, row.distance_km), eta_minutes=row.eta_minutes, created_at=row.created_at,
        )


assistance_service = AssistanceService()
