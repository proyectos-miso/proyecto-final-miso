import { routes } from './app.routes';
import { authGuard } from './core/guards/auth.guard';
import { solventaGuard } from './core/guards/solventa.guard';
import { AuditComponent } from './features/auditoria/audit.component';

// Ruta de la auditoría de suscripción (HOTFIX-auditoria-de-suscripcion, FE-WEB-08).
describe('Rutas del portal (FE-WEB-08)', () => {
  it('la ruta /auditoria exige sesión, rol analista y ser de Solventa y carga la pantalla bajo demanda', async () => {
    const audit = routes.find((route) => route.path === 'auditoria');

    expect(audit?.canActivate?.length).toBe(3);
    expect(audit?.canActivate?.[0]).toBe(authGuard);
    expect(audit?.canActivate?.[2]).toBe(solventaGuard);
    expect(await audit?.loadComponent?.()).toBe(AuditComponent);
  });
});
