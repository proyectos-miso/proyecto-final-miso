import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { DOCUMENT, formatDate } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { OperationsService } from '../../core/operations/operations.service';
import {
  DependencyName,
  DependencyState,
  DependencyStatus,
  LatencyGroup,
  LatencyGroupName,
  OperationsIndicators,
  OperationsPeriod,
} from '../../core/models/operations.model';

export const REFRESH_MS = 30_000;
// La región viva se vacía y se vuelve a escribir: así un aviso igual al anterior también se lee.
export const ANNOUNCE_DELAY_MS = 100;

const PERIOD_LABEL: Record<OperationsPeriod, string> = {
  today: 'Hoy',
  '7d': 'Últimos 7 días',
  '30d': 'Últimos 30 días',
};

const GROUP_LABEL: Record<LatencyGroupName, string> = {
  quote: 'Cotización',
  issue: 'Emisión',
  payment: 'Cobro',
  claim: 'Reporte de siniestro',
};

const DEPENDENCY_LABEL: Record<DependencyName, string> = {
  database: 'Base de datos',
  s3: 'Almacenamiento S3',
  sqs: 'Cola SQS',
  firebase: 'Firebase (avisos push)',
};

// Texto e icono: el estado no depende solo del color.
const STATE: Record<DependencyState, { icon: string; text: string }> = {
  ok: { icon: '✓', text: 'Operativa' },
  degraded: { icon: '!', text: 'Degradada' },
  down: { icon: '✕', text: 'Caída' },
  not_configured: { icon: '–', text: 'Sin configurar' },
  unchecked: { icon: '–', text: 'Sin comprobar' },
};

const COUNT = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 0 });
const RATE = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 2 });
const PERCENT = new Intl.NumberFormat('es-CO', { style: 'percent', maximumFractionDigits: 1 });
const MS = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 0 });
const NO_DATA = 'Sin datos';
const LOAD_ERROR = 'No se pudieron cargar los indicadores. Se volverá a intentar en la siguiente actualización.';
const STALE_STATE = { icon: '?', text: 'Sin datos' };

/** Tablero operativo del equipo de Solventa (FE-WEB-07): se actualiza cada 30 s solo mientras la pestaña está visible. */
@Component({
  selector: 'app-operations',
  imports: [FormsModule],
  template: `
    <section class="operations">
      <header>
        <h1>Operación</h1>
        <p class="subtitle">Indicadores del canal, latencia de las rutas críticas y estado de las dependencias. Se actualiza cada 30 segundos mientras esta pestaña está visible.</p>
      </header>

      <div class="toolbar">
        <div class="field">
          <label for="period">Periodo</label>
          <select id="period" name="period" [ngModel]="period()" (ngModelChange)="changePeriod($event)">
            @for (option of periods; track option) {
              <option [value]="option">{{ periodLabel[option] }}</option>
            }
          </select>
        </div>
        <button type="button" class="btn" [disabled]="loading()" (click)="load(true)">Actualizar ahora</button>
        @if (data()) {
          <span class="updated" data-testid="updated">{{ stale() ? staleText() : 'Actualizado a las ' + lastUpdate() }}</span>
        }
      </div>

      <!-- Solo anuncia el cambio de periodo, los errores y los cambios de estado de una dependencia, no cada refresco. -->
      <p class="status" role="status" aria-live="polite">{{ announcement() }}</p>
      @if (error(); as text) {
        <p class="notice" role="alert">{{ text }}</p>
      }

      @if (data(); as current) {
        <h2 id="kpiTitle">Indicadores · {{ periodLabel[current.period] }}</h2>
        @if (stale()) {
          <p class="stale-note" data-testid="stale-cards">{{ staleText() }}: las cifras y los estados son de la última actualización.</p>
        }
        <dl class="cards" aria-labelledby="kpiTitle" [class.stale]="stale()">
          <div class="card"><dt>Cotizaciones</dt><dd>{{ count(current.quotes) }}</dd></div>
          <div class="card"><dt>Cotizaciones por minuto</dt><dd>{{ rate(current.quotes_per_minute) }}</dd></div>
          <div class="card">
            <dt>Conversión</dt>
            <dd>{{ current.quotes === 0 ? 'Sin cotizaciones' : percent(current.conversion) }}</dd>
          </div>
          <div class="card"><dt>Pólizas emitidas</dt><dd>{{ count(current.policies_issued) }}</dd></div>
          <div class="card"><dt>Siniestros en curso</dt><dd>{{ count(current.open_claims) }}</dd></div>
          <div class="card"><dt>Eventos pendientes de publicar</dt><dd>{{ count(current.outbox_pending) }}</dd></div>
        </dl>

        <div class="table-wrap">
          <table>
            <caption>Latencia de las rutas críticas frente a su meta (últimas 500 respuestas exitosas por ruta en esta réplica; la meta se compara con p95){{ stale() ? ' · ' + staleText() : '' }}</caption>
            <thead>
              <tr>
                <th scope="col">Ruta</th>
                <th scope="col">Muestras</th>
                <th scope="col">p50</th>
                <th scope="col">p95</th>
                <th scope="col">p99</th>
                <th scope="col">Meta</th>
                <th scope="col">Estado</th>
              </tr>
            </thead>
            <tbody>
              @for (row of current.latency; track row.group) {
                <tr>
                  <th scope="row">{{ groupLabel[row.group] }}</th>
                  <td>{{ count(row.samples) }}</td>
                  <td>{{ ms(row.p50_ms) }}</td>
                  <td>{{ ms(row.p95_ms) }}</td>
                  <td>{{ ms(row.p99_ms) }}</td>
                  <td>{{ ms(row.target_ms) }}</td>
                  <td>
                    <span class="state" [attr.data-state]="latencyState(row)">
                      <span aria-hidden="true">{{ latencyIcon(row) }}</span> {{ latencyText(row) }}
                    </span>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>

        <div class="table-wrap">
          <table>
            <caption>Estado de las dependencias{{ stale() ? ' · ' + staleText() : '' }}</caption>
            <thead>
              <tr>
                <th scope="col">Dependencia</th>
                <th scope="col">Estado</th>
                <th scope="col">Tiempo de respuesta</th>
              </tr>
            </thead>
            <tbody>
              @for (dependency of current.dependencies; track dependency.name) {
                <tr>
                  <th scope="row">{{ dependencyLabel[dependency.name] }}</th>
                  <td>
                    @if (stale()) {
                      <span class="state" data-state="stale">
                        <span aria-hidden="true">{{ staleState.icon }}</span> {{ staleText() }}
                      </span>
                    } @else {
                      <span class="state" [attr.data-state]="dependency.status">
                        <span aria-hidden="true">{{ state[dependency.status].icon }}</span> {{ state[dependency.status].text }}
                      </span>
                    }
                  </td>
                  <td>{{ stale() ? noData : ms(dependency.latency_ms) }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      } @else if (loading()) {
        <p class="empty">Cargando indicadores…</p>
      }
    </section>
  `,
  styles: [`
    .operations { max-width: 1100px; margin: 0 auto; padding: 32px 24px; }
    h1 { font-size: var(--t-xl); margin: 0 0 4px; color: var(--texto); }
    h2 { font-size: var(--t-md, 1rem); margin: 0 0 12px; color: var(--texto); }
    .subtitle { color: var(--texto-sec); margin: 0 0 20px; }
    .toolbar { display: flex; flex-wrap: wrap; gap: 12px; align-items: flex-end; margin: 0 0 12px; }
    .field { display: flex; flex-direction: column; gap: 4px; }
    label { font-size: var(--t-sm); font-weight: 600; color: var(--n-700); }
    select { min-height: 44px; padding: 0 12px; border: 1px solid var(--n-300); border-radius: 8px; font: inherit; background: var(--superficie); }
    .btn { min-height: 44px; padding: 0 14px; border: 1px solid var(--marca-600); border-radius: 8px; background: var(--superficie); color: var(--marca-600); font-weight: 600; cursor: pointer; }
    button:disabled { opacity: 0.6; cursor: not-allowed; }
    button:focus-visible, select:focus { outline: 3px solid #0f6d94; outline-offset: 2px; }
    .updated, .empty { color: var(--texto-sec); font-size: var(--t-sm); }
    .status { color: var(--texto-sec); font-size: var(--t-sm); margin: 0 0 12px; min-height: 1em; }
    .stale-note { color: #92400e; font-weight: 600; margin: 0 0 12px; }
    .cards.stale .card { border-style: dashed; }
    .cards.stale dd { color: var(--texto-sec); }
    .notice { padding: 12px 16px; border-radius: 8px; background: #fdeeee; color: #991b1b; }
    .cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 12px; margin: 0 0 24px; }
    .card { border: 1px solid var(--borde); border-radius: 12px; background: var(--superficie); padding: 14px 16px; }
    .card dt { color: var(--n-600); font-size: var(--t-sm); font-weight: 600; }
    .card dd { margin: 6px 0 0; font-size: var(--t-xl); font-weight: 700; color: var(--texto); }
    .table-wrap { overflow-x: auto; border: 1px solid var(--borde); border-radius: 12px; background: var(--superficie); margin: 0 0 24px; }
    table { width: 100%; border-collapse: collapse; font-size: var(--t-sm); }
    caption { text-align: left; padding: 12px 16px; font-weight: 600; color: var(--n-700); }
    th, td { padding: 12px 16px; text-align: left; border-bottom: 1px solid var(--borde); }
    thead th { background: var(--n-50); color: var(--n-600); font-weight: 600; }
    tbody th { font-weight: 600; }
    .state { display: inline-block; padding: 2px 10px; border-radius: 999px; font-weight: 600; font-size: var(--t-xs); background: var(--n-100); color: var(--n-700); }
    .state[data-state='ok'] { background: #e9f7ee; color: #15803d; }
    .state[data-state='degraded'] { background: #fffaf2; color: #92400e; }
    .state[data-state='down'], .state[data-state='over'] { background: #fdeeee; color: #b91c1c; }
  `],
})
export class OperationsComponent implements OnInit, OnDestroy {
  private readonly operations = inject(OperationsService);
  private readonly document = inject(DOCUMENT);
  private timer: ReturnType<typeof setInterval> | null = null;
  private request: Subscription | null = null;
  private analystRequest = false;
  private announceTimer: ReturnType<typeof setTimeout> | null = null;

  readonly periods: OperationsPeriod[] = ['today', '7d', '30d'];
  readonly periodLabel = PERIOD_LABEL;
  readonly groupLabel = GROUP_LABEL;
  readonly dependencyLabel = DEPENDENCY_LABEL;
  readonly state = STATE;
  readonly staleState = STALE_STATE;
  readonly noData = NO_DATA;

  readonly period = signal<OperationsPeriod>('today');
  readonly data = signal<OperationsIndicators | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly announcement = signal('');
  /** Falló la última actualización: lo que se ve es de antes y se marca así. */
  readonly stale = signal(false);
  readonly lastUpdate = computed(() => {
    const current = this.data();
    return current ? formatDate(current.generated_at, 'HH:mm:ss', 'en-US') : '';
  });
  readonly staleText = computed(() => `Sin datos desde ${this.lastUpdate()}`);

  private readonly onVisibilityChange = () => {
    if (this.visible()) {
      this.load(false);
      this.startTimer();
    } else {
      this.stopTimer();
    }
  };

  ngOnInit(): void {
    this.load(true);
    this.document.addEventListener('visibilitychange', this.onVisibilityChange);
    this.startTimer();
  }

  ngOnDestroy(): void {
    this.stopTimer();
    this.document.removeEventListener('visibilitychange', this.onVisibilityChange);
    this.request?.unsubscribe();
    if (this.announceTimer !== null) clearTimeout(this.announceTimer);
  }

  changePeriod(period: OperationsPeriod): void {
    this.period.set(period);
    this.load(true);
  }

  /**
   * `announce` marca una carga pedida por el analista; el refresco automático solo anuncia lo que cambió y se omite
   * mientras la del analista está en curso, para no cancelarla.
   */
  load(announce: boolean): void {
    if (!announce && this.analystRequest) return;
    this.request?.unsubscribe();
    this.analystRequest = announce;
    this.loading.set(true);
    const period = this.period();
    this.request = this.operations.indicators(period).subscribe({
      next: (indicators) => {
        const recovered = this.stale();
        const changed = this.changedDependencies(indicators.dependencies);
        this.data.set(indicators);
        this.stale.set(false);
        this.error.set(null);
        this.finish();
        if (announce) {
          this.announce(`Indicadores de ${PERIOD_LABEL[period].toLowerCase()} actualizados.`);
        } else if (recovered || changed.length > 0) {
          const parts = recovered ? ['Indicadores actualizados de nuevo.'] : [];
          if (changed.length > 0) parts.push(`Cambió el estado de: ${changed.join(', ')}.`);
          this.announce(parts.join(' '));
        }
      },
      error: () => {
        this.finish();
        if (this.data() === null) {
          this.error.set(LOAD_ERROR);
          return;
        }
        this.stale.set(true);
        this.error.set(`No se pudieron cargar los indicadores. ${this.staleText()}; se volverá a intentar en la siguiente actualización.`);
      },
    });
  }

  count(value: number | null): string {
    return value === null ? NO_DATA : COUNT.format(value);
  }

  rate(value: number | null): string {
    return value === null ? NO_DATA : RATE.format(value);
  }

  percent(value: number | null): string {
    return value === null ? NO_DATA : PERCENT.format(value);
  }

  ms(value: number | null): string {
    return value === null ? NO_DATA : `${MS.format(value)} ms`;
  }

  latencyState(row: LatencyGroup): 'ok' | 'over' | 'none' {
    return row.within_target === null ? 'none' : row.within_target ? 'ok' : 'over';
  }

  latencyIcon(row: LatencyGroup): string {
    return { ok: '✓', over: '✕', none: '–' }[this.latencyState(row)];
  }

  latencyText(row: LatencyGroup): string {
    return { ok: 'Dentro de la meta', over: 'Sobre la meta', none: NO_DATA }[this.latencyState(row)];
  }

  private finish(): void {
    this.loading.set(false);
    this.analystRequest = false;
  }

  private announce(text: string): void {
    if (this.announceTimer !== null) clearTimeout(this.announceTimer);
    this.announcement.set('');
    this.announceTimer = setTimeout(() => {
      this.announceTimer = null;
      this.announcement.set(text);
    }, ANNOUNCE_DELAY_MS);
  }

  private changedDependencies(next: DependencyStatus[]): string[] {
    const previous = new Map((this.data()?.dependencies ?? []).map((item) => [item.name, item.status]));
    if (previous.size === 0) return [];
    return next
      .filter((item) => previous.get(item.name) !== item.status)
      .map((item) => `${DEPENDENCY_LABEL[item.name]} (${STATE[item.status].text})`);
  }

  private visible(): boolean {
    return this.document.visibilityState !== 'hidden';
  }

  private startTimer(): void {
    if (this.timer !== null || !this.visible()) return;
    this.timer = setInterval(() => (this.visible() ? this.load(false) : this.stopTimer()), REFRESH_MS);
  }

  private stopTimer(): void {
    if (this.timer !== null) {
      clearInterval(this.timer);
      this.timer = null;
    }
  }
}
