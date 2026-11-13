import { Component, computed, inject, input, output, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable, finalize } from 'rxjs';
import { COP } from '../../core/money';
import { AuditEntry, Beneficiary, PolicyDetail } from '../../core/models/policy.model';
import { PolicyService } from '../../core/policies/policy.service';
import { extractErrorMessage } from '../../core/utils/http-error';
import { BeneficiariesEditorComponent } from '../shared/beneficiaries-editor.component';

type Panel = 'beneficiaries' | 'sum' | 'cancel' | 'audit' | null;
const MIN_JUSTIFICATION = 20;
const RENEWAL_WINDOW_MS = 30 * 24 * 60 * 60 * 1000;

/** Operaciones del analista sobre la póliza: endosos, cancelación y auditoría (HU-WEB-15). */
@Component({
  selector: 'app-policy-operations',
  imports: [DatePipe, FormsModule, BeneficiariesEditorComponent],
  template: `
    <h3>Operaciones</h3>
    @if (closed()) {
      <p class="none">La póliza está {{ policy().status === 'CANCELLED' ? 'cancelada' : 'terminada' }}: no admite endosos ni cancelación.</p>
    }
    <div class="actions">
      @if (!closed()) {
        <button type="button" class="btn-op" [attr.aria-expanded]="panel() === 'beneficiaries'" (click)="toggle('beneficiaries')">Endoso de beneficiarios</button>
        <button type="button" class="btn-op" [attr.aria-expanded]="panel() === 'sum'" (click)="toggle('sum')">Endoso de suma asegurada</button>
        @if (renewable()) {
          <button type="button" class="btn-op" [disabled]="busy()" (click)="renew()">Renovar por un año</button>
        }
        <button type="button" class="btn-op danger" [attr.aria-expanded]="panel() === 'cancel'" (click)="toggle('cancel')">Cancelar póliza</button>
      }
      <button type="button" class="btn-op" [attr.aria-expanded]="panel() === 'audit'" (click)="toggle('audit')">Auditoría</button>
    </div>

    <p class="result" role="status">{{ message() }}</p>
    @if (error(); as text) {
      <p class="notice" role="alert">{{ text }}</p>
    }

    @if (panel() === 'beneficiaries') {
      <form class="op" (ngSubmit)="endorseBeneficiaries()">
        <app-beneficiaries-editor legend="Beneficiarios nuevos" prefix="endoso" (changed)="beneficiaries.set($event)" />
        <label for="opJustificationBen">Justificación del endoso</label>
        <textarea id="opJustificationBen" name="justification" rows="3" maxlength="2000"
          [ngModel]="justification()" (ngModelChange)="justification.set($event)"></textarea>
        <p class="hint">Mínimo {{ minJustification }} caracteres.</p>
        <button type="submit" class="btn-primary" [disabled]="busy() || !beneficiaries() || !justified()">Registrar endoso</button>
      </form>
    }

    @if (panel() === 'sum') {
      <form class="op" (ngSubmit)="endorseSum()">
        <label for="opAmount">Nueva suma asegurada (menor que {{ cop(policy().insured_amount) }})</label>
        <input id="opAmount" name="amount" type="number" min="1" [max]="policy().insured_amount - 1" step="1000000"
          [ngModel]="amount()" (ngModelChange)="amount.set($event)" />
        <p class="hint">La prima no cambia: bajar la suma no requiere volver a suscribir.</p>
        <label for="opJustificationSum">Justificación del endoso</label>
        <textarea id="opJustificationSum" name="justification" rows="3" maxlength="2000"
          [ngModel]="justification()" (ngModelChange)="justification.set($event)"></textarea>
        <button type="submit" class="btn-primary" [disabled]="busy() || !validAmount() || !justified()">Registrar endoso</button>
      </form>
    }

    @if (panel() === 'cancel') {
      <form class="op" (ngSubmit)="cancel()">
        <p class="hint">Se devuelve al titular la prima de los días que faltan del mes pagado. La cancelación no se puede deshacer.</p>
        <label for="opReason">Motivo de la cancelación</label>
        <textarea id="opReason" name="reason" rows="3" maxlength="2000"
          [ngModel]="justification()" (ngModelChange)="justification.set($event)"></textarea>
        <button type="submit" class="btn-primary danger" [disabled]="busy() || !justified()">Confirmar cancelación</button>
      </form>
    }

    @if (panel() === 'audit') {
      @if (audit(); as entries) {
        <ol class="audit" aria-label="Decisiones sobre la póliza">
          @for (entry of entries; track $index) {
            <li>
              <span><strong>{{ entry.summary }}</strong> · {{ entry.occurred_at | date: 'dd/MM/yyyy HH:mm' }}</span>
              <span class="sub">
                {{ entry.actor }}
                @if (entry.amount) { · {{ cop(entry.amount) }} }
                @if (entry.reference) { · {{ entry.reference }} }
              </span>
              @if (entry.justification) {
                <span class="sub just">«{{ entry.justification }}»</span>
              }
            </li>
          }
        </ol>
      }
    }
  `,
  styles: [`
    h3 { margin: 0 0 12px; font-size: var(--t-md); font-weight: 700; color: var(--n-900); }
    .actions { display: flex; flex-wrap: wrap; gap: 8px; }
    .btn-op { min-height: 38px; padding: 0 14px; border: 1px solid var(--borde); border-radius: 8px; background: #fff; color: var(--n-800); font-size: var(--t-xs); font-weight: 600; cursor: pointer; transition: all 0.15s; }
    .btn-op:hover { background: var(--fondo); }
    .btn-op[aria-expanded='true'] { background: var(--marca-600); color: #fff; }
    .btn-op.danger { border-color: #b91c1c; color: #b91c1c; }
    .btn-op.danger[aria-expanded='true'] { background: #b91c1c; color: #fff; }
    button:focus-visible, input:focus, textarea:focus { outline: 3px solid rgba(15, 109, 148, 0.35); outline-offset: 1px; }
    .op { display: flex; flex-direction: column; gap: 6px; margin-top: 12px; }
    label { font-size: var(--t-xs); font-weight: 600; color: var(--n-700); }
    input, textarea { padding: 8px 10px; border: 1px solid var(--n-300); border-radius: 8px; font: inherit; }
    .hint, .none { margin: 0; color: var(--texto-sec); font-size: var(--t-xs); }
    .btn-primary { align-self: flex-start; min-height: 44px; padding: 0 18px; border: none; border-radius: 8px; background: var(--marca-600); color: #fff; font-weight: 600; cursor: pointer; }
    .btn-primary.danger { background: #b91c1c; }
    .btn-primary:disabled { opacity: 0.6; cursor: not-allowed; }
    .result { margin: 8px 0 0; color: #15803d; font-size: var(--t-sm); }
    .result:empty { display: none; }
    .notice { margin: 8px 0 0; padding: 10px 14px; border-radius: 8px; background: #fdeeee; color: #991b1b; font-size: var(--t-sm); }
    .audit { margin: 12px 0 0; padding-left: 18px; font-size: var(--t-sm); }
    .audit li { padding: 6px 0; border-bottom: 1px solid var(--borde); display: flex; flex-direction: column; gap: 2px; }
    .sub { color: var(--texto-sec); font-size: var(--t-xs); }
    .just { font-style: italic; }
  `],
})
export class PolicyOperationsComponent {
  private readonly policies = inject(PolicyService);

  readonly policy = input.required<PolicyDetail>();
  readonly changed = output<void>();

  readonly panel = signal<Panel>(null);
  readonly beneficiaries = signal<Beneficiary[] | null>(null);
  readonly justification = signal('');
  readonly amount = signal<number | null>(null);
  readonly busy = signal(false);
  readonly message = signal('');
  readonly error = signal<string | null>(null);
  readonly audit = signal<AuditEntry[] | null>(null);

  readonly minJustification = MIN_JUSTIFICATION;
  readonly cop = COP.format;
  readonly closed = computed(() => ['CANCELLED', 'TERMINATED'].includes(this.policy().status));
  readonly renewable = computed(
    () => this.policy().status === 'EXPIRED' || new Date(this.policy().valid_until).getTime() - Date.now() <= RENEWAL_WINDOW_MS,
  );
  readonly justified = computed(() => this.justification().trim().length >= MIN_JUSTIFICATION);
  readonly validAmount = computed(() => {
    const amount = Number(this.amount());
    return amount > 0 && amount < this.policy().insured_amount;
  });

  toggle(panel: Exclude<Panel, null>): void {
    this.panel.set(this.panel() === panel ? null : panel);
    this.justification.set('');
    this.error.set(null);
    this.message.set('');
    if (this.panel() === 'audit') this.loadAudit();
  }

  endorseBeneficiaries(): void {
    const beneficiaries = this.beneficiaries();
    if (!beneficiaries) return;
    this.run(
      this.policies.endorse(this.policy().policy_number, {
        kind: 'BENEFICIARIES', justification: this.justification().trim(), beneficiaries,
      }),
      (result) => `Endoso registrado. La póliza quedó en la versión ${result.version}.`,
    );
  }

  endorseSum(): void {
    this.run(
      this.policies.endorse(this.policy().policy_number, {
        kind: 'SUM_INSURED', justification: this.justification().trim(), insured_amount: Number(this.amount()),
      }),
      (result) => `Endoso registrado. La póliza quedó en la versión ${result.version}.`,
    );
  }

  renew(): void {
    this.run(
      this.policies.renew(this.policy().policy_number),
      (result) => `Póliza renovada por un año. Quedó en la versión ${result.version}.`,
    );
  }

  cancel(): void {
    this.run(
      this.policies.cancel(this.policy().policy_number, this.justification().trim()),
      (result) =>
        result.refund_amount
          ? `Póliza cancelada. Se devolvieron ${this.cop(result.refund_amount)} al titular.`
          : 'Póliza cancelada. No hay prima para devolver.',
    );
  }

  private run<T>(request: Observable<T>, success: (result: T) => string): void {
    this.busy.set(true);
    this.error.set(null);
    request.pipe(finalize(() => this.busy.set(false))).subscribe({
      next: (result) => {
        this.message.set(success(result));
        this.panel.set(null);
        this.justification.set('');
        this.changed.emit();
      },
      error: (error: HttpErrorResponse) => this.error.set(extractErrorMessage(error, 'No se pudo registrar la operación.')),
    });
  }

  private loadAudit(): void {
    this.audit.set(null);
    this.policies.audit(this.policy().policy_number).subscribe({
      next: (audit) => this.audit.set(audit.entries),
      error: (error: HttpErrorResponse) => {
        this.audit.set([]);
        this.error.set(extractErrorMessage(error, 'No se pudo cargar la auditoría.'));
      },
    });
  }
}
