import { ClaimStatus, PhotoIntegrity } from '../../core/models/claim.model';

export const CLAIM_STATUS_OPTIONS: { value: ClaimStatus; label: string }[] = [
  { value: 'REPORTED', label: 'Reportado' },
  { value: 'IN_REVIEW', label: 'En evaluación' },
  { value: 'REJECTED', label: 'Rechazado' },
  { value: 'SETTLED', label: 'Liquidado' },
  { value: 'PAID', label: 'Pagado' },
];

export const CLAIM_EVENTS: Record<string, string> = {
  REPORTED: 'Reportado',
  ASSIGNED: 'Caso tomado',
  EXPERT_ASSIGNED: 'Perito asignado',
  REJECTED: 'Rechazado',
  SETTLED: 'Liquidado',
  PAID: 'Indemnización pagada',
};

export const INTEGRITY: Record<PhotoIntegrity, string> = {
  INTACT: 'Íntegra: coincide con la huella',
  ALTERED: 'Alterada: no coincide con la huella',
  UNVERIFIED: 'Sin verificar: no se pudo leer la foto',
};

export function claimStatusLabel(status: string): string {
  return CLAIM_STATUS_OPTIONS.find((option) => option.value === status)?.label ?? status;
}
