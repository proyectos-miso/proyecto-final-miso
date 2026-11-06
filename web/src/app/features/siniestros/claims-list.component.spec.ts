import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { ClaimsListComponent } from './claims-list.component';

// Bandeja de siniestros (HU-WEB-15: AC-01).
describe('ClaimsListComponent (HU-WEB-15)', () => {
  let fixture: ComponentFixture<ClaimsListComponent>;
  let http: HttpTestingController;
  const page = () => fixture.nativeElement as HTMLElement;
  const pending = () => http.expectOne((req) => req.url.endsWith('/experience/claims'));

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ClaimsListComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    fixture = TestBed.createComponent(ClaimsListComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('AC-01 lista radicado, póliza, fecha, estado y responsable con enlace al expediente', () => {
    pending().flush({
      items: [
        { claim_number: 'SIN-2026-00002', policy_number: 'POL-2026-00002', reported_at: '2026-10-28T12:00:00Z', status: 'IN_REVIEW', origin: 'PARAMETRIC', assigned_to: 'operaciones@solventa.example' },
        { claim_number: 'SIN-2026-00001', policy_number: 'POL-2026-00001', reported_at: '2026-10-27T12:00:00Z', status: 'REPORTED', origin: 'APP', assigned_to: null },
      ],
      has_more: false,
    });
    fixture.detectChanges();

    const rows = page().querySelectorAll('tbody tr');
    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('POL-2026-00002');
    expect(rows[0].textContent).toContain('En evaluación');
    expect(rows[0].textContent).toContain('Paramétrico');
    expect(rows[1].textContent).toContain('Sin asignar');
    expect(rows[1].querySelector('a')?.getAttribute('href')).toBe('/siniestros/SIN-2026-00001');
    expect(page().querySelectorAll('thead th[scope="col"]').length).toBe(5);
    expect(page().querySelector('[role="status"]')?.textContent).toContain('2 siniestros');
  });

  it('filtra por estado y dice cuando no hay siniestros', () => {
    pending().flush({ items: [], has_more: false });
    const select = page().querySelector('#claimStatus') as HTMLSelectElement;
    select.value = select.options[3].value;
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    const call = pending();
    expect(call.request.params.get('status')).toBe('REJECTED');
    call.flush({ items: [], has_more: false });
    fixture.detectChanges();
    expect(page().querySelector('[data-testid="no-claims"]')?.textContent).toContain('No hay siniestros en estado Rechazado');
  });
});
