import {
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { finalize } from 'rxjs';
import { COP } from '../../core/money';
import { PolicyDetail } from '../../core/models/policy.model';
import { PolicyService } from '../../core/policies/policy.service';
import { extractErrorMessage } from '../../core/utils/http-error';
import { PLAN_LABELS, policyStatusLabel } from './policy-labels';

const PAYMENT_STATUS: Record<string, string> = { APPROVED: 'Aprobado', DECLINED: 'Rechazado' };
const PAYMENT_METHOD: Record<string, string> = { PSE: 'PSE', CARD: 'Tarjeta' };
const CLAIM_STATUS: Record<string, string> = {
  REPORTED: 'Reportado',
  IN_REVIEW: 'En revisión',
  APPROVED: 'Aprobado',
  REJECTED: 'Rechazado',
  PAID: 'Pagado',
};

/** Detalle de una póliza para operaciones (HU-WEB-09): todo en una vista y el titular enmascarado. */
@Component({
  selector: 'app-policy-detail',
  imports: [DatePipe],
  template: `
    <section class="detail" aria-labelledby="policyDetailTitle" (keydown.escape)="closed.emit()">
      <header>
        <h2 id="policyDetailTitle" tabindex="-1" #title>Póliza <span class="nowrap">{{ policyNumber() }}</span></h2>
        <button type="button" class="btn-close" (click)="closed.emit()">Cerrar detalle</button>
      </header>

      @if (loading()) {
        <p role="status">Cargando el detalle…</p>
      }

      @if (notFound()) {
        <div class="notice" role="alert" data-testid="not-found">
          No encontramos la póliza {{ policyNumber() }}. Verifique el número o búsquela de nuevo.
        </div>
      }

      @if (error(); as message) {
        <div class="notice" role="alert">
          <strong>No se pudo cargar el detalle</strong>
          <p>{{ message }}</p>
        </div>
      }

      @if (detail(); as d) {
        <p class="state"><span class="chip" [attr.data-status]="d.status">{{ statusLabel(d.status) }}</span></p>

        <h3>Titular</h3>
        <dl>
          <dt>Nombre</dt>
          <dd>{{ d.holder.name ?? 'Sin nombre registrado' }}</dd>
          <dt>Documento</dt>
          <dd>{{ d.holder.document_type ? d.holder.document_type + ' ' : '' }}{{ d.holder.document }}</dd>
          @if (d.holder.email) {
            <dt>Correo</dt>
            <dd>{{ d.holder.email }}</dd>
          }
          @if (d.holder.phone) {
            <dt>Teléfono</dt>
            <dd>{{ d.holder.phone }}</dd>
          }
        </dl>

        <h3>Plan y vigencia</h3>
        <dl>
          <dt>Plan</dt>
          <dd>{{ planLabels[d.plan] }}</dd>
          <dt>Vigencia</dt>
          <dd>{{ d.valid_from | date: 'dd/MM/yyyy' }} – {{ d.valid_until | date: 'dd/MM/yyyy' }}</dd>
          <dt>Socio</dt>
          <dd>{{ d.partner_name ?? 'Portal Solventa' }}</dd>
          <dt>Suma asegurada</dt>
          <dd>{{ cop(d.insured_amount) }}</dd>
          <dt>Prima mensual</dt>
          <dd>{{ cop(d.monthly_premium) }}</dd>
          <dt>Prima anual</dt>
          <dd>{{ cop(d.annual_premium) }}</dd>
        </dl>

        <h3>Coberturas contratadas</h3>
        @if (d.coverages.length === 0) {
          <p class="none">Sin coberturas registradas.</p>
        }
        <ul class="items">
          @for (coverage of d.coverages; track coverage.name) {
            <li>
              <span>{{ coverage.name }}</span>
              <strong>{{ cop(coverage.limit) }}</strong>
              @if (coverage.deductible > 0) {
                <span class="sub">Deducible {{ cop(coverage.deductible) }}</span>
              }
            </li>
          }
        </ul>

        <h3>Historial de pagos</h3>
        @if (d.payments.length === 0) {
          <p class="none">No hay pagos registrados.</p>
        } @else {
          <ul class="items">
            @for (payment of d.payments; track $index) {
              <li>
                <span>{{ payment.paid_at | date: 'dd/MM/yyyy HH:mm' }} · {{ method(payment.method) }}</span>
                <strong>{{ cop(payment.amount) }}</strong>
                <span class="sub">
                  {{ paymentStatus(payment.status) }}
                  @if (payment.receipt_number) {
                    · Recibo {{ payment.receipt_number }}
                  }
                  @if (payment.decline_reason) {
                    · {{ payment.decline_reason }}
                  }
                </span>
              </li>
            }
          </ul>
        }

        <h3>Siniestros asociados ({{ d.claims.length }})</h3>
        @if (d.claims.length === 0) {
          <p class="none" data-testid="no-claims">No hay siniestros registrados.</p>
        } @else {
          <ul class="items">
            @for (claim of d.claims; track claim.claim_number) {
              <li>
                <span>{{ claim.claim_number }}</span>
                <strong>{{ claimStatus(claim.status) }}</strong>
                <span class="sub">Reportado el {{ claim.reported_at | date: 'dd/MM/yyyy' }}</span>
              </li>
            }
          </ul>
        }
      }
    </section>
  `,
  styles: [`
    .detail { border: 1px solid var(--borde); border-radius: 12px; background: var(--superficie); padding: 20px; }
    header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 12px; }
    .nowrap { white-space: nowrap; }
    h2 { font-size: var(--t-lg); margin: 0; color: var(--texto); }
    h2:focus { outline: none; }
    h2:focus-visible { outline: 3px solid rgba(15, 109, 148, 0.35); outline-offset: 2px; }
    h3 { font-size: var(--t-sm); color: var(--n-700); margin: 20px 0 8px; }
    .btn-close { min-height: 44px; padding: 0 14px; border: 1px solid var(--n-300); border-radius: 8px; background: var(--superficie); color: var(--n-700); font-weight: 600; cursor: pointer; white-space: nowrap; }
    .btn-close:focus-visible { outline: 3px solid rgba(15, 109, 148, 0.35); outline-offset: 1px; }
    .state { margin: 8px 0 0; }
    dl { display: grid; grid-template-columns: max-content 1fr; gap: 6px 16px; margin: 0; font-size: var(--t-sm); }
    dt { color: var(--texto-sec); }
    dd { margin: 0; color: var(--texto); overflow-wrap: anywhere; }
    .items { list-style: none; margin: 0; padding: 0; font-size: var(--t-sm); }
    .items li { display: grid; grid-template-columns: 1fr auto; gap: 2px 12px; padding: 8px 0; border-bottom: 1px solid var(--borde); }
    .items li:last-child { border-bottom: none; }
    .sub { grid-column: 1 / -1; color: var(--texto-sec); font-size: var(--t-xs); }
    .none { color: var(--texto-sec); font-size: var(--t-sm); margin: 0; }
    .notice { margin-top: 12px; padding: 12px 16px; border-radius: 8px; background: #fdeeee; color: #991b1b; font-size: var(--t-sm); }
    .notice p { margin: 4px 0 0; }
    .chip { display: inline-block; padding: 2px 10px; border-radius: 999px; font-weight: 600; font-size: var(--t-xs); background: var(--n-100); color: var(--n-700); }
    .chip[data-status='ACTIVE'] { background: #e9f7ee; color: #15803d; }
    .chip[data-status='ISSUED'] { background: #fdf4e7; color: #b45309; }
    .chip[data-status='CANCELLED'] { background: #fdeeee; color: #b91c1c; }
  `],
})
export class PolicyDetailComponent {
  private readonly policies = inject(PolicyService);
  private readonly injector = inject(Injector);
  private readonly title = viewChild.required<ElementRef<HTMLElement>>('title');

  readonly policyNumber = input.required<string>();
  readonly closed = output<void>();

  readonly detail = signal<PolicyDetail | null>(null);
  readonly loading = signal(false);
  readonly notFound = signal(false);
  readonly error = signal<string | null>(null);

  readonly planLabels = PLAN_LABELS;
  readonly statusLabel = policyStatusLabel;
  readonly cop = COP.format;

  constructor() {
    effect(() => {
      const number = this.policyNumber();
      untracked(() => this.load(number));
    });
  }

  method(code: string): string {
    return PAYMENT_METHOD[code] ?? code;
  }

  paymentStatus(code: string): string {
    return PAYMENT_STATUS[code] ?? code;
  }

  claimStatus(code: string): string {
    return CLAIM_STATUS[code] ?? code;
  }

  private load(number: string): void {
    this.detail.set(null);
    this.notFound.set(false);
    this.error.set(null);
    this.loading.set(true);
    afterNextRender(() => this.title().nativeElement.focus(), { injector: this.injector });
    this.policies
      .getPolicyDetail(number)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (detail) => this.detail.set(detail),
        error: (error: HttpErrorResponse) => {
          // Un número con otro formato (422) tampoco existe para el analista.
          if (error.status === 404 || error.status === 422) {
            this.notFound.set(true);
          } else {
            this.error.set(extractErrorMessage(error, 'Intente de nuevo en unos minutos.'));
          }
        },
      });
  }
}
