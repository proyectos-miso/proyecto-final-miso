import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { PolicyDetail } from '../../core/models/policy.model';
import { OPEN_DOWNLOAD, PolicyDetailComponent } from './policy-detail.component';

// Panel de detalle de operaciones (HU-WEB-09: TC-08 y TC-11; HU-WEB-16: TC-11, descarga de la carátula).
describe('PolicyDetailComponent (HU-WEB-09)', () => {
  let fixture: ComponentFixture<PolicyDetailComponent>;
  let http: HttpTestingController;
  let opened: ReturnType<typeof vi.fn>;

  const detail: PolicyDetail = {
    policy_number: 'POL-2026-00001',
    status: 'ACTIVE',
    plan: 'premium',
    valid_from: '2026-01-10T12:00:00Z',
    valid_until: '2027-01-10T12:00:00Z',
    partner_name: null,
    holder: {
      name: 'Carolina Prueba Gómez',
      document_type: 'CC',
      document: '******4050',
      email: 'c*****@example.com',
      phone: '******2233',
    },
    insured_amount: 200000000,
    monthly_premium: 100000,
    annual_premium: 1200000,
    coverages: [
      { name: 'Muerte por cualquier causa', limit: 200000000, deductible: 0 },
      { name: 'Incapacidad total y permanente', limit: 200000000, deductible: 0 },
    ],
    payments: [
      { paid_at: '2026-01-10T12:05:00Z', amount: 100000, currency: 'COP', method: 'PSE', status: 'APPROVED', receipt_number: 'REC-2026-00002', decline_reason: null },
      { paid_at: '2026-01-10T12:00:00Z', amount: 100000, currency: 'COP', method: 'CARD', status: 'DECLINED', receipt_number: null, decline_reason: 'Fondos insuficientes' },
    ],
    claims: [{ claim_number: 'SIN-2026-00001', status: 'IN_REVIEW', reported_at: '2026-02-01T12:00:00Z' }],
  };

  const page = () => fixture.nativeElement as HTMLElement;
  const text = () => page().textContent ?? '';
  const url = (number: string) => (req: { url: string }) => req.url.endsWith(`/experience/policies/${number}`);

  const open = async (number: string) => {
    fixture.componentRef.setInput('policyNumber', number);
    fixture.detectChanges();
    await fixture.whenStable();
  };

  beforeEach(async () => {
    opened = vi.fn();
    await TestBed.configureTestingModule({
      imports: [PolicyDetailComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: OPEN_DOWNLOAD, useValue: opened }],
    }).compileComponents();
    fixture = TestBed.createComponent(PolicyDetailComponent);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('TC-08 muestra titular enmascarado, plan, coberturas, primas, pagos y siniestros', async () => {
    await open('POL-2026-00001');
    http.expectOne(url('POL-2026-00001')).flush(detail);
    fixture.detectChanges();

    expect(text()).toContain('Carolina Prueba Gómez');
    expect(text()).toContain('CC ******4050');
    expect(text()).toContain('c*****@example.com');
    expect(text()).toContain('******2233');
    expect(text()).toContain('Premium');
    expect(text()).toContain('Vigente');
    expect(text()).toContain('Muerte por cualquier causa');
    expect(text()).toMatch(/200\.000\.000 COP/);
    expect(text()).toMatch(/100\.000 COP/);
    expect(text()).toMatch(/1\.200\.000 COP/);
    expect(text()).toContain('Recibo REC-2026-00002');
    expect(text()).toContain('Rechazado');
    expect(text()).toContain('Fondos insuficientes');
    expect(text()).toContain('Tarjeta');
    expect(text()).toContain('Siniestros asociados (1)');
    expect(text()).toContain('SIN-2026-00001');
    expect(text()).toContain('En revisión');
    const headings = Array.from(page().querySelectorAll('h3')).map((h) => h.textContent?.trim());
    expect(headings).toEqual([
      'Titular', 'Plan y vigencia', 'Beneficiarios', 'Coberturas contratadas', 'Historial de pagos',
      'Siniestros asociados (1)', 'Operaciones',
    ]);
  });

  it('TC-08 sin siniestros ni pagos lo dice, y sin titular no muestra contacto', async () => {
    await open('POL-2026-00003');
    http.expectOne(url('POL-2026-00003')).flush({
      ...detail,
      policy_number: 'POL-2026-00003',
      partner_name: 'Banco Aliado S.A.',
      holder: { name: null, document_type: null, document: '******0011', email: null, phone: null },
      payments: [],
      claims: [],
    });
    fixture.detectChanges();

    expect(page().querySelector('[data-testid="no-claims"]')?.textContent).toContain('No hay siniestros registrados.');
    expect(text()).toContain('No hay pagos registrados.');
    expect(text()).toContain('Sin nombre registrado');
    expect(text()).toContain('Banco Aliado S.A.');
    expect(text()).not.toContain('Correo');
    expect(text()).not.toContain('Teléfono');
  });

  it('TC-08 una póliza que no existe o con otro formato dice que no se encontró, sin errores técnicos', async () => {
    await open('POL-2026-99999');
    http.expectOne(url('POL-2026-99999')).flush({ detail: 'No encontramos la póliza.' }, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    const notFound = page().querySelector('[data-testid="not-found"]') as HTMLElement;
    expect(notFound.getAttribute('role')).toBe('alert');
    expect(notFound.textContent).toContain('No encontramos la póliza POL-2026-99999');
    expect(text()).not.toContain('404');
    expect(text()).not.toContain('Not Found');

    await open('abc');
    http.expectOne(url('abc')).flush({ detail: [{ type: 'string_pattern_mismatch' }] }, { status: 422, statusText: 'Unprocessable Entity' });
    fixture.detectChanges();
    expect(page().querySelector('[data-testid="not-found"]')?.textContent).toContain('No encontramos la póliza abc');
    expect(text()).not.toContain('string_pattern_mismatch');
  });

  it('TC-08 al abrir lleva el foco al título; Cerrar y Escape avisan que se cierra', async () => {
    let closed = 0;
    fixture.componentInstance.closed.subscribe(() => closed++);
    await open('POL-2026-00001');
    http.expectOne(url('POL-2026-00001')).flush(detail);
    fixture.detectChanges();

    expect(document.activeElement?.id).toBe('policyDetailTitle');
    (page().querySelector('.btn-close') as HTMLButtonElement).click();
    page().querySelector('section')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    expect(closed).toBe(2);
  });

  it('TC-11 muestra como texto el HTML guardado en el nombre del titular', async () => {
    await open('POL-2026-00001');
    http.expectOne(url('POL-2026-00001')).flush({ ...detail, holder: { ...detail.holder, name: '<img src=x onerror=alert(1)>' } });
    fixture.detectChanges();

    expect(page().querySelector('img')).toBeNull();
    expect(text()).toContain('<img src=x onerror=alert(1)>');
  });

  describe('carátula (HU-WEB-16)', () => {
    const caratula = (number: string) => (req: { url: string }) => req.url.endsWith(`/experience/policies/${number}/caratula`);
    const button = () => page().querySelector('.btn-download') as HTMLButtonElement;
    const status = () => page().querySelector('.download-status')?.textContent?.trim() ?? '';
    const link = { url: 'https://solventa-documents.s3.amazonaws.com/caratulas/POL-2026-00001.pdf?X-Amz-Expires=900', expires_at: '2026-11-01T13:20:00Z' };

    const show = async (data: PolicyDetail = detail) => {
      await open(data.policy_number);
      http.expectOne(url(data.policy_number)).flush(data);
      fixture.detectChanges();
    };

    it('TC-11 el botón pide el enlace, anuncia el avance y abre la descarga', async () => {
      await show();
      expect(button().textContent?.trim()).toBe('Descargar carátula (PDF)');
      expect(page().querySelector('.download-status')?.getAttribute('role')).toBe('status');

      button().click();
      fixture.detectChanges();
      expect(status()).toBe('Preparando la carátula…');
      expect(button().getAttribute('aria-disabled')).toBe('true');

      http.expectOne(caratula('POL-2026-00001')).flush(link);
      fixture.detectChanges();
      expect(opened).toHaveBeenCalledExactlyOnceWith(link.url);
      expect(status()).toBe('La descarga de la carátula comenzó.');
      expect(button().getAttribute('aria-disabled')).toBe('false');
    });

    it('TC-11 cada clic pide un enlace nuevo y mientras prepara ignora otro clic', async () => {
      await show();

      button().click();
      button().click();
      http.expectOne(caratula('POL-2026-00001')).flush(link);
      button().click();
      http.expectOne(caratula('POL-2026-00001')).flush({ ...link, url: `${link.url}&nuevo=1` });

      expect(opened).toHaveBeenNthCalledWith(1, link.url);
      expect(opened).toHaveBeenNthCalledWith(2, `${link.url}&nuevo=1`);
    });

    it('TC-11 si el almacenamiento no responde dice que está en generación como alerta', async () => {
      await show();

      button().click();
      http.expectOne(caratula('POL-2026-00001')).flush(
        { detail: 'La carátula está en generación. Intente de nuevo en unos segundos.' },
        { status: 503, statusText: 'Service Unavailable', headers: { 'Retry-After': '5' } },
      );
      fixture.detectChanges();

      const alert = page().querySelector('[data-testid="caratula-error"]') as HTMLElement;
      expect(alert.getAttribute('role')).toBe('alert');
      expect(alert.textContent).toContain('La carátula está en generación. Intente de nuevo en unos segundos.');
      expect(text()).not.toContain('503');
      expect(status()).toBe('');
      expect(opened).not.toHaveBeenCalled();
    });

    it('TC-11 una póliza cancelada no ofrece la carátula', async () => {
      await show({ ...detail, status: 'CANCELLED' });

      expect(button()).toBeNull();
    });
  });
});
