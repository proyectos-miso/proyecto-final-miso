/** Contrato de emisión del BFF (POST /api/v1/experience/policies) */
import { PlanTier, QuoteTier } from './quote.model';

/** Titular capturado en el paso 1; se guarda con la póliza al emitir (HU-WEB-08). */
export interface PolicyHolder {
  name: string;
  document_type: string;
  email: string;
  phone: string;
}

export interface PolicyIssueRequest {
  quote_id: string;
  tier: PlanTier;
  holder?: PolicyHolder;
}

export type PolicyStatus = 'ISSUED' | 'ACTIVE' | 'CANCELLED';

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
