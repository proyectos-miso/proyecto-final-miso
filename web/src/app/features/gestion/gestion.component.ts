import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { finalize } from 'rxjs';
import { PlanTier } from '../../core/models/quote.model';
import { PolicySearchItem, PolicyStatus } from '../../core/models/policy.model';
import { PolicyService } from '../../core/policies/policy.service';
import { extractErrorMessage } from '../../core/utils/http-error';

/** Consulta de pólizas de operaciones (HU-WEB-08): busca por documento, número, socio o titular. */
@Component({
  selector: 'app-gestion',
  imports: [DatePipe, FormsModule],
  template: `
    <section class="gestion">
      <header>
        <h1>Consulta de pólizas</h1>
        <p class="subtitle">Busque por documento del cliente, número de póliza, socio de distribución o titular.</p>
      </header>

      <form class="search" role="search" (ngSubmit)="search()">
        <div class="field grow">
          <label for="policySearch">Buscar</label>
          <input
            id="policySearch"
            name="q"
            type="search"
            maxlength="80"
            autocomplete="off"
            placeholder="Documento, número, socio o titular"
            [ngModel]="query()"
            (ngModelChange)="query.set($event)"
          />
        </div>
        <div class="field">
          <label for="statusFilter">Estado</label>
          <select id="statusFilter" name="status" [ngModel]="status()" (ngModelChange)="status.set($event); search()">
            <option value="">Todos</option>
            @for (option of statusOptions; track option.value) {
              <option [value]="option.value">{{ option.label }}</option>
            }
          </select>
        </div>
        <button type="submit" class="btn-search" [disabled]="loading()">{{ loading() ? 'Buscando…' : 'Buscar' }}</button>
      </form>

      <p class="count" role="status" aria-live="polite">{{ statusText() }}</p>

      @if (error(); as message) {
        <div class="notice notice-error" role="alert">
          <div>
            <strong>No se pudo consultar las pólizas</strong>
            <p>{{ message }}</p>
          </div>
        </div>
      }

      @if (noResults()) {
        <div class="empty" data-testid="no-results">
          <p>No encontramos pólizas que coincidan con «{{ searchedQuery() }}»{{ status() ? ' en estado ' + statusLabel(status()) : '' }}.</p>
          <button type="button" class="btn-clear" (click)="clear()">Limpiar filtro</button>
        </div>
      }

      @if (items().length > 0) {
        <div class="table-wrap">
          <table>
            <caption class="sr-only">Pólizas encontradas</caption>
            <thead>
              <tr>
                <th scope="col">Póliza</th>
                <th scope="col">Titular</th>
                <th scope="col">Plan</th>
                <th scope="col">Estado</th>
                <th scope="col">Vigencia</th>
                <th scope="col">Socio</th>
              </tr>
            </thead>
            <tbody>
              @for (item of items(); track item.policy_number) {
                <tr>
                  <th scope="row">{{ item.policy_number }}</th>
                  <td>
                    {{ item.holder_name ?? 'Sin nombre registrado' }}
                    <span class="doc">Documento {{ item.holder_document }}</span>
                  </td>
                  <td>{{ planLabels[item.plan] }}</td>
                  <td><span class="chip" [attr.data-status]="item.status">{{ statusLabel(item.status) }}</span></td>
                  <td>{{ item.valid_from | date: 'dd/MM/yyyy' }} – {{ item.valid_until | date: 'dd/MM/yyyy' }}</td>
                  <td>{{ item.partner_name ?? 'Portal Solventa' }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        @if (hasMore()) {
          <p class="more">Se muestran las 50 pólizas más recientes. Refine la búsqueda para ver otras.</p>
        }
      }
    </section>
  `,
  styles: [`
    .gestion { max-width: 1100px; margin: 0 auto; padding: 32px 24px; }
    h1 { font-size: var(--t-xl); margin: 0 0 4px; color: var(--texto); }
    .subtitle { color: var(--texto-sec); margin: 0 0 24px; }
    .search { display: flex; gap: 12px; align-items: flex-end; flex-wrap: wrap; margin-bottom: 12px; }
    .field { display: flex; flex-direction: column; gap: 6px; }
    .field.grow { flex: 1; min-width: 240px; }
    label { font-size: var(--t-sm); font-weight: 600; color: var(--n-700); }
    input, select { min-height: 44px; padding: 0 12px; border: 1px solid var(--n-300); border-radius: 8px; font-size: var(--t-base); background: var(--superficie); }
    input:focus, select:focus, button:focus-visible { outline: 3px solid rgba(15, 109, 148, 0.35); outline-offset: 1px; }
    .btn-search { min-height: 44px; padding: 0 20px; border: none; border-radius: 8px; background: var(--marca-600); color: #fff; font-weight: 600; cursor: pointer; }
    .btn-search:disabled { opacity: 0.7; cursor: progress; }
    .count { color: var(--texto-sec); font-size: var(--t-sm); margin: 4px 0 16px; }
    .empty { padding: 24px; border: 1px dashed var(--n-300); border-radius: 12px; text-align: center; color: var(--n-700); }
    .btn-clear { min-height: 44px; padding: 0 16px; border: 1px solid var(--marca-600); border-radius: 8px; background: var(--superficie); color: var(--marca-600); font-weight: 600; cursor: pointer; }
    .table-wrap { overflow-x: auto; border: 1px solid var(--borde); border-radius: 12px; background: var(--superficie); }
    table { width: 100%; border-collapse: collapse; font-size: var(--t-sm); }
    th, td { padding: 12px 16px; text-align: left; border-bottom: 1px solid var(--borde); vertical-align: top; }
    thead th { background: var(--n-50); color: var(--n-600); font-weight: 600; }
    tbody th { font-weight: 600; color: var(--texto); white-space: nowrap; }
    .doc { display: block; color: var(--texto-sec); font-size: var(--t-xs); }
    .chip { display: inline-block; padding: 2px 10px; border-radius: 999px; font-weight: 600; font-size: var(--t-xs); background: var(--n-100); color: var(--n-700); }
    .chip[data-status='ACTIVE'] { background: #e9f7ee; color: #15803d; }
    .chip[data-status='ISSUED'] { background: #fdf4e7; color: #b45309; }
    .chip[data-status='CANCELLED'] { background: #fdeeee; color: #b91c1c; }
    .more { color: var(--texto-sec); font-size: var(--t-sm); }
    .sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }
  `],
})
export class GestionComponent implements OnInit {
  private readonly policies = inject(PolicyService);

  readonly query = signal('');
  readonly status = signal<PolicyStatus | ''>('');
  readonly items = signal<PolicySearchItem[]>([]);
  readonly hasMore = signal(false);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly searched = signal(false);
  readonly searchedQuery = signal('');

  readonly statusOptions: { value: PolicyStatus; label: string }[] = [
    { value: 'ACTIVE', label: 'Vigente' },
    { value: 'ISSUED', label: 'Pendiente de pago' },
    { value: 'CANCELLED', label: 'Cancelada' },
  ];
  readonly planLabels: Record<PlanTier, string> = { basic: 'Básico', standard: 'Estándar', premium: 'Premium' };

  readonly noResults = computed(() => this.searched() && !this.loading() && !this.error() && this.items().length === 0);
  readonly statusText = computed(() => {
    if (this.loading()) return 'Buscando pólizas…';
    if (!this.searched() || this.error()) return '';
    const count = this.items().length;
    if (count === 0) return 'Sin resultados.';
    return `${count}${this.hasMore() ? ' o más' : ''} ${count === 1 ? 'póliza encontrada' : 'pólizas encontradas'}.`;
  });

  ngOnInit(): void {
    this.search();
  }

  statusLabel(status: string): string {
    return this.statusOptions.find((option) => option.value === status)?.label ?? status;
  }

  search(): void {
    const query = this.query().trim();
    this.loading.set(true);
    this.error.set(null);
    this.policies
      .searchPolicies(query, this.status())
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (response) => {
          this.items.set(response.items);
          this.hasMore.set(response.has_more);
          this.searchedQuery.set(query);
          this.searched.set(true);
        },
        error: (error: HttpErrorResponse) => {
          this.items.set([]);
          this.hasMore.set(false);
          this.error.set(extractErrorMessage(error, 'Intente de nuevo en unos minutos.'));
        },
      });
  }

  clear(): void {
    this.query.set('');
    this.status.set('');
    this.search();
    (document.getElementById('policySearch') as HTMLInputElement | null)?.focus();
  }
}
