import { POLICY_STATUS_OPTIONS, policyStatusLabel } from './policy-labels';

describe('etiquetas de estado de la póliza', () => {
  it('una póliza vencida se muestra como «Vencida» y se puede filtrar (HU-MOV-11)', () => {
    expect(policyStatusLabel('EXPIRED')).toBe('Vencida');
    expect(POLICY_STATUS_OPTIONS.map((option) => option.value)).toContain('EXPIRED');
  });

  it('un estado desconocido se muestra tal cual', () => {
    expect(policyStatusLabel('OTRO')).toBe('OTRO');
  });
});
