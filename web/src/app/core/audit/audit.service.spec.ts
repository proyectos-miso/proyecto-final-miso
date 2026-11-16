import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { API_BASE_URL } from '../quotes/quote.service';
import { AuditService, DECISION_ID } from './audit.service';

// Auditoría de suscripción del portal (HOTFIX-auditoria-de-suscripcion, FE-WEB-08).
describe('AuditService (FE-WEB-08)', () => {
  let http: HttpTestingController;
  let audit: AuditService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: API_BASE_URL, useValue: '/api/v1' }],
    });
    http = TestBed.inject(HttpTestingController);
    audit = TestBed.inject(AuditService);
  });

  afterEach(() => http.verify());

  it('consulta el detalle y recalcula una decisión desde experience', () => {
    audit.detail('QUO-1A2B3C4D5E6F').subscribe();
    audit.replay('UWR-1A2B3C4D5E6F').subscribe();

    const detail = http.expectOne('/api/v1/experience/decisions/QUO-1A2B3C4D5E6F');
    expect(detail.request.method).toBe('GET');
    detail.flush({});
    const replay = http.expectOne('/api/v1/experience/decisions/UWR-1A2B3C4D5E6F/replay');
    expect(replay.request.method).toBe('POST');
    replay.flush({});
  });

  it('registra la revocación con el documento en el cuerpo y no en la URL', () => {
    audit.revokeConsent(' 1020304050 ').subscribe();

    const call = http.expectOne('/api/v1/experience/consents/revoke');
    expect(call.request.method).toBe('POST');
    expect(call.request.body).toEqual({ document_number: '1020304050' });
    expect(call.request.urlWithParams).not.toContain('1020304050');
    call.flush({ document_masked: '******4050', revoked: 1 });
  });

  it('acepta solo identificadores de cotización o de rechazo', () => {
    expect(DECISION_ID.test('QUO-1A2B3C4D5E6F')).toBe(true);
    expect(DECISION_ID.test('UWR-ABCDEF012345')).toBe(true);
    expect(DECISION_ID.test('POL-1A2B3C4D5E6F')).toBe(false);
    expect(DECISION_ID.test('QUO-1A2B')).toBe(false);
    expect(DECISION_ID.test('QUO-1A2B3C4D5E6F/../x')).toBe(false);
  });
});
