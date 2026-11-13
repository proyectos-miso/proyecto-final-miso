import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { PaymentReceipt } from '../../../core/models/payment.model';
import { PolicyRecord } from '../../../core/models/policy.model';
import { PaymentStepComponent } from './payment-step.component';
import { COP } from '../../../core/money';

const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
const DECLINED = 'Pago rechazado: fondos insuficientes. Intente con otro medio de pago.';

describe('PaymentStepComponent (HU-WEB-06)', () => {
  const policy: PolicyRecord = {
    policy_number: 'POL-2026-00001',
    quote_id: 'QUO-1',
    insured_document: '1020304050',
    conditions: {
      tier: 'standard',
      monthly_premium: 27_360,
      annual_premium: 328_320,
      coverages: [],
      breakdown: [],
    },
    status: 'ISSUED',
    issued_at: '2026-10-11T12:00:00Z',
    valid_until: '2027-10-11T12:00:00Z',
  };
  const receipt: PaymentReceipt = {
    transaction_id: 'TX-1',
    idempotency_key: 'clave',
    policy_number: 'POL-2026-00001',
    amount: 27_360,
    status: 'APPROVED',
    receipt_number: 'REC-2026-00001',
    created_at: '2026-10-11T12:05:00Z',
  };
  let fixture: ComponentFixture<PaymentStepComponent>;
  let http: HttpTestingController;
  const page = () => fixture.nativeElement as HTMLElement;
  const shownKey = () => page().querySelector('code')?.textContent;
  const payButton = () => page().querySelector<HTMLButtonElement>('.pay-button');
  const alert = () => page().querySelector('[role="alert"]')?.textContent?.trim();
  const choose = (label: string) => {
    [...page().querySelectorAll('label')]
      .find((option) => option.textContent?.includes(label))!
      .querySelector('input')!
      .click();
    fixture.detectChanges();
  };
  const pay = () => {
    payButton()!.click();
    fixture.detectChanges();
    return http.expectOne('/api/v1/experience/payments/charge');
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [PaymentStepComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(PaymentStepComponent);
    fixture.componentRef.setInput('policy', policy);
    fixture.detectChanges();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('cobra la prima una sola vez con la clave que muestra y confirma el recibo', () => {
    const key = shownKey();
    expect(payButton()!.textContent?.trim()).toBe(`Pagar ${COP.format(27_360)}`);

    payButton()!.click();
    fixture.detectChanges();
    const call = pay();

    expect(key).toMatch(UUID_V4);
    expect(call.request.headers.get('Idempotency-Key')).toBe(key);
    expect(call.request.body).toEqual({
      policy_number: 'POL-2026-00001',
      amount: 27_360,
      payment_method: 'PSE',
      token_or_account: 'tok_pse_bancolombia',
    });
    call.flush(receipt);
    fixture.detectChanges();
    expect(page().querySelector('[role="status"]')?.textContent?.replace(/\s+/g, ' ')).toContain(
      'Pago aprobado · recibo REC-2026-00001',
    );
    expect(payButton()).toBeNull();
  });

  it('si el banco rechaza muestra el motivo; otro medio de pago usa otra clave y limpia el aviso', () => {
    choose('cuenta sin fondos');
    const declinedKey = shownKey();

    const call = pay();
    expect(call.request.body.token_or_account).toBe('tok_insufficient_funds');
    call.flush({ detail: DECLINED }, { status: 402, statusText: 'Payment Required' });
    fixture.detectChanges();

    expect(alert()).toBe(DECLINED);
    expect(payButton()!.disabled).toBe(false);
    choose('Tarjeta terminada en 4417');
    expect(alert()).toBeUndefined();
    expect(shownKey()).not.toBe(declinedKey);
  });

  it('sin respuesta pide reintentar y el reintento usa la misma clave', () => {
    const first = pay();
    first.error(new ProgressEvent('error'));
    fixture.detectChanges();

    expect(alert()).toContain('No se pudo confirmar el pago');
    const retry = pay();
    expect(retry.request.headers.get('Idempotency-Key')).toBe(
      first.request.headers.get('Idempotency-Key'),
    );
    retry.flush(receipt);
  });

  describe('mensajes diferenciados y control de reintento por código HTTP (TC-10, RC-03)', () => {
    it('en error 404 muestra mensaje específico y oculta el botón de reintento', () => {
      const call = pay();
      call.flush(
        { detail: 'La póliza no existe o no se encuentra disponible para cobro.' },
        { status: 404, statusText: 'Not Found' },
      );
      fixture.detectChanges();

      expect(alert()).toContain('La póliza no existe');
      expect(payButton()).toBeNull();
    });

    it('en error 409 muestra aviso de póliza pagada y oculta el botón de reintento', () => {
      const call = pay();
      call.flush(
        { detail: 'La póliza ya está pagada.' },
        { status: 409, statusText: 'Conflict' },
      );
      fixture.detectChanges();

      expect(alert()).toContain('La póliza ya está pagada');
      expect(payButton()).toBeNull();
    });

    it('en error 422 muestra error de datos no procesables y oculta el botón de reintento', () => {
      const call = pay();
      call.flush(
        { detail: 'El monto no coincide con la prima de la póliza.' },
        { status: 422, statusText: 'Unprocessable Content' },
      );
      fixture.detectChanges();

      expect(alert()).toContain('El monto no coincide');
      expect(payButton()).toBeNull();
    });

    it('en error 502 muestra error de pasarela no disponible y permite reintentar', () => {
      const call = pay();
      call.flush(
        { detail: 'La pasarela no respondió. Reintente con la misma clave: no se cobrará dos veces.' },
        { status: 502, statusText: 'Bad Gateway' },
      );
      fixture.detectChanges();

      expect(alert()).toContain('La pasarela no respondió');
      expect(payButton()).not.toBeNull();
      expect(payButton()!.disabled).toBe(false);
    });
  });
});
