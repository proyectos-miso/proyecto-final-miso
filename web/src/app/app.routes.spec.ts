import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, CanActivateFn, Route, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { Observable, firstValueFrom, isObservable, of } from 'rxjs';
import { routes } from './app.routes';
import { AuthService } from './core/auth/auth.service';
import { solventaGuard } from './core/guards/solventa.guard';
import { OperatorService } from './core/operator/operator.service';
import { OperationsComponent } from './features/operacion/operations.component';

// Ruta del tablero operativo (HOTFIX-tablero-operativo, FE-WEB-07).
describe('Ruta /operacion (FE-WEB-07)', () => {
  const route = routes.find((item) => item.path === 'operacion') as Route;
  let solventa = false;

  const guards = async (): Promise<(boolean | UrlTree)[]> => {
    const results: (boolean | UrlTree)[] = [];
    for (const guard of (route.canActivate ?? []) as CanActivateFn[]) {
      const result = TestBed.runInInjectionContext(() =>
        guard({} as ActivatedRouteSnapshot, { url: '/operacion' } as RouterStateSnapshot),
      );
      results.push(isObservable(result) ? await firstValueFrom(result as Observable<boolean | UrlTree>) : (result as boolean | UrlTree));
    }
    return results;
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        {
          provide: AuthService,
          useValue: { isAuthenticated: () => true, currentSession: () => ({ role: 'analyst', accessToken: 'token' }) },
        },
        { provide: OperatorService, useValue: { isSolventa: () => of(solventa) } },
      ],
    });
  });

  it('usa solventaGuard y carga la pantalla de forma diferida', async () => {
    expect(route.canActivate).toContain(solventaGuard);
    expect(route.component).toBeUndefined();
    expect(await route.loadComponent?.()).toBe(OperationsComponent);
  });

  it('devuelve a /gestion al analista que no es de Solventa', async () => {
    solventa = false;
    const results = await guards();
    const router = TestBed.inject(Router);
    expect(router.serializeUrl(results[results.length - 1] as UrlTree)).toBe('/gestion');
  });

  it('deja pasar al analista de Solventa', async () => {
    solventa = true;
    expect(await guards()).toEqual([true, true, true]);
  });
});
