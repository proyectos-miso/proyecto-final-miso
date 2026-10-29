import { PlanTier } from '../../core/models/quote.model';
import { PolicyStatus } from '../../core/models/policy.model';

export const POLICY_STATUS_OPTIONS: { value: PolicyStatus; label: string }[] = [
  { value: 'ACTIVE', label: 'Vigente' },
  { value: 'ISSUED', label: 'Pendiente de pago' },
  { value: 'CANCELLED', label: 'Cancelada' },
];

export const PLAN_LABELS: Record<PlanTier, string> = { basic: 'Básico', standard: 'Estándar', premium: 'Premium' };

export function policyStatusLabel(status: string): string {
  return POLICY_STATUS_OPTIONS.find((option) => option.value === status)?.label ?? status;
}
