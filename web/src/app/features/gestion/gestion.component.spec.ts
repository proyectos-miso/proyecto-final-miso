import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting, TestRequest } from '@angular/common/http/testing';
import { ActivatedRoute, Router, convertToParamMap, ParamMap } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { PolicySearchItem, PolicySearchResponse } from '../../core/models/policy.model';
import { GestionComponent } from './gestion.component';

// Consulta de pólizas de operaciones (HU-WEB-08: TC-06) y apertura del detalle (HU-WEB-09: TC-08).
describe('GestionComponent (HU-WEB-08, HU-WEB-09)', () => {
  let fixture: ComponentFixture<GestionComponent>;
  let component: GestionComponent;
  let http: HttpTestingController;
  let queryParams: BehaviorSubject<ParamMap>;
  let router: { navigate: ReturnType<typeof vi.fn> };

  const item: PolicySearchItem = {
    policy_number: 'POL-2026-00001',
    holder_name: 'Carolina Prueba Gómez',
    holder_document: '******4050',
    plan: 'premium',
    status: 'ACTIVE',
    valid_from: '2026-01-10T12:00:00Z',
    valid_until: '2027-01-10T12:00:00Z',
    partner_name: null,
  };
  const b2b: PolicySearchItem = { ...item, policy_number: 'POL-2026-00003', holder_name: null, partner_name: 'Banco Aliado S.A.' };

  const page = () => fixture.nativeElement as HTMLElement;
  const pending = (): TestRequest => http.expectOne((req) => req.url.endsWith('/experience/policies/search'));
  const respond = (body: PolicySearchResponse) => {
    pending().flush(body);
    fixture.detectChanges();
  };
  const typeAndSearch = (text: string) => {
    const input = page().querySelector('#policySearch') as HTMLInputElement;
    input.value = text;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    (page().querySelector('button.btn-search') as HTMLButtonElement).click();
  };

  beforeEach(async () => {
    queryParams = new BehaviorSubject(convertToParamMap({}));
    router = { navigate: vi.fn().mockResolvedValue(true) };
    await TestBed.configureTestingModule({
      imports: [GestionComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { queryParamMap: queryParams } },
        { provide: Router, useValue: router },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(GestionComponent);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  it('TC-06 al abrir trae las pólizas más recientes y las muestra en la tabla', () => {
    const call = pending();
    expect(call.request.params.get('q')).toBe('');
    expect(call.request.params.has('status')).toBe(false);
    call.flush({ items: [item, b2b], has_more: false });
    fixture.detectChanges();

    const rows = page().querySelectorAll('tbody tr');
    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('POL-2026-00001');
    expect(rows[0].textContent).toContain('Carolina Prueba Gómez');
    expect(rows[0].textContent).toContain('******4050');
    expect(rows[0].textContent).toContain('Premium');
    expect(rows[0].textContent).toContain('Vigente');
    expect(rows[1].textContent).toContain('Sin nombre registrado');
    expect(rows[1].textContent).toContain('Banco Aliado S.A.');
    expect(page().querySelector('[role="status"]')?.textContent).toContain('2 pólizas encontradas');
    expect(page().querySelectorAll('thead th[scope="col"]').length).toBe(6);
  });

  it('TC-06 busca con el texto y el estado elegidos', async () => {
    respond({ items: [item], has_more: false });

    typeAndSearch('  1020304050 ');
    const call = pending();
    expect(call.request.params.get('q')).toBe('1020304050');
    call.flush({ items: [item], has_more: false });

    component.status.set('CANCELLED');
    component.search();
    expect(pending().request.params.get('status')).toBe('CANCELLED');
  });

  it('TC-06 sin resultados muestra el mensaje y "Limpiar filtro" vuelve a la lista inicial', () => {
    respond({ items: [item], has_more: false });
    typeAndSearch('9999999999');
    respond({ items: [], has_more: false });

    const empty = page().querySelector('[data-testid="no-results"]') as HTMLElement;
    expect(empty.textContent).toContain('No encontramos pólizas que coincidan con «9999999999»');
    expect(page().querySelector('table')).toBeNull();
    expect(page().querySelector('[role="status"]')?.textContent).toContain('Sin resultados');

    (empty.querySelector('button') as HTMLButtonElement).click();
    const call = pending();
    expect(call.request.params.get('q')).toBe('');
    call.flush({ items: [item], has_more: false });
    fixture.detectChanges();
    expect(page().querySelector('[data-testid="no-results"]')).toBeNull();
    expect(component.query()).toBe('');
  });

  it('TC-06 avisa cuando hay más de 50 resultados', () => {
    respond({ items: [item], has_more: true });

    expect(page().textContent).toContain('Se muestran las 50 pólizas más recientes');
    expect(page().querySelector('[role="status"]')?.textContent).toContain('1 o más');
  });

  it('muestra el error del servidor como alerta', () => {
    pending().flush({ detail: 'El socio no está habilitado para consultar.' }, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    const alert = page().querySelector('[role="alert"]') as HTMLElement;
    expect(alert.textContent).toContain('El socio no está habilitado para consultar.');
    expect(page().querySelector('[data-testid="no-results"]')).toBeNull();
  });

  it('TC-08 el número de cada póliza es un botón que abre el detalle con ?poliza=', () => {
    respond({ items: [item], has_more: false });

    const button = page().querySelector('#policy-POL-2026-00001') as HTMLButtonElement;
    expect(button.textContent).toContain('Ver detalle de la póliza');
    button.click();
    expect(router.navigate).toHaveBeenCalledWith([], expect.objectContaining({ queryParams: { poliza: 'POL-2026-00001' } }));
  });

  it('TC-08 con ?poliza= en la dirección abre el panel y al cerrarlo el foco vuelve al número', async () => {
    respond({ items: [item, b2b], has_more: false });
    queryParams.next(convertToParamMap({ poliza: 'POL-2026-00003' }));
    fixture.detectChanges();
    await fixture.whenStable();

    const detail = http.expectOne((req) => req.url.endsWith('/experience/policies/POL-2026-00003'));
    detail.flush({ detail: 'No encontramos la póliza.' }, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();
    expect(page().querySelector('app-policy-detail')).not.toBeNull();
    expect(page().querySelector('tr.selected')?.textContent).toContain('POL-2026-00003');

    (page().querySelector('app-policy-detail .btn-close') as HTMLButtonElement).click();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(page().querySelector('app-policy-detail')).toBeNull();
    expect(router.navigate).toHaveBeenCalledWith([], expect.objectContaining({ queryParams: { poliza: null } }));
    expect(document.activeElement?.id).toBe('policy-POL-2026-00003');
  });
});
