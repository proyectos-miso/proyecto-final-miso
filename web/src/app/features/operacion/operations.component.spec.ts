import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { DependencyStatus, OperationsIndicators } from '../../core/models/operations.model';
import { OperationsComponent, REFRESH_MS } from './operations.component';

// Tablero operativo (HOTFIX-tablero-operativo, FE-WEB-07).
describe('OperationsComponent (FE-WEB-07)', () => {
  let fixture: ComponentFixture<OperationsComponent>;
  let http: HttpTestingController;
  let visibility: DocumentVisibilityState;
  const page = () => fixture.nativeElement as HTMLElement;
  const pending = () => http.match((req) => req.url.endsWith('/experience/operations/indicators'));
  const one = (): TestRequest => {
    const calls = pending();
    expect(calls.length).toBe(1);
    return calls[0];
  };
  const setVisibility = (state: DocumentVisibilityState) => {
    visibility = state;
    document.dispatchEvent(new Event('visibilitychange'));
  };

  const DEPENDENCIES: DependencyStatus[] = [
    { name: 'database', status: 'ok', latency_ms: 3 },
    { name: 's3', status: 'degraded', latency_ms: 640 },
    { name: 'sqs', status: 'down', latency_ms: 1000 },
    { name: 'firebase', status: 'not_configured', latency_ms: null },
  ];

  const indicators = (overrides: Partial<OperationsIndicators> = {}): OperationsIndicators => ({
    period: 'today',
    since: '2026-11-15T00:00:00-05:00',
    generated_at: '2026-11-15T15:00:00Z',
    quotes: 120,
    quotes_per_minute: 0.25,
    conversion: 0.25,
    policies_issued: 30,
    open_claims: 4,
    outbox_pending: 2,
    latency: [
      { group: 'quote', samples: 500, p50_ms: 120, p95_ms: 380, p99_ms: 900, target_ms: 400, within_target: true },
      { group: 'issue', samples: 40, p50_ms: 700, p95_ms: 1300, p99_ms: 1500, target_ms: 1000, within_target: false },
      { group: 'payment', samples: 0, p50_ms: null, p95_ms: null, p99_ms: null, target_ms: 1000, within_target: null },
      { group: 'claim', samples: 3, p50_ms: 200, p95_ms: 300, p99_ms: 300, target_ms: 1000, within_target: true },
    ],
    dependencies: DEPENDENCIES,
    ...overrides,
  });

  beforeEach(async () => {
    vi.useFakeTimers();
    visibility = 'visible';
    Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => visibility });
    await TestBed.configureTestingModule({
      imports: [OperationsComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    fixture = TestBed.createComponent(OperationsComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture.destroy();
    http.verify();
    vi.useRealTimers();
    delete (document as unknown as { visibilityState?: unknown }).visibilityState;
  });

  it('muestra las tarjetas, la tabla de latencia y el semáforo con texto e icono', () => {
    const call = one();
    expect(call.request.params.get('period')).toBe('today');
    call.flush(indicators());
    fixture.detectChanges();

    const cards = Array.from(page().querySelectorAll('.cards .card')).map(
      (card) => `${card.querySelector('dt')?.textContent?.trim()} ${card.querySelector('dd')?.textContent?.trim()}`,
    );
    expect(cards).toContain('Cotizaciones 120');
    expect(cards.some((text) => text?.startsWith('Conversión 25'))).toBe(true);
    expect(cards).toContain('Siniestros en curso 4');

    const tables = page().querySelectorAll('table');
    expect(tables.length).toBe(2);
    expect(tables[0].querySelector('caption')?.textContent).toContain('Latencia de las rutas críticas');
    expect(tables[0].querySelectorAll('thead th[scope="col"]').length).toBe(7);
    const latency = Array.from(tables[0].querySelectorAll('tbody tr'));
    expect(latency[0].querySelector('th[scope="row"]')?.textContent).toContain('Cotización');
    expect(latency[0].textContent).toContain('Dentro de la meta');
    expect(latency[1].textContent).toContain('Sobre la meta');
    expect(latency[2].textContent).toContain('Sin datos');

    expect(tables[1].querySelector('caption')?.textContent).toContain('Estado de las dependencias');
    const states = Array.from(tables[1].querySelectorAll('tbody tr')).map((row) => row.querySelector('.state'));
    expect(states.map((state) => state?.textContent?.replace(/\s+/g, ' ').trim())).toEqual([
      '✓ Operativa',
      '! Degradada',
      '✕ Caída',
      '– Sin configurar',
    ]);
    expect(states.every((state) => state?.querySelector('[aria-hidden="true"]'))).toBe(true);
  });

  it('sin cotizaciones lo dice en lugar de mostrar una conversión', () => {
    one().flush(indicators({ quotes: 0, quotes_per_minute: 0, conversion: null }));
    fixture.detectChanges();
    expect(page().querySelector('.cards')?.textContent).toContain('Sin cotizaciones');
  });

  it('se actualiza cada 30 segundos sin anunciar cada refresco', () => {
    one().flush(indicators());
    fixture.detectChanges();
    const status = page().querySelector('[role="status"]') as HTMLElement;
    expect(status.getAttribute('aria-live')).toBe('polite');
    expect(status.textContent).toContain('Indicadores de hoy actualizados.');

    vi.advanceTimersByTime(REFRESH_MS - 1);
    expect(pending().length).toBe(0);
    vi.advanceTimersByTime(1);
    one().flush(indicators());
    fixture.detectChanges();
    vi.advanceTimersByTime(REFRESH_MS);
    one().flush(indicators());
    fixture.detectChanges();

    expect(status.textContent).toContain('Indicadores de hoy actualizados.');
  });

  it('anuncia solo las dependencias que cambiaron de estado en un refresco', () => {
    one().flush(indicators());
    fixture.detectChanges();

    vi.advanceTimersByTime(REFRESH_MS);
    one().flush(indicators({ dependencies: DEPENDENCIES.map((item) => (item.name === 'sqs' ? { ...item, status: 'ok' } : item)) }));
    fixture.detectChanges();

    expect(page().querySelector('[role="status"]')?.textContent?.trim()).toBe('Cambió el estado de: Cola SQS (Operativa).');
  });

  it('no consulta mientras la pestaña está oculta y se pone al día al volver', () => {
    one().flush(indicators());

    setVisibility('hidden');
    vi.advanceTimersByTime(REFRESH_MS * 3);
    expect(pending().length).toBe(0);
    expect(vi.getTimerCount()).toBe(0);

    setVisibility('visible');
    one().flush(indicators());
    vi.advanceTimersByTime(REFRESH_MS);
    one().flush(indicators());
  });

  it('al destruir el componente detiene el temporizador y deja de escuchar la visibilidad', () => {
    one().flush(indicators());
    // Deja correr las tareas internas de Angular: solo queda el refresco de 30 s.
    vi.advanceTimersByTime(1);
    expect(vi.getTimerCount()).toBe(1);

    fixture.destroy();
    vi.advanceTimersByTime(1);

    expect(vi.getTimerCount()).toBe(0);
    vi.advanceTimersByTime(REFRESH_MS * 3);
    setVisibility('hidden');
    setVisibility('visible');
    expect(pending().length).toBe(0);
  });

  it('cambiar el periodo consulta de nuevo y lo anuncia', () => {
    one().flush(indicators());
    fixture.detectChanges();

    const select = page().querySelector('#period') as HTMLSelectElement;
    expect(page().querySelector('label[for="period"]')?.textContent).toContain('Periodo');
    select.value = '7d';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    const call = one();
    expect(call.request.params.get('period')).toBe('7d');
    call.flush(indicators({ period: '7d' }));
    fixture.detectChanges();
    expect(page().querySelector('[role="status"]')?.textContent).toContain('Indicadores de últimos 7 días actualizados.');
  });

  it('un error muestra un aviso genérico y conserva los últimos datos', () => {
    one().flush(indicators());
    fixture.detectChanges();

    vi.advanceTimersByTime(REFRESH_MS);
    one().flush({ detail: 'detalle interno' }, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    const alert = page().querySelector('[role="alert"]');
    expect(alert?.textContent).toContain('No se pudieron cargar los indicadores');
    expect(alert?.textContent).not.toContain('detalle interno');
    expect(page().querySelectorAll('table').length).toBe(2);
  });
});
