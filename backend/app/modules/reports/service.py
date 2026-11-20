"""Reporte mensual al regulador y al reasegurador en CSV y PDF (HU-WEB-15, RC-22).
Solo cifras agregadas: ningún dato personal sale en el reporte. La cesión es un contrato cuota parte simulado."""
import csv
import io
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from typing import List, Tuple

from fpdf import FPDF
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.modules.claims.models import ClaimDB, ClaimEventDB
from app.modules.payments.models import PaymentTransactionDB, PayoutDB
from app.modules.policy.document import cop, latin1
from app.modules.policy.models import PolicyDB, PolicyEventDB

# Porción de primas y siniestros que se cede al reasegurador (contrato cuota parte simulado).
REINSURANCE_QUOTA_SHARE = 0.40
COLOMBIA = timezone(timedelta(hours=-5))


@dataclass
class MonthlyReport:
    period: str
    rows: List[Tuple[str, str, float]]


def month_bounds(period: str) -> Tuple[datetime, datetime]:
    """El mes en hora de Colombia: del día 1 a las 00:00 al día 1 del mes siguiente."""
    start = datetime.strptime(period, "%Y-%m").replace(tzinfo=COLOMBIA)
    end = (start + timedelta(days=32)).replace(day=1)
    return start, end


async def build_report(session: AsyncSession, period: str) -> MonthlyReport:
    start, end = month_bounds(period)

    async def total(stmt) -> float:
        return float(await session.scalar(stmt) or 0)

    issued = await total(select(func.count()).select_from(PolicyDB).where(PolicyDB.issued_at >= start, PolicyDB.issued_at < end))
    cancelled = await total(select(func.count()).select_from(PolicyEventDB).where(
        PolicyEventDB.kind == "CANCELLED", PolicyEventDB.created_at >= start, PolicyEventDB.created_at < end))
    premiums = await total(select(func.sum(PaymentTransactionDB.amount)).where(
        PaymentTransactionDB.status == "APPROVED", PaymentTransactionDB.created_at >= start,
        PaymentTransactionDB.created_at < end))
    refunds = await total(select(func.sum(PayoutDB.amount)).where(
        PayoutDB.kind == "REFUND", PayoutDB.status == "APPROVED", PayoutDB.created_at >= start, PayoutDB.created_at < end))
    reported = await total(select(func.count()).select_from(ClaimDB).where(
        ClaimDB.reported_at >= start, ClaimDB.reported_at < end))
    settled = await total(select(func.sum(ClaimEventDB.amount)).where(
        ClaimEventDB.kind == "SETTLED", ClaimEventDB.created_at >= start, ClaimEventDB.created_at < end))
    rejected = await total(select(func.count()).select_from(ClaimEventDB).where(
        ClaimEventDB.kind == "REJECTED", ClaimEventDB.created_at >= start, ClaimEventDB.created_at < end))
    paid = await total(select(func.sum(PayoutDB.amount)).where(
        PayoutDB.kind == "INDEMNITY", PayoutDB.status == "APPROVED", PayoutDB.created_at >= start, PayoutDB.created_at < end))
    net_premiums = premiums - refunds

    rows = [
        ("Regulador", "Pólizas emitidas", issued),
        ("Regulador", "Pólizas canceladas", cancelled),
        ("Regulador", "Primas cobradas (COP)", premiums),
        ("Regulador", "Primas devueltas por cancelación (COP)", refunds),
        ("Regulador", "Primas netas (COP)", net_premiums),
        ("Regulador", "Siniestros reportados", reported),
        ("Regulador", "Siniestros rechazados", rejected),
        ("Regulador", "Siniestros liquidados (COP)", settled),
        ("Regulador", "Indemnizaciones pagadas (COP)", paid),
        ("Reasegurador", "Porcentaje cedido (cuota parte)", REINSURANCE_QUOTA_SHARE * 100),
        ("Reasegurador", "Primas cedidas (COP)", round(net_premiums * REINSURANCE_QUOTA_SHARE)),
        ("Reasegurador", "Siniestros liquidados recuperables (COP)", round(settled * REINSURANCE_QUOTA_SHARE)),
    ]
    return MonthlyReport(period=period, rows=rows)


def to_csv(report: MonthlyReport) -> bytes:
    """Con BOM para que Excel abra bien las tildes."""
    buffer = io.StringIO()
    writer = csv.writer(buffer)
    writer.writerow(["periodo", "destinatario", "indicador", "valor"])
    for audience, name, value in report.rows:
        writer.writerow([report.period, audience, name, f"{value:.0f}"])
    return ("﻿" + buffer.getvalue()).encode("utf-8")


def to_pdf(report: MonthlyReport, generated_at: datetime) -> bytes:
    pdf = FPDF(format="A4")
    pdf.set_title(f"Reporte mensual {report.period}")
    pdf.set_author("Solventa Seguros Digitales")
    pdf.add_page()
    pdf.set_font("helvetica", "B", 16)
    pdf.multi_cell(0, 9, latin1(f"Reporte mensual {report.period}"), new_x="LMARGIN", new_y="NEXT")
    pdf.set_font("helvetica", "", 10)
    pdf.multi_cell(0, 6, latin1("Solventa Seguros Digitales · Regulador y reasegurador"), new_x="LMARGIN", new_y="NEXT")
    for audience in ("Regulador", "Reasegurador"):
        pdf.ln(3)
        pdf.set_font("helvetica", "B", 12)
        pdf.multi_cell(0, 8, latin1(audience), new_x="LMARGIN", new_y="NEXT")
        pdf.set_font("helvetica", "", 10)
        for row_audience, name, value in report.rows:
            if row_audience == audience:
                shown = cop(value) if "(COP)" in name else f"{value:.0f}"
                pdf.multi_cell(0, 6, latin1(f"{name.replace(' (COP)', '')}: {shown}"), new_x="LMARGIN", new_y="NEXT")
    pdf.ln(4)
    pdf.set_font("helvetica", "I", 8)
    pdf.multi_cell(0, 5, latin1(f"Generado el {generated_at.astimezone(COLOMBIA):%d/%m/%Y %H:%M} (hora de Colombia)."),
                   new_x="LMARGIN", new_y="NEXT")
    return bytes(pdf.output())
