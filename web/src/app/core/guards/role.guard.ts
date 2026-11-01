import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';

/** Pestaña de entrada de cada rol del portal (HU-WEB-08). */
const HOME_BY_ROLE: Record<string, string> = {
  advisor: '/cotizador',
  analyst: '/gestion',
};

export function homeFor(role: string | null | undefined): string {
  return (role && HOME_BY_ROLE[role]) || '/login';
}

/** Cada ruta declara qué roles la usan; los demás vuelven a su pestaña. El servidor también lo valida. */
export const roleGuard =
  (roles: string[]): CanActivateFn =>
  () => {
    const role = inject(AuthService).currentSession()?.role;
    return role && roles.includes(role) ? true : inject(Router).parseUrl(homeFor(role));
  };
