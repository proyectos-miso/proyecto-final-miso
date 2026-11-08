import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { PolicyDetail } from '../../core/models/policy.model';
import { MonthlyReportComponent, SAVE_FILE } from './monthly-report.component';
import { PolicyOperationsComponent } from './policy-operations.component';

const JUSTIFICATION = 'El cliente pidió el cambio por escrito con sus soportes.';

// Endosos, cancelación, auditoría y reporte mensual (HU-WEB-15: AC-10, AC-11, AC-13, AC-14).
describe('PolicyOperationsComponent (HU-WEB-15)', () => {
  let fixture: ComponentFixture<PolicyOperationsComponent>;
  let http: HttpTestingController;
  let changes: number;

  const policy = {
    policy_number: 'POL-2026-00001', status: 'ACTIVE', plan: 'premium', insured_amount: 200_000_000,
  } as PolicyDetail;
  const page = () => fixture.nativeElement as HTMLElement;
  const button = (label: string) => [...page().querySelectorAll('button')].find((b) => b.textContent?.trim() === label)!;
  const setValue = (selector: string, value: string) => {
    const field = page().querySelector(selector) as HTMLInputElement | HTMLTextAreaElement;
    field.value = value;
    field.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PolicyOperationsComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    fixture = TestBed.createComponent(PolicyOperationsComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('policy', policy);
    changes = 0;
    fixture.componentInstance.changed.subscribe(() => changes++);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('AC-10 registra el endoso de suma solo si baja y con justificación', async () => {
    button('Endoso de suma asegurada').click();
    fixture.detectChanges();
    await fixture.whenStable();
    setValue('#opAmount', '250000000');
    setValue('#opJustificationSum', JUSTIFICATION);
    expect(button('Registrar endoso').disabled).toBe(true);

    setValue('#opAmount', '100000000');
    button('Registrar endoso').click();
    const call = http.expectOne((req) => req.url.endsWith('/policies/POL-2026-00001/endorsements'));
    expect(call.request.body).toEqual({ kind: 'SUM_INSURED', justification: JUSTIFICATION, insured_amount: 100_000_000 });
    call.flush({ policy_number: 'POL-2026-00001', status: 'ACTIVE', version: 2 });
    fixture.detectChanges();

    expect(page().querySelector('[role="status"]')?.textContent).toContain('versión 2');
    expect(changes).toBe(1);
  });

  it('AC-11 cancela con motivo e informa la devolución', async () => {
    button('Cancelar póliza').click();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(button('Confirmar cancelación').disabled).toBe(true);
    setValue('#opReason', JUSTIFICATION);
    button('Confirmar cancelación').click();

    const call = http.expectOne((req) => req.url.endsWith('/policies/POL-2026-00001/cancellation'));
    expect(call.request.body).toEqual({ reason: JUSTIFICATION });
    call.flush({ policy_number: 'POL-2026-00001', status: 'CANCELLED', version: 1, refund_amount: 50_000 });
    fixture.detectChanges();
    expect(page().querySelector('[role="status"]')?.textContent?.replace(/\s/g, ' ')).toContain('Se devolvieron');
  });

  it('una póliza cancelada o terminada solo deja ver la auditoría', () => {
    fixture.componentRef.setInput('policy', { ...policy, status: 'TERMINATED' });
    fixture.detectChanges();

    expect(page().textContent).toContain('no admite endosos ni cancelación');
    expect([...page().querySelectorAll('.btn-op')].map((b) => b.textContent?.trim())).toEqual(['Auditoría']);
  });

  it('AC-14 muestra la auditoría en orden con responsable y justificación', () => {
    button('Auditoría').click();
    http.expectOne((req) => req.url.endsWith('/policies/POL-2026-00001/audit')).flush({
      policy_number: 'POL-2026-00001',
      entries: [
        { occurred_at: '2026-10-01T12:00:00Z', kind: 'ISSUED', actor: 'Emisión', summary: 'Póliza emitida' },
        { occurred_at: '2026-10-05T12:00:00Z', kind: 'ENDORSEMENT_SUM_INSURED', actor: 'operaciones@solventa.example',
          summary: 'Endoso de suma asegurada', justification: JUSTIFICATION, reference: 'Versión 2' },
      ],
    });
    fixture.detectChanges();

    const items = page().querySelectorAll('.audit li');
    expect(items.length).toBe(2);
    expect(items[1].textContent).toContain('operaciones@solventa.example');
    expect(items[1].textContent).toContain(JUSTIFICATION);
  });

  it('muestra el error del servidor sin perder lo escrito', async () => {
    button('Cancelar póliza').click();
    fixture.detectChanges();
    await fixture.whenStable();
    setValue('#opReason', JUSTIFICATION);
    button('Confirmar cancelación').click();
    http.expectOne((req) => req.url.endsWith('/cancellation'))
      .flush({ detail: 'La póliza ya está cancelada o terminada.' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    expect(page().querySelector('[role="alert"]')?.textContent).toContain('ya está cancelada');
    expect((page().querySelector('#opReason') as HTMLTextAreaElement).value).toBe(JUSTIFICATION);
  });
});

describe('MonthlyReportComponent (HU-WEB-15, AC-13)', () => {
  it('descarga el CSV y el PDF del mes elegido', async () => {
    const saved: string[] = [];
    await TestBed.configureTestingModule({
      imports: [MonthlyReportComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: SAVE_FILE, useValue: (_: Blob, name: string) => saved.push(name) }],
    }).compileComponents();
    const fixture = TestBed.createComponent(MonthlyReportComponent);
    const http = TestBed.inject(HttpTestingController);
    fixture.componentInstance.period.set('2026-10');
    fixture.detectChanges();

    fixture.componentInstance.download('csv');
    http.expectOne((req) => req.url.endsWith('/reports/2026-10') && req.params.get('format') === 'csv').flush(new Blob(['a']));
    fixture.componentInstance.download('pdf');
    http.expectOne((req) => req.url.endsWith('/reports/2026-10') && req.params.get('format') === 'pdf')
      .flush(new Blob(['b']), { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(saved).toEqual(['reporte-2026-10.csv']);
    expect(fixture.nativeElement.textContent).toContain('solo para operaciones de Solventa');
    http.verify();
  });
});
