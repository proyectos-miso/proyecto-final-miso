import { Component, ElementRef, Injector, afterNextRender, computed, inject, signal, viewChild } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { finalize } from 'rxjs';
import { AuditService, DECISION_ID } from '../../core/audit/audit.service';
import { DecisionDetail, ReplayResult } from '../../core/models/audit.model';
import { COP } from '../../core/money';
import { extractErrorMessage } from '../../core/utils/http-error';

const DOCUMENT = /^[A-Za-z0-9]{4,20}$/;
const MULTIPLIER = new Intl.NumberFormat('es-CO', { minimumFractionDigits: 2, maximumFractionDigits: 4 });
const COUNT = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 0 });
const REVOKED_INPUT = 'Omitido: consentimiento revocado';
export const REJECTION_NOT_REPLAYABLE =
  'Los rechazos no guardan el puntaje del buró (HU-WEB-07), así que no se pueden recalcular.';
export const CONSENT_REVOKED_NOT_REPLAYABLE =
  'El consentimiento del titular fue revocado: no se recalcula con sus datos personales.';
export const RELOAD_FAILED = 'La revocación quedó registrada. Busque la decisión de nuevo.';
export const REVOKED_REJECTION = 'Se omiten los datos personales y los montos derivados de ellos.';

const STATUS_LABEL: Record<string, string> = {
  active: 'Cotización activa',
  degraded: 'Cotización degradada (perfil parcial)',
  review_required: 'Requiere revisión de suscripción',
};

const TIER_LABEL: Record<string, string> = { basic: 'Básico', standard: 'Estándar', premium: 'Premium' };

/** Detalle y recálculo de las decisiones de suscripción; solo para el equipo de Solventa (FE-WEB-08). */
@Component({
  selector: 'app-audit',
  imports: [DatePipe, FormsModule],
  template: `
    <section class="audit">
      <header>
        <h1 #heading tabindex="-1">Auditoría</h1>
        <p class="subtitle">Consulte las entradas y la salida de una cotización o un rechazo y recalcule las cotizaciones con las reglas vigentes.</p>
      </header>

      <p class="status" role="status" aria-live="polite" data-testid="audit-status">{{ message() }}</p>
      @if (error(); as text) {
        <p class="notice" role="alert">{{ text }}</p>
      }

      <form class="search" (ngSubmit)="search()" aria-labelledby="searchTitle" novalidate>
        <h2 id="searchTitle">Buscar una decisión</h2>
        <div class="field">
          <label for="decisionId">Identificador de la cotización o del rechazo</label>
          <input id="decisionId" name="decisionId" required maxlength="32" autocomplete="off" spellcheck="false"
            [attr.aria-describedby]="idError() ? 'decisionIdHint decisionIdError' : 'decisionIdHint'"
            [attr.aria-invalid]="idError() ? 'true' : null"
            [attr.aria-errormessage]="idError() ? 'decisionIdError' : null"
            [ngModel]="decisionId()" (ngModelChange)="decisionId.set($event)" />
          <span id="decisionIdHint" class="hint">Empieza con QUO- (cotización) o UWR- (rechazo) y sigue con 12 caracteres, por ejemplo QUO-1A2B3C4D5E6F.</span>
          @if (idError(); as text) {
            <span id="decisionIdError" class="field-error">{{ text }}</span>
          }
        </div>
        <button type="submit" class="btn-primary" [disabled]="busy()">Buscar</button>
      </form>

      @if (detail(); as decision) {
        <section class="detail" aria-labelledby="detailTitle">
          <h2 id="detailTitle">{{ decision.kind === 'quote' ? 'Cotización' : 'Rechazo de suscripción' }} {{ decision.decision_id }}</h2>
          <dl>
            <dt>Fecha</dt>
            <dd>{{ decision.created_at | date: 'dd/MM/yyyy HH:mm' }}</dd>
            <dt>Versión de reglas</dt>
            <dd data-testid="rules-version">{{ decision.rules_version ?? 'Sin versión: anterior a la auditoría' }}</dd>
            <dt>Decisión</dt>
            <dd data-testid="decision-status">{{ statusLabel(decision.outcome.status) }}</dd>
          </dl>
          @if (decision.consent_revoked) {
            <p class="notice info" data-testid="consent-revoked">
              @if (decision.kind === 'quote') {
                El titular revocó su consentimiento: se omiten el documento, la fecha de nacimiento, el puntaje y el desglose de la prima.
              } @else {
                {{ revokedRejection }}
              }
            </p>
          }

          @if (decision.kind === 'quote' || !decision.consent_revoked) {
            <div class="table-wrap">
              <table>
                <caption>Entradas de la decisión</caption>
                <thead>
                  <tr><th scope="col">Entrada</th><th scope="col">Valor</th></tr>
                </thead>
                <tbody>
                  <tr><th scope="row">Documento</th><td data-testid="input-document">{{ personal(decision, decision.inputs.document_masked) }}</td></tr>
                  @if (decision.kind === 'quote') {
                    <tr><th scope="row">Fecha de nacimiento</th><td data-testid="input-birth-date">{{ personal(decision, decision.inputs.birth_date) }}</td></tr>
                    <tr><th scope="row">Suma asegurada</th><td>{{ money(decision.inputs.insured_amount) }}</td></tr>
                    <tr><th scope="row">Riesgo de ocupación</th><td>{{ decision.inputs.occupation_risk ?? 'No registrado' }}</td></tr>
                    <tr><th scope="row">Puntaje del buró</th><td data-testid="input-score">{{ personal(decision, decision.inputs.score) }}</td></tr>
                  } @else {
                    <tr><th scope="row">Monto solicitado</th><td data-testid="input-requested">{{ money(decision.inputs.insured_amount) }}</td></tr>
                  }
                </tbody>
              </table>
            </div>
          }

          @if (decision.kind === 'rejection' && !decision.consent_revoked) {
            <dl class="rejection">
              <dt>Regla</dt>
              <dd data-testid="rejection-rule">{{ decision.outcome.rule_id }}</dd>
              <dt>Motivo</dt>
              <dd data-testid="rejection-reason">{{ decision.outcome.reason }}</dd>
              <dt>Monto máximo aprobable</dt>
              <dd data-testid="rejection-max">{{ money(decision.outcome.max_approvable_amount) }}</dd>
            </dl>
          }

          @if (decision.consent_revoked && decision.outcome.tiers.length) {
            <div class="table-wrap">
              <table data-testid="tier-summary">
                <caption>Primas por plan</caption>
                <thead>
                  <tr><th scope="col">Plan</th><th scope="col">Prima mensual</th><th scope="col">Prima anual</th></tr>
                </thead>
                <tbody>
                  @for (tier of decision.outcome.tiers; track tier.tier) {
                    <tr>
                      <th scope="row">{{ tierLabel(tier.tier) }}</th>
                      <td>{{ money(tier.monthly_premium) }}</td>
                      <td>{{ money(tier.annual_premium ?? null) }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }

          @for (tier of decision.consent_revoked ? [] : decision.outcome.tiers; track tier.tier) {
            <div class="table-wrap">
              <table data-testid="tier-table">
                <caption>Desglose del plan {{ tierLabel(tier.tier) }}</caption>
                <thead>
                  <tr><th scope="col">Factor</th><th scope="col">Multiplicador</th><th scope="col">Aporte mensual</th></tr>
                </thead>
                <tbody>
                  @for (factor of tier.breakdown ?? []; track factor.code) {
                    <tr>
                      <th scope="row">{{ factor.label }}</th>
                      <td>{{ multiplier(factor.multiplier) }}</td>
                      <td>{{ money(factor.amount) }}</td>
                    </tr>
                  }
                </tbody>
                <tfoot>
                  <tr><th scope="row" colspan="2">Prima mensual</th><td>{{ money(tier.monthly_premium) }}</td></tr>
                </tfoot>
              </table>
            </div>
          }

          <div class="replay">
            @if (decision.kind === 'rejection') {
              <p class="hint" data-testid="rejection-not-replayable">{{ rejectionNotReplayable }}</p>
            } @else if (decision.consent_revoked) {
              <p class="hint" data-testid="consent-not-replayable">{{ consentRevokedNotReplayable }}</p>
            } @else if (decision.replayable) {
              <button type="button" class="btn-primary" [disabled]="busy()" (click)="replay()">Recalcular</button>
            } @else {
              <p class="hint" data-testid="not-replayable">Decisión anterior a la auditoría: no se puede recalcular.</p>
            }
          </div>

          @if (result(); as replayed) {
            <section class="result" [class.differs]="!replayed.matches" aria-labelledby="resultTitle" data-testid="replay-result">
              <h3 id="resultTitle">{{ replayed.matches ? 'Coincide' : 'No coincide' }}</h3>
              <p>Reglas de la decisión: {{ replayed.rules_version_then }}. Reglas vigentes: {{ replayed.rules_version_now }}.</p>
              @if (!replayed.matches) {
                <div class="table-wrap">
                  <table>
                    <caption>Diferencias del recálculo</caption>
                    <thead>
                      <tr><th scope="col">Campo</th><th scope="col">En la decisión</th><th scope="col">Con las reglas vigentes</th></tr>
                    </thead>
                    <tbody>
                      @for (difference of replayed.differences; track difference.field) {
                        <tr>
                          <th scope="row">{{ difference.field }}</th>
                          <td>{{ shown(difference.then) }}</td>
                          <td>{{ shown(difference.now) }}</td>
                        </tr>
                      }
                    </tbody>
                  </table>
                </div>
              }
            </section>
          }
        </section>
      }

      <section class="consent" aria-labelledby="consentTitle">
        <h2 id="consentTitle">Revocación del consentimiento</h2>
        <p class="hint">Registre la revocación que pidió el titular: su consentimiento deja de servir para cotizar y sus datos se omiten en la auditoría.</p>
        <div class="field">
          <label for="consentDocument">Documento del titular</label>
          <input id="consentDocument" name="consentDocument" maxlength="32" autocomplete="off" inputmode="text"
            [attr.aria-describedby]="documentError() ? 'consentDocumentHint consentDocumentError' : 'consentDocumentHint'"
            [attr.aria-invalid]="documentError() ? 'true' : null"
            [attr.aria-errormessage]="documentError() ? 'consentDocumentError' : null"
            [ngModel]="documentNumber()" (ngModelChange)="documentNumber.set($event)" />
          <span id="consentDocumentHint" class="hint">De 4 a 20 letras o números, sin espacios ni puntos.</span>
          @if (documentError(); as text) {
            <span id="consentDocumentError" class="field-error">{{ text }}</span>
          }
        </div>
        <button #revokeButton type="button" class="btn-op danger" [disabled]="busy()" (click)="askRevocation($event)">
          Registrar revocación del consentimiento
        </button>
      </section>

      @if (confirming()) {
        <section #confirmPanel class="confirm" tabindex="-1" role="group" aria-labelledby="confirmTitle" aria-describedby="confirmText">
          <h2 id="confirmTitle">¿Registrar la revocación del consentimiento del documento {{ maskedDocument() }}?</h2>
          <p id="confirmText">Todos sus consentimientos vigentes quedan revocados y no sirven para cotizar. No se puede deshacer desde el portal.</p>
          <div class="row">
            <button type="button" class="btn-primary danger" [disabled]="busy()" (click)="confirmRevocation()">Confirmar: registrar revocación</button>
            <button type="button" class="btn" [disabled]="busy()" (click)="cancelRevocation()">Cancelar</button>
          </div>
        </section>
      }
    </section>
  `,
  styles: [`
    .audit { max-width: 1100px; margin: 0 auto; padding: 32px 24px; }
    h1 { font-size: var(--t-xl); margin: 0 0 4px; color: var(--texto); }
    h2 { font-size: var(--t-md, 1rem); margin: 0 0 8px; color: var(--texto); }
    h3 { font-size: var(--t-md, 1rem); margin: 0 0 6px; }
    .subtitle { color: var(--texto-sec); margin: 0 0 20px; }
    .status { color: var(--texto-sec); font-size: var(--t-sm); margin: 0 0 12px; min-height: 1em; }
    .notice { padding: 12px 16px; border-radius: 8px; background: #fdeeee; color: #991b1b; }
    .notice.info { background: #fffaf2; color: #92400e; }
    .search, .detail, .consent, .confirm { border: 1px solid var(--borde); border-radius: 12px; background: var(--superficie); padding: 16px 20px; margin: 0 0 20px; }
    .confirm { border-color: var(--marca-600); }
    .field { display: flex; flex-direction: column; gap: 4px; max-width: 420px; margin: 0 0 12px; }
    label { font-size: var(--t-sm); font-weight: 600; color: var(--n-700); }
    input { min-height: 44px; padding: 0 12px; border: 1px solid var(--n-300); border-radius: 8px; font: inherit; }
    input[aria-invalid='true'] { border-color: #b91c1c; }
    .hint { color: var(--texto-sec); font-size: var(--t-xs); }
    .field-error { color: #b91c1c; font-size: var(--t-xs); font-weight: 600; }
    dl { display: grid; grid-template-columns: max-content 1fr; gap: 6px 16px; margin: 0 0 16px; }
    dt { font-weight: 600; color: var(--n-700); font-size: var(--t-sm); }
    dd { margin: 0; }
    .table-wrap { overflow-x: auto; border: 1px solid var(--borde); border-radius: 12px; margin: 0 0 16px; }
    table { width: 100%; border-collapse: collapse; font-size: var(--t-sm); }
    caption { text-align: left; padding: 12px 16px; font-weight: 600; color: var(--n-700); }
    th, td { padding: 10px 16px; text-align: left; border-bottom: 1px solid var(--borde); }
    thead th { background: var(--n-50); color: var(--n-600); font-weight: 600; }
    tbody th, tfoot th { font-weight: 600; }
    .result { border-left: 4px solid #15803d; padding: 8px 16px; margin: 16px 0 0; }
    .result.differs { border-left-color: #b91c1c; }
    .row { display: flex; flex-wrap: wrap; gap: 12px; }
    .btn, .btn-op { min-height: 44px; padding: 0 14px; border: 1px solid var(--marca-600); border-radius: 8px; background: var(--superficie); color: var(--marca-600); font-weight: 600; cursor: pointer; }
    .btn-op.danger { border-color: #b91c1c; color: #b91c1c; }
    .btn-primary { min-height: 44px; padding: 0 18px; border: none; border-radius: 8px; background: var(--marca-600); color: #fff; font-weight: 600; cursor: pointer; }
    .btn-primary.danger { background: #b91c1c; }
    button:disabled { opacity: 0.6; cursor: not-allowed; }
    button:focus-visible, input:focus, section:focus, h1:focus { outline: 3px solid #0f6d94; outline-offset: 2px; }
  `],
})
export class AuditComponent {
  private readonly audit = inject(AuditService);
  private readonly injector = inject(Injector);
  private readonly heading = viewChild<ElementRef<HTMLElement>>('heading');
  private readonly confirmPanel = viewChild<ElementRef<HTMLElement>>('confirmPanel');
  // Botón que abrió la confirmación; recibe el foco al terminar o, si ya no existe, el encabezado.
  private opener: HTMLElement | null = null;

  readonly decisionId = signal('');
  readonly idError = signal<string | null>(null);
  readonly detail = signal<DecisionDetail | null>(null);
  readonly result = signal<ReplayResult | null>(null);
  readonly documentNumber = signal('');
  readonly documentError = signal<string | null>(null);
  readonly confirming = signal(false);
  readonly busy = signal(false);
  readonly message = signal('');
  readonly error = signal<string | null>(null);

  readonly maskedDocument = computed(() => mask(this.documentNumber().trim()));
  readonly rejectionNotReplayable = REJECTION_NOT_REPLAYABLE;
  readonly consentRevokedNotReplayable = CONSENT_REVOKED_NOT_REPLAYABLE;
  readonly revokedRejection = REVOKED_REJECTION;

  search(): void {
    const id = this.decisionId().trim().toUpperCase();
    this.result.set(null);
    if (!DECISION_ID.test(id)) {
      this.idError.set('Escriba un identificador válido: QUO- o UWR- seguido de 12 caracteres (0-9, A-F).');
      return;
    }
    this.idError.set(null);
    this.error.set(null);
    this.busy.set(true);
    this.audit
      .detail(id)
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: (decision) => {
          this.detail.set(decision);
          this.message.set(`Decisión ${decision.decision_id} cargada.`);
        },
        error: (error: HttpErrorResponse) => {
          this.detail.set(null);
          this.message.set('');
          this.error.set(extractErrorMessage(error, 'No se pudo consultar la decisión.'));
        },
      });
  }

  replay(): void {
    const decision = this.detail();
    if (!decision) return;
    this.error.set(null);
    this.busy.set(true);
    this.audit
      .replay(decision.decision_id)
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: (replayed) => {
          this.result.set(replayed);
          this.message.set(
            replayed.matches
              ? `Recálculo: coincide con la decisión (reglas ${replayed.rules_version_now}).`
              : `Recálculo: no coincide; ${COUNT.format(replayed.differences.length)} diferencias con las reglas ${replayed.rules_version_now}.`,
          );
        },
        error: (error: HttpErrorResponse) => {
          this.result.set(null);
          this.error.set(extractErrorMessage(error, 'No se pudo recalcular la decisión.'));
        },
      });
  }

  askRevocation(event?: Event): void {
    if (!DOCUMENT.test(this.documentNumber().trim())) {
      this.documentError.set('Escriba el documento del titular: de 4 a 20 letras o números, sin espacios ni puntos.');
      return;
    }
    this.documentError.set(null);
    this.error.set(null);
    this.opener = (event?.currentTarget as HTMLElement | null) ?? null;
    this.confirming.set(true);
    afterNextRender(() => this.confirmPanel()?.nativeElement.focus(), { injector: this.injector });
  }

  confirmRevocation(): void {
    this.busy.set(true);
    this.audit
      .revokeConsent(this.documentNumber().trim())
      .pipe(
        finalize(() => {
          this.busy.set(false);
          this.confirming.set(false);
          this.restoreFocus();
        }),
      )
      .subscribe({
        next: (revocation) => {
          this.documentNumber.set('');
          this.message.set(
            revocation.revoked > 0
              ? `Revocación registrada: ${COUNT.format(revocation.revoked)} consentimientos del documento ${revocation.document_masked} quedaron revocados.`
              : `El documento ${revocation.document_masked} no tenía consentimientos vigentes.`,
          );
          const current = this.detail();
          if (current) this.reload(current.decision_id);
        },
        error: (error: HttpErrorResponse) => {
          this.error.set(extractErrorMessage(error, 'No se pudo registrar la revocación.'));
        },
      });
  }

  cancelRevocation(): void {
    this.confirming.set(false);
    this.restoreFocus();
  }

  statusLabel(status: string): string {
    return STATUS_LABEL[status] ?? status;
  }

  tierLabel(tier: string): string {
    return TIER_LABEL[tier] ?? tier;
  }

  money(amount: number | null | undefined): string {
    return amount === null || amount === undefined ? 'No registrado' : COP.format(amount);
  }

  multiplier(value: number): string {
    return MULTIPLIER.format(value);
  }

  personal(decision: DecisionDetail, value: string | number | null | undefined): string {
    if (value !== null && value !== undefined) return String(value);
    return decision.consent_revoked ? REVOKED_INPUT : 'No registrado';
  }

  shown(value: unknown): string {
    if (value === null || value === undefined) return '—';
    if (typeof value !== 'number') return String(value);
    return Number.isInteger(value) ? COUNT.format(value) : MULTIPLIER.format(value);
  }

  /** Tras revocar, la decisión abierta se vuelve a pedir para que omita las entradas personales. */
  private reload(decisionId: string): void {
    this.result.set(null);
    this.audit.detail(decisionId).subscribe({
      next: (decision) => this.detail.set(decision),
      error: () => {
        this.detail.set(null);
        this.error.set(RELOAD_FAILED);
      },
    });
  }

  private restoreFocus(): void {
    const opener = this.opener;
    this.opener = null;
    afterNextRender(
      () => {
        const usable = opener?.isConnected && !opener.hasAttribute('disabled');
        const target = usable ? opener : this.heading()?.nativeElement;
        target?.focus();
      },
      { injector: this.injector },
    );
  }
}

function mask(document: string): string {
  return document.length > 4 ? `${'*'.repeat(document.length - 4)}${document.slice(-4)}` : '****';
}
