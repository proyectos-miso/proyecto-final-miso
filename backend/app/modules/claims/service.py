"""Bandeja, expediente, dictamen y liquidación de siniestros (HU-WEB-15).
Un usuario de un socio solo ve los siniestros de pólizas de su socio; el resto responde como inexistente."""
import hashlib
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Callable, List, Optional

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.storage import DocumentStorage, StorageUnavailableError
from app.modules.claims.models import (
    OPEN_STATUSES,
    ClaimAssignRequest,
    ClaimCoverage,
    ClaimDB,
    ClaimDecisionRequest,
    ClaimDetail,
    ClaimEvent,
    ClaimEventDB,
    ClaimList,
    ClaimListItem,
    ClaimPayout,
    ClaimPhoto,
    ClaimPhotoDB,
    Expert,
    ExpertDB,
)
from app.modules.claims.parametric import ParametricRule, matching_rule
from app.modules.payments.payouts import Payee, payout_service
from app.modules.policy.detail import mask_phone
from app.modules.policy.models import PolicyDB
from app.modules.policy.operations import policy_operations
from app.modules.policy.search import mask_document
from app.modules.policy.service import included_coverages

LIST_LIMIT = 50
PHOTO_LINK_SECONDS = 900
DEATH = "death"
DEFAULT_EXPERTS = [
    ("PER-BOG-01", "Laura Inspección Ruiz", "Bogotá"),
    ("PER-BOG-02", "Andrés Peritaje Gómez", "Bogotá"),
    ("PER-MED-01", "Catalina Ajustes Pérez", "Medellín"),
    ("PER-CAL-01", "Jorge Avalúos Díaz", "Cali"),
    ("PER-BAQ-01", "Mónica Siniestros Vega", "Barranquilla"),
    ("PER-BUC-01", "Felipe Revisión Mora", "Bucaramanga"),
]


class ClaimNotFoundError(Exception):
    pass


class ClaimClosedError(Exception):
    """El siniestro ya tiene dictamen: no admite otro ni otra liquidación (AC-20)."""


class InvalidSettlementError(Exception):
    """Cobertura que la póliza no tiene, valor por encima del amparo o póliza sin vigencia."""


@dataclass
class NewPhoto:
    """Foto que entrega HU-MOV-09 al recibir el reporte, ya guardada en S3."""
    photo_id: str
    position: int
    s3_key: str
    sha256: str
    latitude: float
    longitude: float
    accuracy_m: float
    mock_location: bool
    captured_at: datetime
    trusted_time_source: Optional[str] = None


def payees_of(policy: PolicyDB) -> List[Payee]:
    """Beneficiarios de la póliza; las pólizas anteriores a HU-WEB-15 sin beneficiarios le pagan al titular."""
    beneficiaries = [b for b in policy.beneficiaries or [] if b.get("document_number") and b.get("percentage")]
    if not beneficiaries:
        return [Payee(policy.holder_name or "Titular de la póliza", policy.insured_document, 100)]
    return [Payee(b["name"], str(b["document_number"]), float(b["percentage"])) for b in beneficiaries]


class ClaimService:
    def __init__(self, now: Callable[[], datetime] = lambda: datetime.now(timezone.utc)):
        self.now = now

    # -- Consulta -------------------------------------------------------------------------------

    def scoped(self, stmt, partner_id: Optional[str]):
        stmt = stmt.join(PolicyDB, PolicyDB.policy_number == ClaimDB.policy_number)
        return stmt.where(PolicyDB.partner_id == partner_id) if partner_id is not None else stmt

    async def list_claims(self, session: AsyncSession, status: Optional[str], partner_id: Optional[str]) -> ClaimList:
        """Los más recientes primero, a lo sumo 50 (AC-01)."""
        stmt = self.scoped(select(ClaimDB), partner_id)
        if status:
            stmt = stmt.where(ClaimDB.status == status)
        rows = list(await session.scalars(
            stmt.order_by(ClaimDB.reported_at.desc(), ClaimDB.claim_number.desc()).limit(LIST_LIMIT + 1)
        ))
        items = [
            ClaimListItem(
                claim_number=c.claim_number, policy_number=c.policy_number, reported_at=c.reported_at,
                status=c.status, origin=c.origin or "APP", assigned_to=c.assigned_to,
            )
            for c in rows[:LIST_LIMIT]
        ]
        return ClaimList(items=items, has_more=len(rows) > LIST_LIMIT)

    async def find(
        self, session: AsyncSession, claim_number: str, partner_id: Optional[str], lock: bool = False
    ) -> tuple:
        stmt = self.scoped(select(ClaimDB, PolicyDB), partner_id).where(ClaimDB.claim_number == claim_number)
        if lock:
            stmt = stmt.with_for_update()
        row = (await session.execute(stmt)).first()
        if row is None:
            raise ClaimNotFoundError()
        return row

    async def detail(
        self, session: AsyncSession, storage: DocumentStorage, claim_number: str, partner_id: Optional[str]
    ) -> ClaimDetail:
        """Expediente con el asegurado enmascarado y cada foto verificada contra su huella (AC-02, AC-03)."""
        claim, policy = await self.find(session, claim_number, partner_id)
        coverages = [ClaimCoverage(coverage_id=c["id"], name=c["name"], limit=c["limit"])
                     for c in included_coverages(policy.conditions)]
        photos = await session.scalars(
            select(ClaimPhotoDB).where(ClaimPhotoDB.claim_number == claim_number).order_by(ClaimPhotoDB.position)
        )
        events = await session.scalars(
            select(ClaimEventDB).where(ClaimEventDB.claim_number == claim_number).order_by(ClaimEventDB.id)
        )
        expert = await session.get(ExpertDB, claim.expert_id) if claim.expert_id else None
        payouts = await payout_service.for_reference(session, claim_number, "INDEMNITY")
        return ClaimDetail(
            claim_number=claim.claim_number,
            status=claim.status,
            origin=claim.origin or "APP",
            reported_at=claim.reported_at,
            description=claim.description,
            parametric_event=claim.parametric_event,
            policy_number=policy.policy_number,
            policy_status=policy.status,
            plan=policy.conditions["tier"],
            insured_name=policy.holder_name,
            insured_document=mask_document(policy.insured_document),
            insured_phone=mask_phone(policy.holder_phone) if policy.holder_phone else None,
            coverage=next((c for c in coverages if c.coverage_id == claim.coverage_id), None),
            coverages=coverages,
            assigned_to=claim.assigned_to,
            expert=Expert(expert_id=expert.expert_id, name=expert.name, city=expert.city) if expert else None,
            settled_amount=claim.settled_amount,
            photos=[await self.verified(storage, photo) for photo in photos],
            events=[
                ClaimEvent(kind=e.kind, actor=e.actor, justification=e.justification, amount=e.amount,
                           created_at=e.created_at)
                for e in events
            ],
            payouts=[
                ClaimPayout(beneficiary_name=p.beneficiary_name, beneficiary_document=mask_document(p.beneficiary_document),
                            percentage=p.percentage, amount=p.amount, status=p.status, receipt_number=p.receipt_number)
                for p in payouts
            ],
        )

    async def verified(self, storage: DocumentStorage, photo: ClaimPhotoDB) -> ClaimPhoto:
        """Compara la huella de los bytes guardados con la que registró el teléfono; sin S3 queda sin verificar."""
        try:
            content = await storage.get_bytes(photo.s3_key)
            url = await storage.view_url(photo.s3_key, "image/jpeg", PHOTO_LINK_SECONDS)
            integrity = "INTACT" if hashlib.sha256(content).hexdigest() == photo.sha256.lower() else "ALTERED"
        except StorageUnavailableError:
            url, integrity = None, "UNVERIFIED"
        return ClaimPhoto(
            photo_id=photo.photo_id, position=photo.position, url=url, latitude=photo.latitude,
            longitude=photo.longitude, accuracy_m=photo.accuracy_m, mock_location=photo.mock_location,
            captured_at=photo.captured_at, trusted_time=photo.trusted_time_source is not None, integrity=integrity,
        )

    async def seed_experts(self, session: AsyncSession) -> None:
        """Catálogo inicial de peritos por ciudad; no se repite si ya existe."""
        if await session.scalar(select(ExpertDB).limit(1)) is not None:
            return
        session.add_all([ExpertDB(expert_id=i, name=n, city=c) for i, n, c in DEFAULT_EXPERTS])
        await session.commit()

    async def experts(self, session: AsyncSession, city: Optional[str]) -> List[Expert]:
        stmt = select(ExpertDB).order_by(ExpertDB.city, ExpertDB.name)
        if city:
            stmt = stmt.where(ExpertDB.city == city)
        return [Expert(expert_id=e.expert_id, name=e.name, city=e.city) for e in await session.scalars(stmt)]

    # -- Decisiones -----------------------------------------------------------------------------

    def event(self, session: AsyncSession, claim: ClaimDB, kind: str, actor: str, **fields) -> None:
        session.add(ClaimEventDB(claim_number=claim.claim_number, kind=kind, actor=actor, created_at=self.now(), **fields))

    async def assign(
        self, session: AsyncSession, claim_number: str, request: ClaimAssignRequest, actor: str,
        partner_id: Optional[str],
    ) -> None:
        """El analista toma el caso y, si hace falta inspección, asigna un perito del catálogo (AC-07)."""
        claim, _ = await self.find(session, claim_number, partner_id, lock=True)
        if claim.status not in OPEN_STATUSES:
            raise ClaimClosedError()
        if claim.assigned_to != actor:
            claim.assigned_to, claim.status = actor, "IN_REVIEW"
            self.event(session, claim, "ASSIGNED", actor)
        if request.expert_id and request.expert_id != claim.expert_id:
            expert = await session.get(ExpertDB, request.expert_id)
            if expert is None:
                raise InvalidSettlementError("El perito no existe.")
            claim.expert_id = expert.expert_id
            self.event(session, claim, "EXPERT_ASSIGNED", actor, detail={"expert": expert.name, "city": expert.city})
        await session.commit()

    async def decide(
        self, session: AsyncSession, claim_number: str, request: ClaimDecisionRequest, actor: str,
        partner_id: Optional[str],
    ) -> None:
        """Rechaza o liquida con justificación; una vez decidido no se cambia (AC-04, AC-05, AC-06, AC-20)."""
        claim, policy = await self.find(session, claim_number, partner_id, lock=True)
        if claim.status not in OPEN_STATUSES:
            raise ClaimClosedError()
        if request.decision == "REJECT":
            claim.status = "REJECTED"
            claim.assigned_to = claim.assigned_to or actor
            self.event(session, claim, "REJECTED", actor, justification=request.justification)
            await session.commit()
            return
        if request.amount is None:
            raise InvalidSettlementError("Registre el valor a pagar.")
        await self.settle(session, claim, policy, request.coverage_id or claim.coverage_id, request.amount,
                          actor, request.justification)
        await session.commit()

    async def settle(
        self, session: AsyncSession, claim: ClaimDB, policy: PolicyDB, coverage_id: Optional[str], amount: float,
        actor: str, justification: str,
    ) -> None:
        """Liquida dentro del amparo, ordena el pago a los beneficiarios y, si es de muerte, termina la póliza."""
        if policy.status != "ACTIVE":
            raise InvalidSettlementError("La póliza no estaba vigente y al día: no se puede liquidar.")
        coverage = next((c for c in included_coverages(policy.conditions) if c["id"] == coverage_id), None)
        if coverage is None:
            raise InvalidSettlementError("Elija una cobertura incluida en la póliza.")
        if amount > coverage["limit"]:
            raise InvalidSettlementError("El valor supera la suma asegurada del amparo.")
        claim.status, claim.coverage_id, claim.settled_amount = "SETTLED", coverage_id, amount
        claim.assigned_to = claim.assigned_to or actor
        self.event(session, claim, "SETTLED", actor, justification=justification, amount=amount,
                   detail={"coverage": coverage["name"]})
        payouts = await payout_service.create(
            session, claim.claim_number, "INDEMNITY", policy.policy_number, amount, payees_of(policy)
        )
        self.mark_paid(session, claim, payouts)
        if coverage_id == DEATH:
            policy_operations.terminate(session, policy, claim.claim_number, actor)

    def mark_paid(self, session: AsyncSession, claim: ClaimDB, payouts: list) -> None:
        if payouts and all(p.status == "APPROVED" for p in payouts):
            claim.status = "PAID"
            self.event(session, claim, "PAID", "Pasarela de pagos", amount=sum(p.amount for p in payouts))

    async def retry_payouts(self, session: AsyncSession, claim_number: str, partner_id: Optional[str]) -> None:
        """Vuelve a intentar los pagos rechazados; los aprobados no se repiten (AC-08)."""
        claim, _ = await self.find(session, claim_number, partner_id, lock=True)
        if claim.status != "SETTLED":
            raise ClaimClosedError()
        payouts = await payout_service.for_reference(session, claim_number, "INDEMNITY")
        await payout_service.execute(session, payouts)
        self.mark_paid(session, claim, payouts)
        await session.commit()

    # -- Registro (HU-MOV-09 y HU-MOV-10) -------------------------------------------------------

    async def register_claim(
        self, session: AsyncSession, claim_number: str, policy_number: str, reported_at: datetime,
        description: Optional[str] = None, coverage_id: Optional[str] = None, photos: Optional[List[NewPhoto]] = None,
        origin: str = "APP", parametric_event: Optional[dict] = None,
    ) -> ClaimDB:
        """Punto de entrada para crear un siniestro. Uno paramétrico que cumple su regla se liquida solo (AC-12)."""
        claim = ClaimDB(
            claim_number=claim_number, policy_number=policy_number, status="REPORTED", reported_at=reported_at,
            origin=origin, coverage_id=coverage_id, description=description, parametric_event=parametric_event,
        )
        session.add(claim)
        for photo in photos or []:
            session.add(ClaimPhotoDB(claim_number=claim_number, **photo.__dict__))
        self.event(session, claim, "REPORTED", "App del asegurado" if origin == "APP" else "Oráculo de eventos",
                   detail=parametric_event)
        await session.flush()
        rule = matching_rule(parametric_event) if origin == "PARAMETRIC" else None
        if rule:
            await self.settle_parametric(session, claim, rule)
        await session.commit()
        return claim

    async def settle_parametric(self, session: AsyncSession, claim: ClaimDB, rule: ParametricRule) -> None:
        policy = await session.scalar(
            select(PolicyDB).where(PolicyDB.policy_number == claim.policy_number).with_for_update()
        )
        coverage = next((c for c in included_coverages(policy.conditions) if c["id"] == rule.coverage_id), None)
        if policy.status != "ACTIVE" or coverage is None:
            return
        amount = round(coverage["limit"] * rule.payout_ratio)
        await self.settle(session, claim, policy, rule.coverage_id, amount, f"Regla paramétrica {rule.code}",
                          rule.description)


claim_service = ClaimService()
