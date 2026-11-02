import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { homeFor, roleGuard } from './role.guard';

// Cada pestaña del portal según el rol (HU-WEB-08: TC-06).
describe('roleGuard (HU-WEB-08)', () => {
  const role = signal<string | null>(null);

  const run = (roles: string[]) =>
    TestBed.runInInjectionContext(() =>
      roleGuard(roles)({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    );

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: { currentSession: () => (role() ? { role: role() } : null) } },
      ],
    });
  });

  it('TC-06 deja pasar al rol de la ruta', () => {
    role.set('analyst');
    expect(run(['analyst'])).toBe(true);
  });

  it('TC-06 devuelve al asesor al cotizador y al analista a gestión', () => {
    const router = TestBed.inject(Router);
    role.set('advisor');
    expect(router.serializeUrl(run(['analyst']) as UrlTree)).toBe('/cotizador');
    role.set('analyst');
    expect(router.serializeUrl(run(['advisor']) as UrlTree)).toBe('/gestion');
  });

  it('sin sesión o con un rol desconocido va al ingreso', () => {
    const router = TestBed.inject(Router);
    role.set(null);
    expect(router.serializeUrl(run(['analyst']) as UrlTree)).toBe('/login');
    expect(homeFor('otro')).toBe('/login');
  });
});
