import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { Observable, firstValueFrom } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { OperatorService } from '../operator/operator.service';
import { API_BASE_URL } from '../quotes/quote.service';
import { solventaGuard } from './solventa.guard';

// Pestaña «Socios», solo del equipo de Solventa (HOTFIX-gestion-de-socios, FE-WEB-05).
describe('solventaGuard y OperatorService (FE-WEB-05)', () => {
  const session = signal<{ role: string; accessToken: string } | null>(null);
  let http: HttpTestingController;

  const run = () =>
    TestBed.runInInjectionContext(() =>
      solventaGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    ) as Observable<boolean | UrlTree>;

  beforeEach(() => {
    session.set({ role: 'analyst', accessToken: 'token-1' });
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api/v1' },
        { provide: AuthService, useValue: { currentSession: () => session() } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('deja pasar al analista de Solventa y no vuelve a consultar en la misma sesión', async () => {
    const result = firstValueFrom(run());
    http.expectOne('/api/v1/experience/operator').flush({ solventa: true });
    expect(await result).toBe(true);
    expect(TestBed.inject(OperatorService).solventa()).toBe(true);

    expect(await firstValueFrom(run())).toBe(true);
    http.expectNone('/api/v1/experience/operator');
  });

  it('devuelve a gestión al analista de un socio', async () => {
    const result = firstValueFrom(run());
    http.expectOne('/api/v1/experience/operator').flush({ solventa: false });
    const router = TestBed.inject(Router);
    expect(router.serializeUrl((await result) as UrlTree)).toBe('/gestion');
  });

  it('un error del servidor cuenta como que no es de Solventa', async () => {
    const result = firstValueFrom(run());
    http.expectOne('/api/v1/experience/operator').flush({ detail: 'x' }, { status: 403, statusText: 'Forbidden' });
    expect(TestBed.inject(Router).serializeUrl((await result) as UrlTree)).toBe('/gestion');
    expect(TestBed.inject(OperatorService).solventa()).toBe(false);
  });

  it('con otra sesión vuelve a consultar y sin rol de analista no consulta', async () => {
    const operator = TestBed.inject(OperatorService);
    const first = firstValueFrom(operator.isSolventa());
    http.expectOne('/api/v1/experience/operator').flush({ solventa: true });
    await first;

    session.set({ role: 'analyst', accessToken: 'token-2' });
    const second = firstValueFrom(operator.isSolventa());
    http.expectOne('/api/v1/experience/operator').flush({ solventa: false });
    expect(await second).toBe(false);

    session.set({ role: 'advisor', accessToken: 'token-3' });
    expect(await firstValueFrom(operator.isSolventa())).toBe(false);
    http.expectNone('/api/v1/experience/operator');
  });
});
