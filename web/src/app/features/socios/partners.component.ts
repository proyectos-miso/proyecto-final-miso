import {
  Component,
  ElementRef,
  InjectionToken,
  Injector,
  OnDestroy,
  OnInit,
  afterNextRender,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { DOCUMENT, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable, finalize } from 'rxjs';
import { PartnerService } from '../../core/partners/partner.service';
import { PartnerCredentials, PartnerSummary } from '../../core/models/partner.model';
import { extractErrorMessage } from '../../core/utils/http-error';

/** Copia al portapapeles; se reemplaza en las pruebas. */
export const COPY_TEXT = new InjectionToken<(text: string) => Promise<void>>('COPY_TEXT', {
  providedIn: 'root',
  factory: () => {
    const navigator = inject(DOCUMENT).defaultView?.navigator;
    return (text: string) =>
      navigator?.clipboard ? navigator.clipboard.writeText(text) : Promise.reject(new Error('Sin portapapeles'));
  },
});

export const OPENAPI_URL = '/openapi.json';
const EMAIL = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;
const TRUNCATED_NOTICE = 'Se muestran solo los socios más recientes: hay más registrados que no aparecen en esta lista.';
const COUNT = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 0 });

type PartnerAction = 'approve' | 'rotate' | 'revoke';

interface PendingAction {
  action: PartnerAction;
  partner: PartnerSummary;
}

const ACTION_LABEL: Record<PartnerAction, string> = {
  approve: 'Aprobar a producción',
  rotate: 'Rotar credenciales',
  revoke: 'Revocar',
};

const ACTION_WARNING: Record<PartnerAction, string> = {
  approve: 'Podrá emitir pólizas reales con una cuota de 50.000 cotizaciones por hora.',
  rotate: 'El secreto anterior ya no sirve para pedir tokens; los tokens ya emitidos siguen vigentes hasta que venzan. Si hubo compromiso, revoque al socio.',
  revoke: 'Sus tokens dejan de servir de inmediato y no podrá volver a autenticarse. No se puede deshacer desde el portal.',
};

/** Alta, aprobación, rotación y revocación de socios B2B; solo para el equipo de Solventa (FE-WEB-05). */
@Component({
  selector: 'app-partners',
  imports: [DatePipe, FormsModule],
  template: `
    <section class="partners">
      <header>
        <h1 #heading tabindex="-1">Socios</h1>
        <p class="subtitle">
          Registre socios en sandbox, apruébelos a producción y gestione sus credenciales.
          @if (openapiAvailable()) {
            <a [href]="openapiUrl" target="_blank" rel="noopener">Contrato OpenAPI del canal de socios<span class="sr-only"> (abre en una pestaña nueva)</span></a>
          }
        </p>
      </header>

      <p class="status" role="status" aria-live="polite">{{ message() }}</p>
      @if (error(); as text) {
        <p class="notice" role="alert">{{ text }}</p>
      }

      @if (credentials(); as secret) {
        <section #secretPanel class="secret" tabindex="-1" aria-labelledby="secretTitle">
          <h2 id="secretTitle">Credenciales de {{ secret.partner_id }}</h2>
          <p class="warn"><strong>No se volverá a mostrar.</strong> Cópielas ahora y entréguelas al socio por un canal seguro.</p>
          <dl>
            <dt>client_id</dt>
            <dd>
              <code data-testid="client-id">{{ secret.client_id }}</code>
              <button type="button" class="btn" (click)="copy(secret.client_id, 'client_id')">Copiar <span class="sr-only">client_id</span></button>
            </dd>
            <dt>client_secret</dt>
            <dd>
              <code data-testid="client-secret">{{ secret.client_secret }}</code>
              <button type="button" class="btn" (click)="copy(secret.client_secret, 'client_secret')">Copiar <span class="sr-only">client_secret</span></button>
            </dd>
          </dl>
          <button type="button" class="btn-primary" (click)="closeCredentials()">Ya las guardé</button>
        </section>
      }

      @if (pending(); as current) {
        <section #confirmPanel class="confirm" tabindex="-1" role="group" aria-labelledby="confirmTitle" aria-describedby="confirmText">
          <h2 id="confirmTitle">¿{{ actionLabel[current.action] }} a {{ current.partner.name }}?</h2>
          <p id="confirmText">{{ actionWarning[current.action] }}</p>
          <div class="row">
            <button type="button" class="btn-primary" [class.danger]="current.action === 'revoke'" [disabled]="busy()" (click)="confirm()">
              Confirmar: {{ actionLabel[current.action] }}
            </button>
            <button type="button" class="btn" [disabled]="busy()" (click)="cancel()">Cancelar</button>
          </div>
        </section>
      }

      <form class="register" (ngSubmit)="register()" aria-labelledby="registerTitle">
        <h2 id="registerTitle">Registrar socio en sandbox</h2>
        <div class="fields">
          <div class="field">
            <label for="partnerName">Nombre del socio</label>
            <input id="partnerName" name="name" required minlength="2" maxlength="120" autocomplete="organization"
              [ngModel]="name()" (ngModelChange)="name.set($event)" />
          </div>
          <div class="field">
            <label for="partnerEmail">Correo de contacto</label>
            <input id="partnerEmail" name="contactEmail" type="email" required maxlength="254" autocomplete="off"
              aria-describedby="partnerEmailHint" [ngModel]="contactEmail()" (ngModelChange)="contactEmail.set($event)" />
            <span id="partnerEmailHint" class="hint">En el listado solo se ve enmascarado.</span>
          </div>
        </div>
        <button #registerButton type="submit" class="btn-primary" [disabled]="busy() || !canRegister()">Registrar en sandbox</button>
      </form>

      @if (truncated()) {
        <p id="partnersTruncated" class="notice truncated" data-testid="partners-truncated">{{ truncatedNotice }}</p>
      }

      @if (items().length > 0) {
        <div class="table-wrap">
          <table [attr.aria-describedby]="truncated() ? 'partnersTruncated' : null">
            <caption>Socios registrados</caption>
            <thead>
              <tr>
                <th scope="col">Socio</th>
                <th scope="col">Entorno</th>
                <th scope="col">Estado</th>
                <th scope="col">Cuota por hora</th>
                <th scope="col">Contacto</th>
                <th scope="col">Alta</th>
                <th scope="col">Acciones</th>
              </tr>
            </thead>
            <tbody>
              @for (partner of items(); track partner.partner_id) {
                <tr>
                  <th scope="row">{{ partner.name }}<span class="sub">{{ partner.partner_id }}</span></th>
                  <td>{{ partner.environment === 'sandbox' ? 'Sandbox' : 'Producción' }}</td>
                  <td><span class="chip" [attr.data-status]="partner.status">{{ partner.status === 'ACTIVE' ? 'Activo' : 'Revocado' }}</span></td>
                  <td>{{ count(partner.quota_used) }} de {{ count(partner.quota_limit) }}</td>
                  <td>{{ partner.contact_email_masked ?? 'Sin correo' }}</td>
                  <td>{{ partner.created_at ? (partner.created_at | date: 'dd/MM/yyyy') : '—' }}</td>
                  <td class="actions">
                    @if (partner.status === 'ACTIVE') {
                      @if (partner.environment === 'sandbox') {
                        <button type="button" class="btn-op" [disabled]="busy()" (click)="ask('approve', partner, $event)">
                          Aprobar a producción<span class="sr-only"> a {{ partner.name }}</span>
                        </button>
                      }
                      <button type="button" class="btn-op" [disabled]="busy()" (click)="ask('rotate', partner, $event)">
                        Rotar credenciales<span class="sr-only"> de {{ partner.name }}</span>
                      </button>
                      <button type="button" class="btn-op danger" [disabled]="busy()" (click)="ask('revoke', partner, $event)">
                        Revocar<span class="sr-only"> a {{ partner.name }}</span>
                      </button>
                    } @else {
                      <span class="sub">Sin acciones</span>
                    }
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      } @else if (!loading() && !error()) {
        <p class="empty" data-testid="no-partners">No hay socios registrados.</p>
      }
    </section>
  `,
  styles: [`
    .partners { max-width: 1100px; margin: 0 auto; padding: 32px 24px; }
    h1 { font-size: var(--t-xl); margin: 0 0 4px; color: var(--texto); }
    h2 { font-size: var(--t-md, 1rem); margin: 0 0 8px; color: var(--texto); }
    .subtitle { color: var(--texto-sec); margin: 0 0 20px; }
    .subtitle a { color: var(--marca-600); margin-left: 4px; }
    .status { color: var(--texto-sec); font-size: var(--t-sm); margin: 0 0 12px; min-height: 1em; }
    .notice { padding: 12px 16px; border-radius: 8px; background: #fdeeee; color: #991b1b; }
    .notice.truncated { background: #fffaf2; color: #92400e; }
    .secret, .confirm, .register { border: 1px solid var(--borde); border-radius: 12px; background: var(--superficie); padding: 16px 20px; margin: 0 0 20px; }
    .secret { border-color: #b45309; background: #fffaf2; }
    .confirm { border-color: var(--marca-600); }
    .warn { color: #92400e; margin: 0 0 12px; }
    dl { display: grid; grid-template-columns: max-content 1fr; gap: 8px 16px; align-items: center; margin: 0 0 12px; }
    dt { font-weight: 600; color: var(--n-700); font-size: var(--t-sm); }
    dd { margin: 0; display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
    code { padding: 4px 8px; border-radius: 6px; background: var(--n-100); word-break: break-all; }
    .row, .fields { display: flex; flex-wrap: wrap; gap: 12px; }
    .field { display: flex; flex-direction: column; gap: 4px; min-width: 240px; flex: 1; }
    label { font-size: var(--t-sm); font-weight: 600; color: var(--n-700); }
    input { min-height: 44px; padding: 0 12px; border: 1px solid var(--n-300); border-radius: 8px; font: inherit; }
    .hint, .sub, .empty { color: var(--texto-sec); font-size: var(--t-xs); }
    .sub { display: block; font-weight: 400; }
    .register .btn-primary { margin-top: 12px; }
    .btn, .btn-op { min-height: 40px; padding: 0 12px; border: 1px solid var(--marca-600); border-radius: 8px; background: var(--superficie); color: var(--marca-600); font-weight: 600; cursor: pointer; }
    .btn-op.danger { border-color: #b91c1c; color: #b91c1c; }
    .btn-primary { min-height: 44px; padding: 0 18px; border: none; border-radius: 8px; background: var(--marca-600); color: #fff; font-weight: 600; cursor: pointer; }
    .btn-primary.danger { background: #b91c1c; }
    button:disabled { opacity: 0.6; cursor: not-allowed; }
    button:focus-visible, input:focus, a:focus-visible, section:focus, h1:focus { outline: 3px solid #0f6d94; outline-offset: 2px; }
    .table-wrap { overflow-x: auto; border: 1px solid var(--borde); border-radius: 12px; background: var(--superficie); }
    table { width: 100%; border-collapse: collapse; font-size: var(--t-sm); }
    caption { text-align: left; padding: 12px 16px; font-weight: 600; color: var(--n-700); }
    th, td { padding: 12px 16px; text-align: left; border-bottom: 1px solid var(--borde); vertical-align: top; }
    thead th { background: var(--n-50); color: var(--n-600); font-weight: 600; }
    tbody th { font-weight: 600; }
    .actions { display: flex; flex-wrap: wrap; gap: 6px; }
    .chip { display: inline-block; padding: 2px 10px; border-radius: 999px; font-weight: 600; font-size: var(--t-xs); background: #e9f7ee; color: #15803d; }
    .chip[data-status='REVOKED'] { background: #fdeeee; color: #b91c1c; }
    .sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }
  `],
})
export class PartnersComponent implements OnInit, OnDestroy {
  private readonly partners = inject(PartnerService);
  private readonly copyText = inject(COPY_TEXT);
  private readonly injector = inject(Injector);
  private readonly secretPanel = viewChild<ElementRef<HTMLElement>>('secretPanel');
  private readonly confirmPanel = viewChild<ElementRef<HTMLElement>>('confirmPanel');
  private readonly heading = viewChild<ElementRef<HTMLElement>>('heading');
  private readonly registerButton = viewChild<ElementRef<HTMLElement>>('registerButton');
  // Botón que abrió la acción en curso; recibe el foco al terminar o, si ya no existe, el encabezado.
  private opener: HTMLElement | null = null;

  readonly openapiUrl = OPENAPI_URL;
  readonly truncatedNotice = TRUNCATED_NOTICE;
  readonly count = (value: number) => COUNT.format(value);
  readonly actionLabel = ACTION_LABEL;
  readonly actionWarning = ACTION_WARNING;

  readonly items = signal<PartnerSummary[]>([]);
  readonly truncated = signal(false);
  readonly openapiAvailable = signal(false);
  readonly loading = signal(false);
  readonly busy = signal(false);
  readonly message = signal('');
  readonly error = signal<string | null>(null);
  readonly name = signal('');
  readonly contactEmail = signal('');
  readonly pending = signal<PendingAction | null>(null);
  // Solo en memoria del componente: se borra al cerrar el panel o al salir de la pestaña.
  readonly credentials = signal<PartnerCredentials | null>(null);

  readonly canRegister = computed(() => this.name().trim().length >= 2 && EMAIL.test(this.contactEmail().trim()));

  ngOnInit(): void {
    this.load();
    this.partners.openapiAvailable(OPENAPI_URL).subscribe((available) => this.openapiAvailable.set(available));
  }

  ngOnDestroy(): void {
    this.credentials.set(null);
  }

  /** `then` marca la recarga tras una acción: si el aviso de truncado aparece entonces, se anuncia en `aria-live`. */
  load(then?: () => void): void {
    this.loading.set(true);
    this.partners
      .list()
      .pipe(
        finalize(() => {
          this.loading.set(false);
          then?.();
        }),
      )
      .subscribe({
        next: (page) => {
          const appeared = page.truncated && !this.truncated();
          this.items.set(page.items);
          this.truncated.set(page.truncated);
          if (appeared && then) this.message.update((text) => `${text} ${TRUNCATED_NOTICE}`.trim());
        },
        error: (error: HttpErrorResponse) => {
          this.items.set([]);
          this.truncated.set(false);
          this.error.set(extractErrorMessage(error, 'No se pudo cargar la lista de socios.'));
        },
      });
  }

  register(): void {
    if (!this.canRegister()) return;
    this.opener = this.registerButton()?.nativeElement ?? null;
    this.run(this.partners.register(this.name(), this.contactEmail()), (created) => {
      this.name.set('');
      this.contactEmail.set('');
      this.showCredentials(created);
      this.message.set(`Socio ${created.name} registrado en sandbox con ${COUNT.format(created.quota_limit)} cotizaciones por hora.`);
    });
  }

  ask(action: PartnerAction, partner: PartnerSummary, event?: Event): void {
    this.opener = (event?.currentTarget as HTMLElement | null) ?? null;
    this.error.set(null);
    this.pending.set({ action, partner });
    this.focus(this.confirmPanel);
  }

  confirm(): void {
    const current = this.pending();
    if (!current) return;
    const { partner_id: id, name } = current.partner;
    if (current.action === 'approve') {
      this.run(this.partners.approve(id), () => this.message.set(`${name} quedó en producción.`));
    } else if (current.action === 'rotate') {
      this.run(this.partners.rotate(id), (rotated) => {
        this.showCredentials(rotated);
        this.message.set(`Credenciales de ${name} rotadas. El secreto anterior ya no sirve para pedir tokens.`);
      });
    } else {
      this.run(this.partners.revoke(id), () => this.message.set(`${name} quedó revocado.`));
    }
  }

  copy(text: string, label: string): void {
    this.copyText(text).then(
      () => this.message.set(`${label} copiado.`),
      () => this.message.set(`No se pudo copiar ${label}; selecciónelo y cópielo a mano.`),
    );
  }

  cancel(): void {
    this.pending.set(null);
    this.restoreFocus();
  }

  closeCredentials(): void {
    this.credentials.set(null);
    this.message.set('Credenciales ocultas.');
    this.restoreFocus();
  }

  private showCredentials(credentials: PartnerCredentials): void {
    this.credentials.set(credentials);
    this.focus(this.secretPanel);
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

  private run<T>(call: Observable<T>, done: (result: T) => void): void {
    this.busy.set(true);
    this.error.set(null);
    call
      .pipe(
        finalize(() => {
          this.busy.set(false);
          this.pending.set(null);
        }),
      )
      .subscribe({
        next: (result) => {
          done(result);
          this.load(() => this.afterAction());
        },
        error: (error: HttpErrorResponse) => {
          this.error.set(extractErrorMessage(error, 'No se pudo completar la acción.'));
          this.load(() => this.afterAction());
        },
      });
  }

  /** Con el panel de credenciales abierto el foco queda en él; «Ya las guardé» lo devuelve después. */
  private afterAction(): void {
    if (!this.credentials()) this.restoreFocus();
  }

  private focus(panel: () => ElementRef<HTMLElement> | undefined): void {
    afterNextRender(() => panel()?.nativeElement.focus(), { injector: this.injector });
  }
}
