import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { ClaimService } from '../../core/claims/claim.service';
import { ClaimListItem, ClaimStatus } from '../../core/models/claim.model';
import { extractErrorMessage } from '../../core/utils/http-error';
import { CLAIM_STATUS_OPTIONS, claimStatusLabel } from './claim-labels';

/** Bandeja de siniestros reportados desde la app, los más recientes primero (HU-WEB-15, AC-01). */
@Component({
  selector: 'app-claims-list',
  imports: [DatePipe, FormsModule, RouterLink],
  template: `
    <section class="claims">
      <header>
        <h1>Siniestros reportados</h1>
        <p class="subtitle">Abra un siniestro para revisar su expediente, dictaminarlo y liquidarlo.</p>
      </header>

      <div class="filters">
        <label for="claimStatus">Estado</label>
        <select id="claimStatus" name="status" [ngModel]="status()" (ngModelChange)="status.set($event); load()">
          <option value="">Todos</option>
          @for (option of statusOptions; track option.value) {
            <option [value]="option.value">{{ option.label }}</option>
          }
        </select>
      </div>

      <p class="count" role="status" aria-live="polite">{{ statusText() }}</p>

      @if (error(); as message) {
        <p class="notice" role="alert">{{ message }}</p>
      }

      @if (items().length > 0) {
        <div class="table-wrap">
          <table>
            <caption class="sr-only">Siniestros reportados</caption>
            <thead>
              <tr>
                <th scope="col">Radicado</th>
                <th scope="col">Póliza</th>
                <th scope="col">Fecha de reporte</th>
                <th scope="col">Estado</th>
                <th scope="col">Responsable</th>
              </tr>
            </thead>
            <tbody>
              @for (claim of items(); track claim.claim_number) {
                <tr>
                  <th scope="row">
                    <a [routerLink]="['/siniestros', claim.claim_number]">
                      <span class="sr-only">Abrir el expediente del siniestro </span>{{ claim.claim_number }}
                    </a>
                    @if (claim.origin === 'PARAMETRIC') {
                      <span class="tag">Paramétrico</span>
                    }
                  </th>
                  <td>{{ claim.policy_number }}</td>
                  <td>{{ claim.reported_at | date: 'dd/MM/yyyy HH:mm' }}</td>
                  <td><span class="chip" [attr.data-status]="claim.status">{{ statusLabel(claim.status) }}</span></td>
                  <td>{{ claim.assigned_to ?? 'Sin asignar' }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        @if (hasMore()) {
          <p class="more">Se muestran los 50 siniestros más recientes. Filtre por estado para ver otros.</p>
        }
      } @else if (!loading() && !error()) {
        <p class="empty" data-testid="no-claims">No hay siniestros {{ status() ? 'en estado ' + statusLabel(status()) : 'reportados' }}.</p>
      }
    </section>
  `,
  styles: [`
    .claims { max-width: 1100px; margin: 0 auto; padding: 32px 24px; }
    h1 { font-size: var(--t-xl); margin: 0 0 4px; color: var(--texto); }
    .subtitle { color: var(--texto-sec); margin: 0 0 20px; }
    .filters { display: flex; align-items: center; gap: 8px; }
    label { font-size: var(--t-sm); font-weight: 600; color: var(--n-700); }
    select { min-height: 44px; padding: 0 12px; border: 1px solid var(--n-300); border-radius: 8px; font: inherit; background: var(--superficie); }
    select:focus, a:focus-visible { outline: 3px solid rgba(15, 109, 148, 0.35); outline-offset: 1px; }
    .count { color: var(--texto-sec); font-size: var(--t-sm); margin: 12px 0; }
    .notice { padding: 12px 16px; border-radius: 8px; background: #fdeeee; color: #991b1b; }
    .table-wrap { overflow-x: auto; border: 1px solid var(--borde); border-radius: 12px; background: var(--superficie); }
    table { width: 100%; border-collapse: collapse; font-size: var(--t-sm); }
    th, td { padding: 12px 16px; text-align: left; border-bottom: 1px solid var(--borde); vertical-align: top; }
    thead th { background: var(--n-50); color: var(--n-600); font-weight: 600; }
    tbody th { font-weight: 600; white-space: nowrap; }
    tbody th a { display: inline-flex; min-height: 24px; align-items: center; color: var(--marca-600); }
    .tag { display: block; margin-top: 4px; font-size: var(--t-xs); color: #6d28d9; font-weight: 600; }
    .chip { display: inline-block; padding: 2px 10px; border-radius: 999px; font-weight: 600; font-size: var(--t-xs); background: var(--n-100); color: var(--n-700); }
    .chip[data-status='IN_REVIEW'] { background: #fdf4e7; color: #b45309; }
    .chip[data-status='REJECTED'] { background: #fdeeee; color: #b91c1c; }
    .chip[data-status='SETTLED'], .chip[data-status='PAID'] { background: #e9f7ee; color: #15803d; }
    .empty, .more { color: var(--texto-sec); font-size: var(--t-sm); }
    .sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }
  `],
})
export class ClaimsListComponent implements OnInit {
  private readonly claims = inject(ClaimService);

  readonly status = signal<ClaimStatus | ''>('');
  readonly items = signal<ClaimListItem[]>([]);
  readonly hasMore = signal(false);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly statusOptions = CLAIM_STATUS_OPTIONS;
  readonly statusLabel = claimStatusLabel;

  readonly statusText = computed(() => {
    if (this.loading()) return 'Cargando siniestros…';
    const count = this.items().length;
    return count === 0 ? '' : `${count}${this.hasMore() ? ' o más' : ''} ${count === 1 ? 'siniestro' : 'siniestros'}.`;
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.claims
      .list(this.status())
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (response) => {
          this.items.set(response.items);
          this.hasMore.set(response.has_more);
        },
        error: (error: HttpErrorResponse) => {
          this.items.set([]);
          this.error.set(extractErrorMessage(error, 'No se pudo cargar la bandeja.'));
        },
      });
  }
}
