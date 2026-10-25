/** Contrato de cotización del BFF (POST /api/v1/experience/quotes) */
export type PlanTier = 'basic' | 'standard' | 'premium';

export interface QuoteRequest {
  document_type: string;
  document_number: string;
  birth_date: string;
  insured_amount: number;
  city?: string;
  occupation_risk?: number;
}

export interface PremiumFactor {
  code: string;
  label: string;
  multiplier: number;
  amount: number;
}

export interface Coverage {
  id: string;
  name: string;
  limit: number;
  deductible: number;
}

export interface QuoteTier {
  tier: PlanTier;
  monthly_premium: number;
  annual_premium: number;
  coverages: Coverage[];
  breakdown: PremiumFactor[];
}

/** Rechazo de suscripción (HU-WEB-07): motivo en lenguaje de negocio y suma máxima aprobable en COP. */
export interface UnderwritingDecision {
  decision: 'REVIEW_REQUIRED';
  rule_id: string;
  reason: string;
  max_approvable_amount: number;
}

export interface QuoteResponse {
  quote_id: string;
  document_number: string;
  status: 'active' | 'degraded' | 'review_required';
  degradation_reason?: 'TIMEOUT_EXCEEDED' | null;
  score_applied: number;
  tiers: QuoteTier[];
  created_at: string;
  expires_at: string;
  underwriting?: UnderwritingDecision | null;
}
