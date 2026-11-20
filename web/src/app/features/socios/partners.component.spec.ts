import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { PartnerSummary } from '../../core/models/partner.model';
import { COPY_TEXT, PartnersComponent } from './partners.component';

// Gestión de socios (HOTFIX-gestion-de-socios, FE-WEB-05).
describe('PartnersComponent (FE-WEB-05)', () => {
  let fixture: ComponentFixture<PartnersComponent>;
  let http: HttpTestingController;
  const copied: string[] = [];
  const fakeSecret = () => ['sec', 'prueba', Math.random().toString(36).slice(2)].join('_');
  const page = () => fixture.nativeElement as HTMLElement;
  const list = () => http.expectOne((req) => req.method === 'GET' && req.url.endsWith('/experience/partners'));
  const pageOf = (items: PartnerSummary[], truncated = false) => ({ items, truncated });
  const openapi = () => http.expectOne((req) => req.method === 'GET' && req.url === '/openapi.json');
  const button = (text: string) =>
    Array.from(page().querySelectorAll('button')).find((b) => b.textContent?.replace(/\s+/g, ' ').trim().startsWith(text)) as HTMLButtonElement;

  const PARTNERS: PartnerSummary[] = [
    { partner_id: 'fintech_1', name: 'Fintech de Prueba', environment: 'sandbox', status: 'ACTIVE', quota_limit: 1000, quota_used: 12,
      created_at: '2026-11-14T12:00:00Z', has_contact: true, contact_email_masked: 's***@fintech.example.com' },
    { partner_id: 'banco-aliado-1', name: 'Banco Aliado S.A.', environment: 'production', status: 'ACTIVE', quota_limit: 50000, quota_used: 0,
      created_at: '2026-10-01T12:00:00Z', has_contact: false, contact_email_masked: null },
    { partner_id: 'socio-inactivo-1', name: 'Financiera Inactiva S.A.', environment: 'production', status: 'REVOKED', quota_limit: 1000,
      quota_used: 0, created_at: null, has_contact: false, contact_email_masked: null },
  ];

  beforeEach(async () => {
    copied.length = 0;
    await TestBed.configureTestingModule({
      imports: [PartnersComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: COPY_TEXT, useValue: (text: string) => (copied.push(text), Promise.resolve()) },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(PartnersComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => {
    http.match((req) => req.url === '/openapi.json').forEach((req) => req.flush('{}'));
    http.verify();
  });

  it('lista los socios con caption, encabezados y el correo enmascarado; las acciones dependen del estado', () => {
    list().flush(pageOf(PARTNERS));
    fixture.detectChanges();

    expect(page().querySelector('caption')?.textContent).toContain('Socios registrados');
    expect(page().querySelectorAll('thead th[scope="col"]').length).toBe(7);
    const rows = page().querySelectorAll('tbody tr');
    expect(rows.length).toBe(3);
    expect(rows[0].querySelector('th[scope="row"]')?.textContent).toContain('Fintech de Prueba');
    expect(rows[0].textContent).toContain('Sandbox');
    expect(rows[0].textContent).toContain('s***@fintech.example.com');
    expect(rows[0].textContent).toContain(`12 de ${new Intl.NumberFormat('es-CO').format(1000)}`);
    expect(rows[0].textContent).toContain('Aprobar a producción');
    expect(rows[1].textContent).not.toContain('Aprobar a producción');
    expect(rows[1].textContent).toContain('Sin correo');
    expect(rows[2].textContent).toContain('Revocado');
    expect(rows[2].querySelectorAll('button').length).toBe(0);
    expect(page().querySelector('[role="status"]')?.getAttribute('aria-live')).toBe('polite');
  });

  it('registra un socio y muestra las credenciales una sola vez, con Copiar y sin guardarlas en el navegador', async () => {
    list().flush(pageOf([]));
    fixture.detectChanges();
    expect(page().querySelector('[data-testid="no-partners"]')).toBeTruthy();
    const secret = fakeSecret();

    const name = page().querySelector('#partnerName') as HTMLInputElement;
    const email = page().querySelector('#partnerEmail') as HTMLInputElement;
    expect(page().querySelector('label[for="partnerName"]')).toBeTruthy();
    expect(page().querySelector('label[for="partnerEmail"]')).toBeTruthy();
    name.value = 'Fintech de Prueba';
    name.dispatchEvent(new Event('input'));
    email.value = 'soporte@fintech.example.com';
    email.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    button('Registrar en sandbox').click();

    const call = http.expectOne((req) => req.url.endsWith('/quote-edge/partners/register'));
    expect(call.request.body).toEqual({ name: 'Fintech de Prueba', contact_email: 'soporte@fintech.example.com' });
    call.flush({ partner_id: 'fintech_1', name: 'Fintech de Prueba', client_id: 'client_fintech_1', client_secret: secret,
      environment: 'sandbox', quota_limit: 1000, openapi_spec_url: '/openapi.json', message: 'ok' });
    list().flush(pageOf([PARTNERS[0]]));
    fixture.detectChanges();
    await fixture.whenStable();

    const panel = page().querySelector('section.secret') as HTMLElement;
    expect(panel.textContent).toContain('No se volverá a mostrar');
    expect(page().querySelector('[data-testid="client-secret"]')?.textContent).toBe(secret);
    expect(document.activeElement).toBe(panel);
    expect(JSON.stringify({ ...sessionStorage })).not.toContain(secret);
    expect(JSON.stringify({ ...localStorage })).not.toContain(secret);

    button('Copiar client_secret').click();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(copied).toEqual([secret]);
    expect(page().querySelector('[role="status"]')?.textContent).toContain('client_secret copiado');

    button('Ya las guardé').click();
    fixture.detectChanges();
    expect(page().querySelector('section.secret')).toBeNull();
    expect(page().textContent).not.toContain(secret);
  });

  it('revocar pide confirmación y solo entonces llama al servidor', async () => {
    list().flush(pageOf(PARTNERS));
    fixture.detectChanges();

    button('Revocar').click();
    fixture.detectChanges();
    await fixture.whenStable();
    const confirm = page().querySelector('section.confirm') as HTMLElement;
    expect(confirm.textContent).toContain('¿Revocar a Fintech de Prueba?');
    expect(document.activeElement).toBe(confirm);
    http.expectNone((req) => req.url.endsWith('/revoke'));

    button('Cancelar').click();
    fixture.detectChanges();
    expect(page().querySelector('section.confirm')).toBeNull();

    button('Revocar').click();
    fixture.detectChanges();
    button('Confirmar: Revocar').click();
    const call = http.expectOne((req) => req.url.endsWith('/experience/partners/fintech_1/revoke'));
    expect(call.request.method).toBe('POST');
    call.flush({ ...PARTNERS[0], status: 'REVOKED' });
    list().flush(pageOf([{ ...PARTNERS[0], status: 'REVOKED' }, ...PARTNERS.slice(1)]));
    fixture.detectChanges();
    expect(page().querySelector('[role="status"]')?.textContent).toContain('Fintech de Prueba quedó revocado');
    expect(page().querySelector('section.confirm')).toBeNull();
  });

  it('rotar con confirmación muestra el secreto nuevo y aprobar informa el error del servidor', () => {
    list().flush(pageOf(PARTNERS));
    fixture.detectChanges();
    const secret = fakeSecret();

    button('Rotar credenciales').click();
    fixture.detectChanges();
    button('Confirmar: Rotar credenciales').click();
    http.expectOne((req) => req.url.endsWith('/fintech_1/credentials'))
      .flush({ partner_id: 'fintech_1', client_id: 'client_fintech_1', client_secret: secret, message: 'ok' });
    list().flush(pageOf(PARTNERS));
    fixture.detectChanges();
    expect(page().querySelector('[data-testid="client-secret"]')?.textContent).toBe(secret);

    button('Aprobar a producción').click();
    fixture.detectChanges();
    button('Confirmar: Aprobar a producción').click();
    http.expectOne((req) => req.url.endsWith('/fintech_1/approve'))
      .flush({ detail: 'Socio revocado.' }, { status: 409, statusText: 'Conflict' });
    list().flush(pageOf(PARTNERS));
    fixture.detectChanges();
    expect(page().querySelector('[role="alert"]')?.textContent).toContain('Socio revocado.');
  });

  it('al salir de la pestaña se borran las credenciales mostradas', () => {
    list().flush(pageOf([]));
    fixture.componentInstance.credentials.set({ partner_id: 'x', client_id: 'client_x', client_secret: fakeSecret(), message: '' });
    fixture.destroy();
    expect(fixture.componentInstance.credentials()).toBeNull();
  });

  it('muestra el enlace al contrato OpenAPI solo si el servidor lo publica', () => {
    list().flush(pageOf(PARTNERS));
    openapi().flush('{}');
    fixture.detectChanges();
    const link = page().querySelector('a[href="/openapi.json"]') as HTMLAnchorElement;
    expect(link.target).toBe('_blank');
    expect(link.querySelector('.sr-only')?.textContent).toContain('abre en una pestaña nueva');

    const other = TestBed.createComponent(PartnersComponent);
    other.detectChanges();
    list().flush(pageOf(PARTNERS));
    openapi().flush('No encontrado', { status: 404, statusText: 'Not Found' });
    other.detectChanges();
    expect((other.nativeElement as HTMLElement).querySelector('a[href="/openapi.json"]')).toBeNull();
  });

  it('avisa cuando el listado viene truncado y la tabla referencia el aviso', () => {
    list().flush(pageOf(PARTNERS.slice(0, 2)));
    fixture.detectChanges();
    expect(page().querySelector('[data-testid="partners-truncated"]')).toBeNull();
    expect(page().querySelector('table')?.hasAttribute('aria-describedby')).toBe(false);

    fixture.componentInstance.load();
    list().flush(pageOf(PARTNERS.slice(0, 2), true));
    fixture.detectChanges();
    const notice = page().querySelector('[data-testid="partners-truncated"]') as HTMLElement;
    expect(notice.textContent).toContain('solo los socios más recientes');
    expect(page().querySelector('table')?.getAttribute('aria-describedby')).toBe(notice.id);
  });

  it('si el aviso de truncado aparece tras una acción, lo anuncia en la región aria-live', () => {
    list().flush(pageOf(PARTNERS.slice(0, 2)));
    fixture.detectChanges();

    button('Revocar').click();
    fixture.detectChanges();
    button('Confirmar: Revocar').click();
    http.expectOne((req) => req.url.endsWith('/fintech_1/revoke')).flush({ ...PARTNERS[0], status: 'REVOKED' });
    list().flush(pageOf([{ ...PARTNERS[0], status: 'REVOKED' }, PARTNERS[1]], true));
    fixture.detectChanges();

    const status = page().querySelector('[role="status"]')?.textContent ?? '';
    expect(status).toContain('Fintech de Prueba quedó revocado.');
    expect(status).toContain('solo los socios más recientes');
  });

  it('devuelve el foco al botón que abrió la acción o, si ya no existe, al encabezado', async () => {
    list().flush(pageOf(PARTNERS));
    fixture.detectChanges();
    const heading = page().querySelector('h1') as HTMLElement;
    expect(heading.getAttribute('tabindex')).toBe('-1');

    const revoke = button('Revocar');
    revoke.click();
    fixture.detectChanges();
    button('Cancelar').click();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(document.activeElement).toBe(revoke);

    const rotate = button('Rotar credenciales');
    rotate.click();
    fixture.detectChanges();
    button('Confirmar: Rotar credenciales').click();
    http.expectOne((req) => req.url.endsWith('/fintech_1/credentials'))
      .flush({ partner_id: 'fintech_1', client_id: 'client_fintech_1', client_secret: fakeSecret(), message: 'ok' });
    list().flush(pageOf(PARTNERS));
    fixture.detectChanges();
    await fixture.whenStable();
    expect(document.activeElement).toBe(page().querySelector('section.secret'));
    button('Ya las guardé').click();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(document.activeElement).toBe(rotate);

    button('Revocar').click();
    fixture.detectChanges();
    button('Confirmar: Revocar').click();
    http.expectOne((req) => req.url.endsWith('/fintech_1/revoke')).flush({ ...PARTNERS[0], status: 'REVOKED' });
    list().flush(pageOf([{ ...PARTNERS[0], status: 'REVOKED' }, ...PARTNERS.slice(1)]));
    fixture.detectChanges();
    await fixture.whenStable();
    expect(document.activeElement).toBe(heading);
  });
});
