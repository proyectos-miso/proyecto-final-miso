import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { API_BASE_URL } from '../quotes/quote.service';
import { PartnerService } from './partner.service';

// Gestión de socios del portal (HOTFIX-gestion-de-socios, FE-WEB-05).
describe('PartnerService (FE-WEB-05)', () => {
  let http: HttpTestingController;
  let partners: PartnerService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: API_BASE_URL, useValue: '/api/v1' }],
    });
    http = TestBed.inject(HttpTestingController);
    partners = TestBed.inject(PartnerService);
  });

  afterEach(() => http.verify());

  it('lista los socios desde experience', () => {
    partners.list().subscribe();
    const call = http.expectOne('/api/v1/experience/partners');
    expect(call.request.method).toBe('GET');
    call.flush({ items: [], truncated: false });
  });

  it('sabe si el contrato OpenAPI está publicado', () => {
    const seen: boolean[] = [];
    partners.openapiAvailable('/openapi.json').subscribe((value) => seen.push(value));
    http.expectOne('/openapi.json').flush('{}');
    partners.openapiAvailable('/openapi.json').subscribe((value) => seen.push(value));
    http.expectOne('/openapi.json').flush('No encontrado', { status: 404, statusText: 'Not Found' });
    expect(seen).toEqual([true, false]);
  });

  it('registra con el endpoint de quote-edge y el cuerpo sin espacios', () => {
    partners.register('  Fintech de Prueba ', ' soporte@fintech.example.com ').subscribe();
    const call = http.expectOne('/api/v1/quote-edge/partners/register');
    expect(call.request.method).toBe('POST');
    expect(call.request.body).toEqual({ name: 'Fintech de Prueba', contact_email: 'soporte@fintech.example.com' });
    call.flush({});
  });

  it('aprueba, rota y revoca con el identificador codificado', () => {
    partners.approve('socio_1').subscribe();
    partners.rotate('socio_1').subscribe();
    partners.revoke('socio/1').subscribe();
    for (const url of [
      '/api/v1/experience/partners/socio_1/approve',
      '/api/v1/experience/partners/socio_1/credentials',
      '/api/v1/experience/partners/socio%2F1/revoke',
    ]) {
      const call = http.expectOne(url);
      expect(call.request.method).toBe('POST');
      call.flush({});
    }
  });
});
