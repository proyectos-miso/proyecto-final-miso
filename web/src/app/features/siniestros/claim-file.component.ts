import {
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { Observable, finalize } from 'rxjs';
import { ClaimService } from '../../core/claims/claim.service';
import { ClaimDetail, ClaimEvent, Expert } from '../../core/models/claim.model';
import { COP } from '../../core/money';
import { extractErrorMessage } from '../../core/utils/http-error';
import { PLAN_LABELS, policyStatusLabel } from '../gestion/policy-labels';
import { CLAIM_EVENTS, INTEGRITY, claimStatusLabel } from './claim-labels';

const MIN_JUSTIFICATION = 20;
const OPEN = ['REPORTED', 'IN_REVIEW'];

/** Expediente del siniestro: póliza, asegurado, fotos con su huella, historial, dictamen y pago (HU-WEB-15). */
@Component({
  selector: 'app-claim-file',
  imports: [DatePipe, DecimalPipe, FormsModule, RouterLink],
  template: `
    <section class="file" aria-labelledby="claimTitle">
      <a routerLink="/siniestros" class="back">← Volver a la bandeja</a>
      <h1 id="claimTitle" tabindex="-1" #title>Siniestro {{ claimNumber() }}</h1>

      @if (loading()) {
        <p role="status">Cargando el expediente…</p>
      }
      @if (notFound()) {
        <p class="notice" role="alert" data-testid="not-found">No encontramos el siniestro {{ claimNumber() }}.</p>
      }
      @if (error(); as message) {
        <p class="notice" role="alert">{{ message }}</p>
      }

      @if (claim(); as c) {
        <p class="state">
          <span class="chip" [attr.data-status]="c.status">{{ statusLabel(c.status) }}</span>
          @if (c.origin === 'PARAMETRIC') {
            <span class="tag">Paramétrico</span>
          }
          Reportado el {{ c.reported_at | date: 'dd/MM/yyyy HH:mm' }}
        </p>

        <div class="grid">
          <div>
            <h2>Póliza</h2>
            <dl>
              <dt>Número</dt>
              <dd><a [routerLink]="['/gestion']" [queryParams]="{ poliza: c.policy_number }">{{ c.policy_number }}</a></dd>
              <dt>Plan</dt>
              <dd>{{ planLabel(c.plan) }}</dd>
              <dt>Estado</dt>
              <dd>{{ policyStatus(c.policy_status) }}</dd>
              <dt>Amparo afectado</dt>
              <dd>{{ c.coverage ? c.coverage.name + ' · hasta ' + cop(c.coverage.limit) : 'Sin indicar' }}</dd>
            </dl>
          </div>
          <div>
            <h2>Asegurado que reporta</h2>
            <dl>
              <dt>Nombre</dt>
              <dd>{{ c.insured_name ?? 'Sin nombre registrado' }}</dd>
              <dt>Documento</dt>
              <dd>{{ c.insured_document }}</dd>
              @if (c.insured_phone) {
                <dt>Teléfono</dt>
                <dd>{{ c.insured_phone }}</dd>
              }
            </dl>
          </div>
        </div>

        <h2>Descripción</h2>
        @if (c.origin === 'PARAMETRIC' && c.parametric_event) {
          <p>Evento externo: {{ eventText(c.parametric_event) }}</p>
        } @else {
          <p>{{ c.description || 'El asegurado no escribió una descripción.' }}</p>
        }

        <h2>Fotos ({{ c.photos.length }})</h2>
        @if (c.photos.length === 0) {
          <p class="none">El reporte no trae fotos.</p>
        } @else {
          <ul class="photos">
            @for (photo of c.photos; track photo.photo_id) {
              <li>
                <figure>
                  @if (photo.url) {
                    <img [src]="photo.url" [alt]="'Foto ' + photo.position + ', tomada el ' + (photo.captured_at | date: 'dd/MM/yyyy HH:mm') + ' en ' + (photo.latitude | number: '1.4-4') + ', ' + (photo.longitude | number: '1.4-4')" />
                  } @else {
                    <div class="no-image">Foto {{ photo.position }} no disponible</div>
                  }
                  <figcaption>
                    <span class="integrity" [attr.data-integrity]="photo.integrity">{{ integrity[photo.integrity] }}</span>
                    <span>Tomada el {{ photo.captured_at | date: 'dd/MM/yyyy HH:mm' }}</span>
                    <span>Ubicación {{ photo.latitude | number: '1.4-4' }}, {{ photo.longitude | number: '1.4-4' }} (±{{ photo.accuracy_m | number: '1.0-0' }} m)</span>
                    @if (photo.mock_location) {
                      <span class="warn">La ubicación viene de una app de ubicación simulada.</span>
                    }
                    @if (!photo.trusted_time) {
                      <span class="warn">La hora no se pudo confirmar con una fuente independiente del teléfono.</span>
                    }
                  </figcaption>
                </figure>
              </li>
            }
          </ul>
        }

        <h2>Historial</h2>
        <ol class="history">
          @for (event of c.events; track $index) {
            <li>
              <strong>{{ eventLabel(event.kind) }}</strong> · {{ event.created_at | date: 'dd/MM/yyyy HH:mm' }} · {{ event.actor }}
              @if (event.amount) { · {{ cop(event.amount) }} }
            </li>
          }
        </ol>

        @if (decision(); as d) {
          <section class="decision" aria-labelledby="decisionTitle" data-testid="decision">
            <h2 id="decisionTitle">Dictamen registrado</h2>
            <p class="readonly">Solo lectura: el dictamen no se puede modificar.</p>
            <dl>
              <dt>Decisión</dt>
              <dd>{{ d.kind === 'REJECTED' ? 'Rechazado' : 'Liquidado' }}</dd>
              <dt>Responsable</dt>
              <dd>{{ d.actor }}</dd>
              <dt>Fecha y hora</dt>
              <dd>{{ d.created_at | date: 'dd/MM/yyyy HH:mm:ss' }}</dd>
              @if (d.amount) {
                <dt>Valor liquidado</dt>
                <dd>{{ cop(d.amount) }}</dd>
              }
              <dt>Justificación</dt>
              <dd>{{ d.justification }}</dd>
            </dl>
          </section>
        }

        @if (c.payouts.length) {
          <h2>Pago a los beneficiarios</h2>
          <ul class="items">
            @for (payout of c.payouts; track $index) {
              <li>
                <span>{{ payout.beneficiary_name }} ({{ payout.beneficiary_document }}) · {{ payout.percentage }} %</span>
                <strong>{{ cop(payout.amount) }}</strong>
                <span class="sub">{{ payout.status === 'APPROVED' ? 'Pagado · Recibo ' + payout.receipt_number : 'Pago rechazado por el banco' }}</span>
              </li>
            }
          </ul>
          @if (c.status === 'SETTLED') {
            <button type="button" class="btn-secondary" [disabled]="busy()" (click)="retry()">Reintentar los pagos rechazados</button>
          }
        }

        @if (open()) {
          <section class="actions" aria-labelledby="actionsTitle">
            <h2 id="actionsTitle">Atender el siniestro</h2>
            <form class="assign" (ngSubmit)="assign()">
              <label for="expert">Perito para la inspección (opcional)</label>
              <select id="expert" name="expert" [ngModel]="expertId()" (ngModelChange)="expertId.set($event)">
                <option value="">Sin perito</option>
                @for (expert of experts(); track expert.expert_id) {
                  <option [value]="expert.expert_id">{{ expert.name }} · {{ expert.city }}</option>
                }
              </select>
              <button type="submit" class="btn-secondary" [disabled]="busy()">
                {{ c.assigned_to ? 'Asignar perito' : 'Tomar el caso' }}
              </button>
              @if (c.assigned_to) {
                <span class="sub">Responsable: {{ c.assigned_to }}{{ c.expert ? ' · Perito: ' + c.expert.name : '' }}</span>
              }
            </form>

            <form class="dictamen" (ngSubmit)="decide()">
              <fieldset>
                <legend>Dictamen</legend>
                <label><input type="radio" name="decision" value="APPROVE" [ngModel]="choice()" (ngModelChange)="choice.set($event)" /> Liquidar el amparo</label>
                <label><input type="radio" name="decision" value="REJECT" [ngModel]="choice()" (ngModelChange)="choice.set($event)" /> Rechazar el amparo</label>
              </fieldset>
              @if (choice() === 'APPROVE') {
                <label for="coverage">Amparo</label>
                <select id="coverage" name="coverage" [ngModel]="coverageId()" (ngModelChange)="coverageId.set($event)">
                  @for (coverage of c.coverages; track coverage.coverage_id) {
                    <option [value]="coverage.coverage_id">{{ coverage.name }} · hasta {{ cop(coverage.limit) }}</option>
                  }
                </select>
                <label for="amount">Valor a pagar (COP)</label>
                <input id="amount" name="amount" type="number" min="1" step="1000" [attr.aria-invalid]="amountError() ? 'true' : null"
                  [attr.aria-describedby]="amountError() ? 'amountError' : null"
                  [ngModel]="amount()" (ngModelChange)="amount.set($event)" />
                @if (amountError(); as message) {
                  <p id="amountError" class="field-error">{{ message }}</p>
                }
              }
              <label for="justification">Justificación (obligatoria)</label>
              <textarea id="justification" name="justification" rows="4" maxlength="2000"
                [ngModel]="justification()" (ngModelChange)="justification.set($event)"></textarea>
              <p class="sub">Mínimo {{ minJustification }} caracteres. Queda registrada con su nombre, la fecha y la hora, y no se puede cambiar.</p>
              <button type="submit" class="btn-primary" [disabled]="busy() || !canDecide()">Registrar dictamen</button>
            </form>
          </section>
        }
        <p class="result" role="status">{{ message() }}</p>
      }
    </section>
  `,
  styles: [`
    .file { max-width: 1000px; margin: 0 auto; padding: 24px; }
    .back { color: var(--marca-600); font-size: var(--t-sm); display: inline-flex; min-height: 24px; align-items: center; }
    h1 { font-size: var(--t-xl); margin: 8px 0; color: var(--texto); }
    h1:focus { outline: none; }
    h1:focus-visible { outline: 3px solid rgba(15, 109, 148, 0.35); outline-offset: 2px; }
    h2 { font-size: var(--t-base); color: var(--n-700); margin: 24px 0 8px; }
    .state { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; color: var(--texto-sec); font-size: var(--t-sm); }
    .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 0 32px; }
    dl { display: grid; grid-template-columns: max-content 1fr; gap: 6px 16px; margin: 0; font-size: var(--t-sm); }
    dt { color: var(--texto-sec); }
    dd { margin: 0; overflow-wrap: anywhere; }
    .photos { list-style: none; padding: 0; margin: 0; display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 16px; }
    figure { margin: 0; border: 1px solid var(--borde); border-radius: 12px; overflow: hidden; background: var(--superficie); }
    img, .no-image { width: 100%; aspect-ratio: 4 / 3; object-fit: cover; display: block; background: var(--n-100); }
    .no-image { display: flex; align-items: center; justify-content: center; color: var(--texto-sec); font-size: var(--t-sm); }
    figcaption { display: flex; flex-direction: column; gap: 4px; padding: 10px 12px; font-size: var(--t-xs); color: var(--n-700); }
    .integrity { font-weight: 700; }
    .integrity[data-integrity='INTACT'] { color: #15803d; }
    .integrity[data-integrity='ALTERED'] { color: #b91c1c; }
    .integrity[data-integrity='UNVERIFIED'] { color: #b45309; }
    .warn { color: #b45309; font-weight: 600; }
    .history { padding-left: 20px; font-size: var(--t-sm); }
    .history li { padding: 4px 0; }
    .decision { margin-top: 16px; padding: 16px; border: 1px solid var(--borde); border-left: 4px solid var(--marca-600); border-radius: 12px; background: var(--superficie); }
    .readonly { margin: 0 0 8px; color: var(--texto-sec); font-size: var(--t-xs); }
    .items { list-style: none; margin: 0; padding: 0; font-size: var(--t-sm); }
    .items li { display: grid; grid-template-columns: 1fr auto; gap: 2px 12px; padding: 8px 0; border-bottom: 1px solid var(--borde); }
    .sub { color: var(--texto-sec); font-size: var(--t-xs); }
    .items .sub { grid-column: 1 / -1; }
    .actions { margin-top: 24px; padding-top: 8px; border-top: 1px solid var(--borde); }
    form { display: flex; flex-direction: column; gap: 6px; margin-bottom: 16px; max-width: 560px; }
    fieldset { border: 1px solid var(--borde); border-radius: 8px; padding: 8px 12px; display: flex; gap: 16px; flex-wrap: wrap; }
    label { font-size: var(--t-sm); font-weight: 600; color: var(--n-700); }
    input[type='number'], select, textarea { min-height: 40px; padding: 8px 10px; border: 1px solid var(--n-300); border-radius: 8px; font: inherit; background: var(--superficie); }
    input:focus, select:focus, textarea:focus, button:focus-visible, a:focus-visible { outline: 3px solid rgba(15, 109, 148, 0.35); outline-offset: 1px; }
    .field-error { margin: 0; color: #b91c1c; font-size: var(--t-xs); font-weight: 600; }
    .btn-primary { align-self: flex-start; min-height: 44px; padding: 0 18px; border: none; border-radius: 8px; background: var(--marca-600); color: #fff; font-weight: 600; cursor: pointer; }
    .btn-secondary { align-self: flex-start; min-height: 40px; padding: 0 14px; border: 1px solid var(--marca-600); border-radius: 8px; background: var(--superficie); color: var(--marca-600); font-weight: 600; cursor: pointer; }
    button:disabled { opacity: 0.6; cursor: not-allowed; }
    .chip { display: inline-block; padding: 2px 10px; border-radius: 999px; font-weight: 600; font-size: var(--t-xs); background: var(--n-100); color: var(--n-700); }
    .chip[data-status='IN_REVIEW'] { background: #fdf4e7; color: #b45309; }
    .chip[data-status='REJECTED'] { background: #fdeeee; color: #b91c1c; }
    .chip[data-status='SETTLED'], .chip[data-status='PAID'] { background: #e9f7ee; color: #15803d; }
    .tag { font-size: var(--t-xs); color: #6d28d9; font-weight: 700; }
    .notice { padding: 12px 16px; border-radius: 8px; background: #fdeeee; color: #991b1b; }
    .none { color: var(--texto-sec); font-size: var(--t-sm); }
    .result { color: #15803d; font-size: var(--t-sm); }
    .result:empty { display: none; }
  `],
})
export class ClaimFileComponent {
  private readonly claims = inject(ClaimService);
  private readonly injector = inject(Injector);
  private readonly title = viewChild.required<ElementRef<HTMLElement>>('title');

  readonly claimNumber = input.required<string>();

  readonly claim = signal<ClaimDetail | null>(null);
  readonly experts = signal<Expert[]>([]);
  readonly loading = signal(false);
  readonly notFound = signal(false);
  readonly error = signal<string | null>(null);
  readonly busy = signal(false);
  readonly message = signal('');

  readonly expertId = signal('');
  readonly choice = signal<'APPROVE' | 'REJECT' | ''>('');
  readonly coverageId = signal('');
  readonly amount = signal<number | null>(null);
  readonly justification = signal('');

  readonly minJustification = MIN_JUSTIFICATION;
  readonly integrity = INTEGRITY;
  readonly statusLabel = claimStatusLabel;
  readonly policyStatus = policyStatusLabel;
  readonly cop = COP.format;

  readonly open = computed(() => OPEN.includes(this.claim()?.status ?? ''));
  readonly decision = computed<ClaimEvent | null>(
    () => [...(this.claim()?.events ?? [])].reverse().find((e) => e.kind === 'REJECTED' || e.kind === 'SETTLED') ?? null,
  );
  readonly limit = computed(() => this.claim()?.coverages.find((c) => c.coverage_id === this.coverageId())?.limit ?? 0);
  readonly amountError = computed(() => {
    const amount = Number(this.amount());
    if (this.choice() !== 'APPROVE' || !this.amount()) return null;
    if (amount <= 0) return 'El valor debe ser mayor que cero.';
    if (amount > this.limit()) return `El valor supera la suma asegurada del amparo (${this.cop(this.limit())}).`;
    return null;
  });
  readonly canDecide = computed(() => {
    if (this.justification().trim().length < MIN_JUSTIFICATION || !this.choice()) return false;
    return this.choice() === 'REJECT' || (!!this.coverageId() && Number(this.amount()) > 0 && !this.amountError());
  });

  constructor() {
    effect(() => {
      const number = this.claimNumber();
      untracked(() => this.load(number, true));
    });
    this.claims.experts().subscribe({ next: (experts) => this.experts.set(experts), error: () => this.experts.set([]) });
  }

  planLabel(plan: string): string {
    return PLAN_LABELS[plan as keyof typeof PLAN_LABELS] ?? plan;
  }

  eventLabel(kind: string): string {
    return CLAIM_EVENTS[kind] ?? kind;
  }

  eventText(event: Record<string, unknown>): string {
    return Object.entries(event).map(([key, value]) => `${key}: ${value}`).join(' · ');
  }

  assign(): void {
    this.run(this.claims.assign(this.claimNumber(), this.expertId() || null), 'Caso asignado.');
  }

  decide(): void {
    if (!this.canDecide()) return;
    const approve = this.choice() === 'APPROVE';
    this.run(
      this.claims.decide(this.claimNumber(), {
        decision: approve ? 'APPROVE' : 'REJECT',
        justification: this.justification().trim(),
        ...(approve ? { coverage_id: this.coverageId(), amount: Number(this.amount()) } : {}),
      }),
      approve ? 'Siniestro liquidado y pago ordenado a los beneficiarios.' : 'Siniestro rechazado.',
    );
  }

  retry(): void {
    this.run(this.claims.retryPayouts(this.claimNumber()), 'Se reintentaron los pagos rechazados.');
  }

  private run(request: Observable<void>, success: string): void {
    this.busy.set(true);
    this.error.set(null);
    request.pipe(finalize(() => this.busy.set(false))).subscribe({
      next: () => {
        this.message.set(success);
        this.load(this.claimNumber(), false);
      },
      error: (error: HttpErrorResponse) => this.error.set(extractErrorMessage(error, 'No se pudo registrar la operación.')),
    });
  }

  private load(number: string, focus: boolean): void {
    this.loading.set(true);
    this.notFound.set(false);
    if (focus) afterNextRender(() => this.title().nativeElement.focus(), { injector: this.injector });
    this.claims
      .detail(number)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (claim) => {
          this.claim.set(claim);
          this.coverageId.set(this.coverageId() || claim.coverage?.coverage_id || claim.coverages[0]?.coverage_id || '');
        },
        error: (error: HttpErrorResponse) => {
          this.claim.set(null);
          if (error.status === 404 || error.status === 422) this.notFound.set(true);
          else this.error.set(extractErrorMessage(error, 'No se pudo cargar el expediente.'));
        },
      });
  }
}
