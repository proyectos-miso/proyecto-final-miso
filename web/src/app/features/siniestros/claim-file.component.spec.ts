import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { ClaimDetail } from '../../core/models/claim.model';
import { ClaimFileComponent } from './claim-file.component';

// Expediente, dictamen y liquidación del siniestro (HU-WEB-15).
describe('ClaimFileComponent (HU-WEB-15)', () => {
  let fixture: ComponentFixture<ClaimFileComponent>;
  let http: HttpTestingController;

  const claim: ClaimDetail = {
    claim_number: 'SIN-2026-00001',
    status: 'REPORTED',
    origin: 'APP',
    reported_at: '2026-10-27T12:00:00Z',
    description: 'Fallecimiento del titular en accidente de tránsito.',
    parametric_event: null,
    policy_number: 'POL-2026-00001',
    policy_status: 'ACTIVE',
    plan: 'premium',
    insured_name: 'Carolina Prueba Gómez',
    insured_document: '******4050',
    insured_phone: '******0000',
    coverage: { coverage_id: 'death', name: 'Muerte por cualquier causa', limit: 200_000_000 },
    coverages: [
      { coverage_id: 'death', name: 'Muerte por cualquier causa', limit: 200_000_000 },
      { coverage_id: 'disability', name: 'Incapacidad total y permanente', limit: 100_000_000 },
    ],
    assigned_to: null,
    expert: null,
    settled_amount: null,
    photos: [
      { photo_id: 'f1', position: 1, url: 'https://s3.example/f1', latitude: 4.6097, longitude: -74.0817, accuracy_m: 12,
        mock_location: false, captured_at: '2026-10-27T10:00:00Z', trusted_time: true, integrity: 'INTACT' },
      { photo_id: 'f2', position: 2, url: null, latitude: 4.61, longitude: -74.08, accuracy_m: 30,
        mock_location: true, captured_at: '2026-10-27T10:05:00Z', trusted_time: false, integrity: 'ALTERED' },
    ],
    events: [{ kind: 'REPORTED', actor: 'App del asegurado', justification: null, amount: null, created_at: '2026-10-27T12:00:00Z' }],
    payouts: [],
  };

  const page = () => fixture.nativeElement as HTMLElement;
  const text = () => page().textContent ?? '';
  const detailCall = () => http.expectOne((req) => req.url.endsWith('/experience/claims/SIN-2026-00001'));
  const setValue = (selector: string, value: string) => {
    const field = page().querySelector(selector) as HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement;
    field.value = value;
    field.dispatchEvent(new Event(field.tagName === 'SELECT' ? 'change' : 'input'));
    fixture.detectChanges();
  };
  const choose = async (decision: 'APPROVE' | 'REJECT') => {
    const radio = page().querySelector(`input[value="${decision}"]`) as HTMLInputElement;
    radio.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  };
  const open = async (detail: ClaimDetail = claim) => {
    fixture.componentRef.setInput('claimNumber', 'SIN-2026-00001');
    fixture.detectChanges();
    await fixture.whenStable();
    http.expectOne((req) => req.url.endsWith('/experience/experts')).flush([
      { expert_id: 'PER-BOG-01', name: 'Laura Inspección Ruiz', city: 'Bogotá' },
    ]);
    detailCall().flush(detail);
    fixture.detectChanges();
    await fixture.whenStable();
  };
  const JUSTIFICATION = 'Se verificó el certificado de defunción y la cobertura vigente.';

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ClaimFileComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    fixture = TestBed.createComponent(ClaimFileComponent);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('AC-02 muestra póliza, amparo, asegurado enmascarado, descripción, fotos e historial', async () => {
    await open();

    expect(text()).toContain('Muerte por cualquier causa');
    expect(text()).toContain('******4050');
    expect(text()).toContain('Fallecimiento del titular');
    expect(page().querySelectorAll('figure').length).toBe(2);
    expect(page().querySelector('img')?.getAttribute('alt')).toContain('Foto 1, tomada el');
    expect(page().querySelector('img')?.getAttribute('alt')).toContain('4.6097');
    expect(text()).toContain('Reportado');
    expect(document.activeElement?.id).toBe('claimTitle');
  });

  it('AC-03 y AC-17 marcan la foto alterada, la ubicación simulada y la hora sin confirmar', async () => {
    await open();

    const integrity = [...page().querySelectorAll('.integrity')].map((e) => e.getAttribute('data-integrity'));
    expect(integrity).toEqual(['INTACT', 'ALTERED']);
    expect(text()).toContain('Alterada: no coincide con la huella');
    expect(text()).toContain('ubicación simulada');
    expect(text()).toContain('La hora no se pudo confirmar');
    expect(text()).toContain('Foto 2 no disponible');
  });

  it('AC-05 no deja liquidar por encima del amparo y AC-04 exige la justificación', async () => {
    await open();
    await choose('APPROVE');
    setValue('#coverage', 'disability');
    setValue('#amount', '150000000');
    setValue('#justification', JUSTIFICATION);
    const submit = page().querySelector('.dictamen .btn-primary') as HTMLButtonElement;

    expect(page().querySelector('#amountError')?.textContent).toContain('supera la suma asegurada');
    expect(page().querySelector('#amount')?.getAttribute('aria-invalid')).toBe('true');
    expect(submit.disabled).toBe(true);

    setValue('#amount', '90000000');
    setValue('#justification', 'corta');
    expect(submit.disabled).toBe(true);
  });

  it('AC-04 liquida con amparo, valor y justificación y recarga el expediente', async () => {
    await open();
    await choose('APPROVE');
    setValue('#amount', '150000000');
    setValue('#justification', JUSTIFICATION);
    (page().querySelector('.dictamen .btn-primary') as HTMLButtonElement).click();

    const call = http.expectOne((req) => req.url.endsWith('/claims/SIN-2026-00001/decision'));
    expect(call.request.body).toEqual({
      decision: 'APPROVE', justification: JUSTIFICATION, coverage_id: 'death', amount: 150_000_000,
    });
    call.flush(null, { status: 204, statusText: 'No Content' });
    detailCall().flush({ ...claim, status: 'PAID' });
    fixture.detectChanges();
    expect(page().querySelector('[role="status"].result')?.textContent).toContain('pago ordenado');
  });

  it('AC-06 muestra el dictamen registrado de solo lectura y sin formulario', async () => {
    await open({
      ...claim,
      status: 'REJECTED',
      events: [
        ...claim.events,
        { kind: 'REJECTED', actor: 'operaciones@solventa.example', justification: JUSTIFICATION, amount: null, created_at: '2026-10-28T09:30:00Z' },
      ],
    });

    const decision = page().querySelector('[data-testid="decision"]') as HTMLElement;
    expect(decision.textContent).toContain('Rechazado');
    expect(decision.textContent).toContain('operaciones@solventa.example');
    expect(decision.textContent).toContain(JUSTIFICATION);
    expect(decision.textContent).toContain('no se puede modificar');
    expect(page().querySelector('.dictamen')).toBeNull();
  });

  it('AC-07 toma el caso con el perito elegido', async () => {
    await open();
    setValue('#expert', 'PER-BOG-01');
    (page().querySelector('.assign .btn-secondary') as HTMLButtonElement).click();

    const call = http.expectOne((req) => req.url.endsWith('/claims/SIN-2026-00001/assign'));
    expect(call.request.body).toEqual({ expert_id: 'PER-BOG-01' });
    call.flush(null, { status: 204, statusText: 'No Content' });
    detailCall().flush({ ...claim, status: 'IN_REVIEW', assigned_to: 'operaciones@solventa.example' });
  });

  it('AC-08 muestra el pago a cada beneficiario y permite reintentar los rechazados', async () => {
    await open({
      ...claim,
      status: 'SETTLED',
      payouts: [
        { beneficiary_name: 'Lucía', beneficiary_document: '****0111', percentage: 60, amount: 90_000_000, status: 'APPROVED', receipt_number: 'REC-2026-00001' },
        { beneficiary_name: 'Mateo', beneficiary_document: '******0033', percentage: 40, amount: 60_000_000, status: 'DECLINED', receipt_number: null },
      ],
    });

    expect(text()).toContain('Recibo REC-2026-00001');
    expect(text()).toContain('Pago rechazado por el banco');
    const retry = [...page().querySelectorAll('button')].find((b) => b.textContent?.includes('Reintentar'))!;
    retry.click();
    http.expectOne((req) => req.url.endsWith('/claims/SIN-2026-00001/payouts/retry')).flush(null, { status: 204, statusText: 'No Content' });
    detailCall().flush({ ...claim, status: 'PAID' });
  });

  it('un siniestro que no existe o de otro socio se informa sin errores técnicos', async () => {
    fixture.componentRef.setInput('claimNumber', 'SIN-2026-00001');
    fixture.detectChanges();
    await fixture.whenStable();
    http.expectOne((req) => req.url.endsWith('/experience/experts')).flush([]);
    detailCall().flush({ detail: 'No encontramos el siniestro.' }, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    expect(page().querySelector('[data-testid="not-found"]')?.textContent).toContain('No encontramos el siniestro SIN-2026-00001');
    expect(text()).not.toContain('404');
  });

  it('el texto del asegurado se muestra como texto, sin interpretar HTML', async () => {
    await open({ ...claim, description: '<img src=x onerror=alert(1)>' });

    expect(page().querySelectorAll('img').length).toBe(1);
    expect(text()).toContain('<img src=x onerror=alert(1)>');
  });
});
