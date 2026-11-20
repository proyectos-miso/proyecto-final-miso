import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { DecisionDetail } from '../../core/models/audit.model';
import {
  AuditComponent,
  CONSENT_REVOKED_NOT_REPLAYABLE,
  RELOAD_FAILED,
  REJECTION_NOT_REPLAYABLE,
  REVOKED_NOTICE,
} from './audit.component';

// Auditoría de suscripción (HOTFIX-auditoria-de-suscripcion, FE-WEB-08).
describe('AuditComponent (FE-WEB-08)', () => {
  let fixture: ComponentFixture<AuditComponent>;
  let http: HttpTestingController;
  const page = () => fixture.nativeElement as HTMLElement;
  const status = () => page().querySelector('[role="status"]') as HTMLElement;
  const button = (text: string) =>
    Array.from(page().querySelectorAll('button')).find((b) => b.textContent?.replace(/\s+/g, ' ').trim().startsWith(text)) as HTMLButtonElement;
  const type = (selector: string, value: string) => {
    const input = page().querySelector(selector) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  };
  const submitSearch = (id: string) => {
    type('#decisionId', id);
    (page().querySelector('form.search') as HTMLFormElement).dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  };

  const QUOTE: DecisionDetail = {
    decision_id: 'QUO-1A2B3C4D5E6F',
    kind: 'quote',
    created_at: '2026-11-15T15:00:00Z',
    rules_version: '2026.11',
    consent_revoked: false,
    replayable: true,
    inputs: { document_masked: '******4050', birth_date: '1990-05-15', insured_amount: 100000000, occupation_risk: 2, score: 720 },
    outcome: {
      status: 'active',
      degradation_reason: null,
      rule_id: null,
      reason: null,
      max_approvable_amount: null,
      tiers: [
        { tier: 'standard', monthly_premium: 30096, breakdown: [
          { code: 'base_rate', label: 'Tarifa base actuarial', multiplier: 1, amount: 27360 },
          { code: 'occupation', label: 'Ajuste por riesgo de ocupación (nivel 2)', multiplier: 1.1, amount: 2736 },
        ] },
      ],
    },
  };

  const REJECTION: DecisionDetail = {
    decision_id: 'UWR-ABCDEF012345',
    kind: 'rejection',
    created_at: '2026-11-15T15:00:00Z',
    rules_version: '2026.11',
    consent_revoked: false,
    replayable: false,
    inputs: { document_masked: '******4050', insured_amount: 900000000 },
    outcome: { status: 'review_required', degradation_reason: null, tiers: [], rule_id: 'UW-01',
      reason: 'La suma asegurada solicitada supera el cupo.', max_approvable_amount: 700000000 },
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AuditComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    fixture = TestBed.createComponent(AuditComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('valida el identificador antes de consultar al servidor', () => {
    submitSearch('POL-2026-00001');

    const input = page().querySelector('#decisionId') as HTMLInputElement;
    expect(input.getAttribute('aria-invalid')).toBe('true');
    expect(input.getAttribute('aria-errormessage')).toBe('decisionIdError');
    expect(input.getAttribute('aria-describedby')).toBe('decisionIdHint decisionIdError');
    expect(page().querySelector('#decisionIdError')?.textContent).toContain('QUO- o UWR-');
    expect(page().querySelector('label[for="decisionId"]')).toBeTruthy();
    http.expectNone(() => true);
  });

  it('muestra las entradas, la versión de reglas, la decisión y el desglose por tier en tablas accesibles', () => {
    submitSearch(' quo-1a2b3c4d5e6f ');
    http.expectOne((req) => req.method === 'GET' && req.url.endsWith('/experience/decisions/QUO-1A2B3C4D5E6F')).flush(QUOTE);
    fixture.detectChanges();

    expect(page().querySelector('[data-testid="rules-version"]')?.textContent).toContain('2026.11');
    expect(page().querySelector('[data-testid="decision-status"]')?.textContent).toContain('Cotización activa');
    expect(page().querySelector('[data-testid="input-document"]')?.textContent).toBe('******4050');
    expect(page().textContent).not.toContain('1020304050');
    const captions = Array.from(page().querySelectorAll('caption')).map((c) => c.textContent?.trim());
    expect(captions).toEqual(['Entradas de la decisión', 'Desglose del plan Estándar']);
    const tier = page().querySelector('[data-testid="tier-table"]') as HTMLTableElement;
    expect(tier.querySelectorAll('thead th[scope="col"]').length).toBe(3);
    expect(tier.querySelectorAll('tbody th[scope="row"]').length).toBe(2);
    expect(tier.querySelector('tfoot')?.textContent).toContain('Prima mensual');
    expect(status().getAttribute('aria-live')).toBe('polite');
    expect(status().textContent).toContain('QUO-1A2B3C4D5E6F');
  });

  it('Recalcular anuncia en aria-live si coincide o la lista de diferencias', () => {
    submitSearch('QUO-1A2B3C4D5E6F');
    http.expectOne((req) => req.url.endsWith('/decisions/QUO-1A2B3C4D5E6F')).flush(QUOTE);
    fixture.detectChanges();

    button('Recalcular').click();
    const first = http.expectOne((req) => req.method === 'POST' && req.url.endsWith('/decisions/QUO-1A2B3C4D5E6F/replay'));
    first.flush({ decision_id: QUOTE.decision_id, matches: true, differences: [], rules_version_then: '2026.11', rules_version_now: '2026.11' });
    fixture.detectChanges();
    expect(status().textContent).toContain('coincide');
    expect(page().querySelector('[data-testid="replay-result"] h3')?.textContent).toBe('Coincide');

    button('Recalcular').click();
    http.expectOne((req) => req.url.endsWith('/replay')).flush({
      decision_id: QUOTE.decision_id,
      matches: false,
      differences: [{ field: 'standard.occupation.multiplier', then: 1.1, now: 1.2 }],
      rules_version_then: '2026.11',
      rules_version_now: '2027.01',
    });
    fixture.detectChanges();
    expect(status().textContent).toContain('no coincide; 1 diferencias');
    const result = page().querySelector('[data-testid="replay-result"]') as HTMLElement;
    expect(result.querySelector('h3')?.textContent).toBe('No coincide');
    expect(result.querySelector('caption')?.textContent).toContain('Diferencias del recálculo');
    expect(result.querySelector('tbody th[scope="row"]')?.textContent).toBe('standard.occupation.multiplier');
  });

  it('un rechazo muestra solo lo guardado y explica por qué no se recalcula en lugar del botón', () => {
    submitSearch('UWR-ABCDEF012345');
    http.expectOne((req) => req.url.endsWith('/decisions/UWR-ABCDEF012345')).flush(REJECTION);
    fixture.detectChanges();

    expect(page().querySelector('[data-testid="input-document"]')?.textContent).toBe('******4050');
    expect(page().querySelector('[data-testid="input-requested"]')?.textContent).toContain('900.000.000');
    expect(page().querySelector('[data-testid="rejection-rule"]')?.textContent).toBe('UW-01');
    expect(page().querySelector('[data-testid="rejection-reason"]')?.textContent).toContain('supera el cupo');
    expect(page().querySelector('[data-testid="rejection-max"]')?.textContent).toContain('700.000.000');
    expect(page().querySelector('[data-testid="rules-version"]')?.textContent).toContain('2026.11');
    expect(page().querySelector('[data-testid="decision-status"]')?.textContent).toContain('Requiere revisión');
    expect(page().textContent).toContain('15/11/2026');
    expect(page().querySelector('[data-testid="input-birth-date"]')).toBeNull();
    expect(page().querySelector('[data-testid="input-score"]')).toBeNull();
    expect(page().textContent).not.toContain('Puntaje del buró');
    expect(page().textContent).not.toContain('Fecha de nacimiento');
    expect(page().querySelector('[data-testid="rejection-not-replayable"]')?.textContent).toBe(REJECTION_NOT_REPLAYABLE);
    expect(REJECTION_NOT_REPLAYABLE).toBe('Los rechazos no guardan el puntaje del buró (HU-WEB-07), así que no se pueden recalcular.');
    expect(page().querySelector('[data-testid="not-replayable"]')).toBeNull();
    expect(button('Recalcular')).toBeUndefined();
  });

  it('un rechazo anterior a la auditoría con el consentimiento revocado omite el documento, la regla, el motivo y los montos', () => {
    submitSearch('UWR-ABCDEF012345');
    http.expectOne((req) => req.url.endsWith('/decisions/UWR-ABCDEF012345')).flush({
      decision_id: REJECTION.decision_id, kind: 'rejection', created_at: REJECTION.created_at, rules_version: null,
      consent_revoked: true, replayable: false, inputs: { document_masked: null },
      outcome: { status: 'review_required', degradation_reason: null, tiers: [] },
    });
    fixture.detectChanges();

    const notice = page().querySelector('[data-testid="consent-revoked"]')?.textContent?.trim();
    expect(notice).toBe(REVOKED_NOTICE);
    expect(REVOKED_NOTICE).toBe('Se omiten los datos personales y los montos derivados de ellos.');
    expect(page().querySelectorAll('[data-testid="consent-revoked"]').length).toBe(1);
    for (const testid of ['input-document', 'input-requested', 'rejection-rule', 'rejection-reason', 'rejection-max']) {
      expect(page().querySelector(`[data-testid="${testid}"]`)).toBeNull();
    }
    expect(page().textContent).not.toContain('Monto máximo aprobable');
    expect(page().textContent).not.toContain('Monto solicitado');
    expect(page().textContent).not.toContain('COP');
    expect(page().querySelector('[data-testid="decision-status"]')?.textContent).toContain('Requiere revisión');
    expect(page().querySelector('[data-testid="rules-version"]')?.textContent).toContain('anterior a la auditoría');
    expect(page().querySelector('[data-testid="rejection-not-replayable"]')).toBeTruthy();
    expect(button('Recalcular')).toBeUndefined();
  });

  it('un rechazo nuevo con el consentimiento revocado muestra su versión de reglas sin documento, regla, motivo ni montos', () => {
    submitSearch('UWR-ABCDEF012345');
    http.expectOne((req) => req.url.endsWith('/decisions/UWR-ABCDEF012345')).flush({
      decision_id: REJECTION.decision_id, kind: 'rejection', created_at: REJECTION.created_at, rules_version: '2026.11',
      consent_revoked: true, replayable: false, inputs: { document_masked: null },
      outcome: { status: 'review_required', degradation_reason: null, tiers: [] },
    });
    fixture.detectChanges();

    expect(page().querySelectorAll('[data-testid="consent-revoked"]').length).toBe(1);
    expect(page().querySelector('[data-testid="consent-revoked"]')?.textContent?.trim()).toBe(REVOKED_NOTICE);
    for (const testid of ['input-document', 'input-requested', 'rejection-rule', 'rejection-reason', 'rejection-max']) {
      expect(page().querySelector(`[data-testid="${testid}"]`)).toBeNull();
    }
    expect(page().textContent).not.toContain('Monto máximo aprobable');
    expect(page().textContent).not.toContain('Monto solicitado');
    expect(page().textContent).not.toContain('COP');
    const version = page().querySelector('[data-testid="rules-version"]')?.textContent;
    expect(version).toContain('2026.11');
    expect(version).not.toContain('anterior a la auditoría');
    expect(page().querySelector('[data-testid="decision-status"]')?.textContent).toContain('Requiere revisión');
    expect(page().querySelector('[data-testid="rejection-not-replayable"]')).toBeTruthy();
    expect(button('Recalcular')).toBeUndefined();
  });

  it('informa el error del servidor al recalcular', () => {
    submitSearch('QUO-1A2B3C4D5E6F');
    http.expectOne((req) => req.url.endsWith('/decisions/QUO-1A2B3C4D5E6F')).flush(QUOTE);
    fixture.detectChanges();

    button('Recalcular').click();
    http.expectOne((req) => req.url.endsWith('/replay')).flush(
      { detail: 'Decisión anterior a la auditoría: no se puede recalcular.' },
      { status: 409, statusText: 'Conflict' },
    );
    fixture.detectChanges();
    expect(page().querySelector('[role="alert"]')?.textContent).toContain('anterior a la auditoría');
  });

  it('un espacio pegado no corta el identificador: no hay maxlength de 16 y se recorta antes de validar', () => {
    const input = page().querySelector('#decisionId') as HTMLInputElement;
    expect(input.maxLength).toBe(32);
    expect(input.getAttribute('aria-describedby')).toBe('decisionIdHint');

    submitSearch('  QUO-1A2B3C4D5E6F');
    http.expectOne((req) => req.url.endsWith('/decisions/QUO-1A2B3C4D5E6F')).flush(QUOTE);
    fixture.detectChanges();

    expect(page().querySelector('#decisionIdError')).toBeNull();
    expect(input.getAttribute('aria-invalid')).toBeNull();
  });

  it('el campo del documento tiene texto de ayuda y su error en aria-describedby', () => {
    const input = page().querySelector('#consentDocument') as HTMLInputElement;
    expect(input.getAttribute('aria-describedby')).toBe('consentDocumentHint');
    expect(page().querySelector('#consentDocumentHint')?.textContent).toContain('De 4 a 20 letras o números');

    button('Registrar revocación del consentimiento').click();
    fixture.detectChanges();

    expect(input.getAttribute('aria-describedby')).toBe('consentDocumentHint consentDocumentError');
    expect(input.getAttribute('aria-errormessage')).toBe('consentDocumentError');
  });

  it('con el consentimiento revocado una cotización solo muestra la nota, los planes y los nombres de las coberturas, sin primas ni montos', () => {
    submitSearch('QUO-1A2B3C4D5E6F');
    http.expectOne((req) => req.url.endsWith('/decisions/QUO-1A2B3C4D5E6F')).flush({
      decision_id: QUOTE.decision_id, kind: 'quote', created_at: QUOTE.created_at, rules_version: '2026.11',
      consent_revoked: true, replayable: false, inputs: {},
      outcome: { status: 'active', degradation_reason: null, tiers: [
        { tier: 'basic', coverages: [{ id: 'death', name: 'Muerte por cualquier causa' }] },
        { tier: 'standard', coverages: [{ id: 'death', name: 'Muerte por cualquier causa' }, { id: 'disability', name: 'Incapacidad total y permanente' }] },
      ] },
    });
    fixture.detectChanges();

    const notices = page().querySelectorAll('[data-testid="consent-revoked"]');
    expect(notices.length).toBe(1);
    expect(notices[0].textContent?.trim()).toBe(REVOKED_NOTICE);
    expect(REVOKED_NOTICE).toBe('Se omiten los datos personales y los montos derivados de ellos.');
    const plans = page().querySelector('[data-testid="revoked-plans"]') as HTMLElement;
    expect(Array.from(plans.querySelectorAll('.plan')).map((plan) => plan.textContent?.trim())).toEqual(['Básico', 'Estándar']);
    expect(plans.textContent).toContain('Incapacidad total y permanente');
    expect(page().querySelector('[data-testid="tier-summary"]')).toBeNull();
    expect(page().querySelector('[data-testid="tier-table"]')).toBeNull();
    for (const testid of ['input-document', 'input-birth-date', 'input-score']) {
      expect(page().querySelector(`[data-testid="${testid}"]`)).toBeNull();
    }
    const text = page().textContent ?? '';
    for (const hidden of ['Primas por plan', 'Prima mensual', 'Prima anual', 'Suma asegurada', 'Riesgo de ocupación',
      'Desglose del plan', 'Multiplicador', 'Límite', 'Deducible', 'COP', '$']) {
      expect(text).not.toContain(hidden);
    }
    expect(text).not.toMatch(/\d{1,3}(\.\d{3})+/);
    expect(button('Recalcular')).toBeUndefined();
    expect(page().querySelector('[data-testid="consent-not-replayable"]')?.textContent).toBe(CONSENT_REVOKED_NOT_REPLAYABLE);
    expect(CONSENT_REVOKED_NOT_REPLAYABLE).toBe('El consentimiento del titular fue revocado: no se recalcula con sus datos personales.');
  });

  it('buscar otro identificador limpia el resultado del recálculo anterior', () => {
    submitSearch('QUO-1A2B3C4D5E6F');
    http.expectOne((req) => req.url.endsWith('/decisions/QUO-1A2B3C4D5E6F')).flush(QUOTE);
    fixture.detectChanges();
    button('Recalcular').click();
    http.expectOne((req) => req.url.endsWith('/replay')).flush({
      decision_id: QUOTE.decision_id, matches: true, differences: [], rules_version_then: '2026.11', rules_version_now: '2026.11',
    });
    fixture.detectChanges();
    expect(page().querySelector('[data-testid="replay-result"]')).toBeTruthy();

    submitSearch('QUO-ABCDEF012345');
    expect(page().querySelector('[data-testid="replay-result"]')).toBeNull();
    http.expectOne((req) => req.url.endsWith('/decisions/QUO-ABCDEF012345')).flush({ ...QUOTE, decision_id: 'QUO-ABCDEF012345' });
    fixture.detectChanges();
    expect(page().querySelector('[data-testid="replay-result"]')).toBeNull();
  });

  it('la revocación del consentimiento pide confirmación, maneja el foco y solo al confirmar llama al servidor', async () => {
    button('Registrar revocación del consentimiento').click();
    fixture.detectChanges();
    expect(page().querySelector('#consentDocumentError')).toBeTruthy();
    expect(page().querySelector('section.confirm')).toBeNull();

    type('#consentDocument', ' 1020304050 ');
    const opener = button('Registrar revocación del consentimiento');
    opener.click();
    fixture.detectChanges();
    await fixture.whenStable();
    const confirm = page().querySelector('section.confirm') as HTMLElement;
    expect(confirm.textContent).toContain('******4050');
    expect(confirm.textContent).not.toContain('1020304050');
    expect(document.activeElement).toBe(confirm);
    http.expectNone(() => true);

    button('Cancelar').click();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(page().querySelector('section.confirm')).toBeNull();
    expect(document.activeElement).toBe(opener);

    opener.click();
    fixture.detectChanges();
    await fixture.whenStable();
    button('Confirmar: registrar revocación').click();
    const call = http.expectOne((req) => req.method === 'POST' && req.url.endsWith('/experience/consents/revoke'));
    expect(call.request.body).toEqual({ document_number: '1020304050' });
    call.flush({ document_masked: '******4050', revoked: 1 });
    fixture.detectChanges();
    await fixture.whenStable();

    expect(status().textContent).toContain('1 consentimientos del documento ******4050 quedaron revocados');
    expect(page().querySelector('section.confirm')).toBeNull();
    expect((page().querySelector('#consentDocument') as HTMLInputElement).value).toBe('');
    expect(document.activeElement).toBe(opener);
  });

  it('tras revocar vuelve a pedir la decisión abierta para omitir sus entradas personales', async () => {
    submitSearch('QUO-1A2B3C4D5E6F');
    http.expectOne((req) => req.url.endsWith('/decisions/QUO-1A2B3C4D5E6F')).flush(QUOTE);
    fixture.detectChanges();

    type('#consentDocument', '1020304050');
    button('Registrar revocación del consentimiento').click();
    fixture.detectChanges();
    await fixture.whenStable();
    button('Confirmar: registrar revocación').click();
    http.expectOne((req) => req.url.endsWith('/consents/revoke')).flush({ document_masked: '******4050', revoked: 1 });
    const revoked = { ...QUOTE, consent_revoked: true, replayable: false, inputs: {},
      outcome: { status: 'active', degradation_reason: null, tiers: [{ tier: 'standard', coverages: [] }] } };
    http.expectOne((req) => req.method === 'GET' && req.url.endsWith('/decisions/QUO-1A2B3C4D5E6F')).flush(revoked);
    fixture.detectChanges();

    expect(page().querySelector('[data-testid="consent-revoked"]')?.textContent?.trim()).toBe(REVOKED_NOTICE);
    expect(page().querySelector('[data-testid="input-document"]')).toBeNull();
    expect(page().textContent).not.toContain('1990-05-15');
  });

  it('tras revocar limpia el recálculo anterior y, si la recarga falla, vacía el detalle y avisa', async () => {
    submitSearch('QUO-1A2B3C4D5E6F');
    http.expectOne((req) => req.url.endsWith('/decisions/QUO-1A2B3C4D5E6F')).flush(QUOTE);
    fixture.detectChanges();
    button('Recalcular').click();
    http.expectOne((req) => req.url.endsWith('/replay')).flush({
      decision_id: QUOTE.decision_id, matches: true, differences: [], rules_version_then: '2026.11', rules_version_now: '2026.11',
    });
    fixture.detectChanges();

    type('#consentDocument', '1020304050');
    button('Registrar revocación del consentimiento').click();
    fixture.detectChanges();
    await fixture.whenStable();
    button('Confirmar: registrar revocación').click();
    http.expectOne((req) => req.url.endsWith('/consents/revoke')).flush({ document_masked: '******4050', revoked: 1 });
    fixture.detectChanges();
    expect(page().querySelector('[data-testid="replay-result"]')).toBeNull();
    http.expectOne((req) => req.method === 'GET' && req.url.endsWith('/decisions/QUO-1A2B3C4D5E6F')).flush(
      { detail: 'Error interno.' },
      { status: 500, statusText: 'Server Error' },
    );
    fixture.detectChanges();

    expect(page().querySelector('[role="alert"]')?.textContent).toContain(RELOAD_FAILED);
    expect(RELOAD_FAILED).toBe('La revocación quedó registrada. Busque la decisión de nuevo.');
    expect(page().querySelector('section.detail')).toBeNull();
    expect(page().querySelector('[data-testid="input-document"]')).toBeNull();
    expect(page().textContent).not.toContain('1990-05-15');
    expect(status().textContent).toContain('quedaron revocados');
  });
});
