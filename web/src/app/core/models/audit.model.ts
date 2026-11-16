/** Contrato de la auditoría de suscripción del portal de Solventa (FE-WEB-08). */
export type DecisionKind = 'quote' | 'rejection';

export interface PremiumFactor {
  code: string;
  label: string;
  multiplier: number;
  amount: number;
}

/**
 * Un rechazo solo trae el documento y el monto solicitado: no guarda el puntaje ni la fecha de nacimiento (HU-WEB-07).
 * Con el consentimiento revocado, una cotización no trae ninguna entrada y un rechazo solo el documento en null (RISK-11).
 */
export interface DecisionInputs {
  document_masked?: string | null;
  insured_amount?: number | null;
  birth_date?: string | null;
  occupation_risk?: number | null;
  score?: number | null;
}

/** Con el consentimiento revocado, una cobertura solo trae su id y su nombre. */
export interface Coverage {
  id: string;
  name: string;
  limit?: number;
  deductible?: number;
}

/**
 * Con el consentimiento revocado, un plan solo trae su nombre y sus coberturas: sin primas ni desglose, porque
 * los montos dejan deducir la franja de edad y la del buró (RISK-11).
 */
export interface TierOutcome {
  tier: string;
  monthly_premium?: number;
  annual_premium?: number | null;
  coverages?: Coverage[];
  breakdown?: PremiumFactor[];
}

/** La regla, el motivo y el cupo solo llegan en un rechazo con el consentimiento vigente: el cupo delata la franja del buró o la edad. */
export interface DecisionOutcome {
  status: string;
  degradation_reason: string | null;
  tiers: TierOutcome[];
  rule_id?: string | null;
  reason?: string | null;
  max_approvable_amount?: number | null;
}

export interface DecisionDetail {
  decision_id: string;
  kind: DecisionKind;
  created_at: string;
  rules_version: string | null;
  consent_revoked: boolean;
  replayable: boolean;
  inputs: DecisionInputs;
  outcome: DecisionOutcome;
}

export interface Difference {
  field: string;
  then: unknown;
  now: unknown;
}

export interface ReplayResult {
  decision_id: string;
  matches: boolean;
  differences: Difference[];
  rules_version_then: string;
  rules_version_now: string;
}

export interface ConsentRevocation {
  document_masked: string;
  revoked: number;
}
