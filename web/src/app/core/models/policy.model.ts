/** Contrato de emisión del BFF (POST /api/v1/experience/policies) */
import { PlanTier, QuoteTier } from './quote.model';

/** Titular capturado en el paso 1; se guarda con la póliza al emitir (HU-WEB-08). */
export interface PolicyHolder {
  name: string;
  document_type: string;
  email: string;
  phone: string;
}

export type Relationship = 'CONYUGE' | 'HIJO' | 'PADRE' | 'MADRE' | 'HERMANO' | 'OTRO';

/** Beneficiario de la póliza de vida; los porcentajes de todos suman 100 (HU-WEB-15). */
export interface Beneficiary {
  name: string;
  document_type: string;
  document_number: string;
  relationship: Relationship;
  percentage: number;
}

export interface PolicyIssueRequest {
  quote_id: string;
  tier: PlanTier;
  beneficiaries: Beneficiary[];
  holder?: PolicyHolder;
}

export type PolicyStatus = 'ISSUED' | 'ACTIVE' | 'CANCELLED' | 'TERMINATED';

/** Fila de la búsqueda de operaciones: documento enmascarado y sin datos de contacto (HU-WEB-08). */
export interface PolicySearchItem {
  policy_number: string;
  holder_name: string | null;
  holder_document: string;
  plan: PlanTier;
  status: PolicyStatus;
  valid_from: string;
  valid_until: string;
  partner_name: string | null;
}

export interface PolicySearchResponse {
  items: PolicySearchItem[];
  has_more: boolean;
}

/** Titular del detalle: documento, correo y teléfono llegan enmascarados del servidor (HU-WEB-09). */
export interface PolicyDetailHolder {
  name: string | null;
  document_type: string | null;
  document: string;
  email: string | null;
  phone: string | null;
}

export interface PolicyDetailCoverage {
  name: string;
  limit: number;
  deductible: number;
}

export interface PolicyPayment {
  paid_at: string;
  amount: number;
  currency: string;
  method: string;
  status: string;
  receipt_number: string | null;
  decline_reason: string | null;
}

/** Siniestro sin descripción ni ubicación, que pueden traer datos de salud. */
export interface PolicyClaim {
  claim_number: string;
  status: string;
  reported_at: string;
}

export interface PolicyDetail {
  policy_number: string;
  status: PolicyStatus;
  plan: PlanTier;
  valid_from: string;
  valid_until: string;
  partner_name: string | null;
  holder: PolicyDetailHolder;
  insured_amount: number;
  monthly_premium: number;
  annual_premium: number;
  coverages: PolicyDetailCoverage[];
  payments: PolicyPayment[];
  claims: PolicyClaim[];
  version?: number;
  beneficiaries?: MaskedBeneficiary[];
}

/** Beneficiario con el documento enmascarado por el servidor. */
export interface MaskedBeneficiary {
  name: string;
  document_type: string;
  document: string;
  relationship: Relationship;
  percentage: number;
}

export interface EndorsementRequest {
  kind: 'BENEFICIARIES' | 'SUM_INSURED';
  justification: string;
  beneficiaries?: Beneficiary[];
  insured_amount?: number;
}

export interface PolicyOperationResult {
  policy_number: string;
  status: PolicyStatus;
  version: number;
  refund_amount?: number | null;
}

/** Una decisión sobre la póliza, en orden y de solo lectura (HU-WEB-15, RC-19). */
export interface AuditEntry {
  occurred_at: string;
  kind: string;
  actor: string;
  summary: string;
  justification?: string | null;
  amount?: number | null;
  reference?: string | null;
}

export interface PolicyAudit {
  policy_number: string;
  entries: AuditEntry[];
}

/** Enlace prefirmado de la carátula en PDF; vence a los 15 minutos (HU-WEB-16). */
export interface CaratulaLink {
  url: string;
  expires_at: string;
}

export interface PolicyRecord {
  policy_number: string;
  quote_id: string;
  insured_document: string;
  conditions: QuoteTier;
  status: 'ISSUED' | 'ACTIVE';
  issued_at: string;
  valid_until: string;
  pdf_s3_key?: string | null;
}
