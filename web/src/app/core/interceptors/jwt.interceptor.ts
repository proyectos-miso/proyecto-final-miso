import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from '../auth/auth.service';

export const jwtInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const session = authService.currentSession();

  const isAuthEndpoint =
    req.url.includes('/auth/login') || req.url.includes('/auth/refresh') || req.url.includes('/auth/logout');
  // El contrato OpenAPI es público: no hace falta el token del analista (FE-WEB-05).
  const isPublicContract = req.url === '/openapi.json';

  let modifiedReq = req;
  if (session?.accessToken && !isAuthEndpoint && !isPublicContract) {
    modifiedReq = req.clone({
      setHeaders: {
        Authorization: `Bearer ${session.accessToken}`,
      },
    });
  }

  return next(modifiedReq).pipe(
    catchError((error: unknown) => {
      if (
        error instanceof HttpErrorResponse &&
        error.status === 401 &&
        !isAuthEndpoint &&
        !isPublicContract &&
        session?.refreshToken
      ) {
        // Intento de renovación silenciosa de sesión ante token expirado (Criterio 3)
        return authService.refreshToken().pipe(
          switchMap((refreshedSession) => {
            const retriedReq = req.clone({
              setHeaders: {
                Authorization: `Bearer ${refreshedSession.accessToken}`,
              },
            });
            return next(retriedReq);
          }),
          catchError((refreshErr) => {
            authService.logout();
            return throwError(() => refreshErr);
          }),
        );
      }
      return throwError(() => error);
    }),
  );
};
