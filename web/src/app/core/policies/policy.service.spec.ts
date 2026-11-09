import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { PaymentService } from '../payments/payment.service';
import { API_BASE_URL } from '../quotes/quote.service';
import { PolicyService } from './policy.service';

// Servicios web de pólizas y pagos (HU-WEB-15: RC-05).
describe('PolicyService y PaymentService (HU-WEB-15, RC-05)', () => {
  let http: HttpTestingController;
  let policies: PolicyService;
  let payments: PaymentService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: API_BASE_URL, useValue: '/api/v1' }],
    });
    http = TestBed.inject(HttpTestingController);
    policies = TestBed.inject(PolicyService);
    payments = TestBed.inject(PaymentService);
  });

  afterEach(() => http.verify());

  it('emite con beneficiarios y titular en el cuerpo', () => {
    const beneficiaries = [
      { name: 'Lucía', document_type: 'CC', document_number: '52000111', relationship: 'CONYUGE' as const, percentage: 100 },
    ];
    policies.issuePolicy({ quote_id: 'Q-1', tier: 'standard', beneficiaries }).subscribe();

    const call = http.expectOne('/api/v1/experience/policies');
    expect(call.request.method).toBe('POST');
    expect(call.request.body.beneficiaries).toEqual(beneficiaries);
    call.flush({});
  });

  it('busca con texto y estado solo si hay estado', () => {
    policies.searchPolicies('  1020 ', 'ACTIVE').subscribe();
    policies.searchPolicies('x').subscribe();

    const [withStatus, withoutStatus] = http.match((req) => req.url === '/api/v1/experience/policies/search');
    expect(withStatus.request.params.get('q')).toBe('1020');
    expect(withStatus.request.params.get('status')).toBe('ACTIVE');
    expect(withoutStatus.request.params.has('status')).toBe(false);
    withStatus.flush({ items: [], has_more: false });
    withoutStatus.flush({ items: [], has_more: false });
  });

  it('codifica el número de póliza en el detalle, la carátula, el endoso, la cancelación y la auditoría', () => {
    const number = 'POL-2026-00001';
    policies.getPolicyDetail(number).subscribe();
    policies.getCaratulaLink(number).subscribe();
    policies.endorse(number, { kind: 'SUM_INSURED', justification: 'x'.repeat(20), insured_amount: 1 }).subscribe();
    policies.cancel(number, 'motivo suficientemente largo').subscribe();
    policies.audit(number).subscribe();

    http.expectOne(`/api/v1/experience/policies/${number}`).flush({});
    http.expectOne(`/api/v1/experience/policies/${number}/caratula`).flush({});
    const endorse = http.expectOne(`/api/v1/experience/policies/${number}/endorsements`);
    expect(endorse.request.body.kind).toBe('SUM_INSURED');
    endorse.flush({});
    const cancel = http.expectOne(`/api/v1/experience/policies/${number}/cancellation`);
    expect(cancel.request.body).toEqual({ reason: 'motivo suficientemente largo' });
    cancel.flush({});
    http.expectOne(`/api/v1/experience/policies/${number}/audit`).flush({ entries: [] });
  });

  it('pide el reporte mensual como archivo con su formato', () => {
    let received: Blob | undefined;
    policies.monthlyReport('2026-10', 'pdf').subscribe((blob) => (received = blob));

    const call = http.expectOne((req) => req.url === '/api/v1/experience/reports/2026-10');
    expect(call.request.params.get('format')).toBe('pdf');
    expect(call.request.responseType).toBe('blob');
    call.flush(new Blob(['%PDF']));
    expect(received).toBeInstanceOf(Blob);
  });

  it('cobra con la clave de idempotencia en la cabecera', () => {
    payments
      .charge({ policy_number: 'POL-2026-00001', amount: 100000, payment_method: 'PSE', token_or_account: 'tok_x' }, 'clave-1')
      .subscribe();

    const call = http.expectOne('/api/v1/experience/payments/charge');
    expect(call.request.headers.get('Idempotency-Key')).toBe('clave-1');
    expect(call.request.body.token_or_account).toBe('tok_x');
    call.flush({});
  });
});
