import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthService } from '../auth/auth.service';
import { UserSession } from '../models/auth.model';
import { API_BASE_URL } from '../quotes/quote.service';
import { OperatorService } from './operator.service';

// Visibilidad de la pestaña «Socios» (HOTFIX-gestion-de-socios, FE-WEB-05).
describe('OperatorService (FE-WEB-05)', () => {
  let http: HttpTestingController;
  let operator: OperatorService;
  const session = signal<UserSession | null>(null);
  const analyst = (accessToken: string): UserSession => ({
    accessToken,
    refreshToken: 'refresh',
    tokenType: 'bearer',
    role: 'analyst',
    advisorName: 'Analista de Prueba',
  });

  const jwt = (sub: string, jti: string): string => {
    const payload = btoa(JSON.stringify({ sub, jti })).replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_');
    return `cabecera.${payload}.firma`;
  };

  beforeEach(() => {
    session.set(null);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api/v1' },
        { provide: AuthService, useValue: { currentSession: session } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    operator = TestBed.inject(OperatorService);
  });

  afterEach(() => http.verify());

  it('consulta una vez por sesión y recuerda la respuesta', () => {
    session.set(analyst('sesion-a'));
    const results: boolean[] = [];
    operator.isSolventa().subscribe((value) => results.push(value));
    http.expectOne('/api/v1/experience/operator').flush({ solventa: true });
    operator.isSolventa().subscribe((value) => results.push(value));

    http.expectNone('/api/v1/experience/operator');
    expect(results).toEqual([true, true]);
    expect(operator.solventa()).toBe(true);
  });

  it('con respuestas en orden invertido se queda con la de la sesión actual', () => {
    session.set(analyst('sesion-solventa'));
    const first: boolean[] = [];
    operator.isSolventa().subscribe((value) => first.push(value));
    const oldCall = http.expectOne('/api/v1/experience/operator');

    session.set(analyst('sesion-socio'));
    const second: boolean[] = [];
    operator.isSolventa().subscribe((value) => second.push(value));
    const newCall = http.expectOne('/api/v1/experience/operator');
    expect(operator.solventa()).toBe(false);

    newCall.flush({ solventa: false });
    oldCall.flush({ solventa: true });

    expect(second).toEqual([false]);
    expect(first).toEqual([false]);
    expect(operator.solventa()).toBe(false);
  });

  it('renovar el token de la misma persona no oculta la pestaña ni vuelve a consultar', () => {
    session.set(analyst(jwt('operaciones@solventa.example', 'uno')));
    operator.isSolventa().subscribe();
    http.expectOne('/api/v1/experience/operator').flush({ solventa: true });

    session.set(analyst(jwt('operaciones@solventa.example', 'dos')));
    const results: boolean[] = [];
    operator.isSolventa().subscribe((value) => results.push(value));

    http.expectNone('/api/v1/experience/operator');
    expect(results).toEqual([true]);
    expect(operator.solventa()).toBe(true);
  });

  it('acepta la respuesta pedida antes de renovar el token de la misma persona', () => {
    session.set(analyst(jwt('operaciones@solventa.example', 'uno')));
    operator.isSolventa().subscribe();
    const call = http.expectOne('/api/v1/experience/operator');

    session.set(analyst(jwt('operaciones@solventa.example', 'dos')));
    call.flush({ solventa: true });

    expect(operator.solventa()).toBe(true);
  });

  it('otro usuario empieza sin la pestaña y descarta la respuesta del anterior', () => {
    session.set(analyst(jwt('operaciones@solventa.example', 'uno')));
    operator.isSolventa().subscribe();
    const oldCall = http.expectOne('/api/v1/experience/operator');
    oldCall.flush({ solventa: true });

    session.set(analyst(jwt('analista@bancoaliado.example', 'dos')));
    operator.isSolventa().subscribe();
    expect(operator.solventa()).toBe(false);
    http.expectOne('/api/v1/experience/operator').flush({ solventa: false });
    expect(operator.solventa()).toBe(false);
  });

  it('una sesión nueva empieza sin la pestaña «Socios» hasta que el servidor responde', () => {
    session.set(analyst('sesion-a'));
    operator.isSolventa().subscribe();
    http.expectOne('/api/v1/experience/operator').flush({ solventa: true });
    expect(operator.solventa()).toBe(true);

    session.set(analyst('sesion-b'));
    operator.isSolventa().subscribe();
    expect(operator.solventa()).toBe(false);
    http.expectOne('/api/v1/experience/operator').flush({ solventa: true });
    expect(operator.solventa()).toBe(true);
  });
});
