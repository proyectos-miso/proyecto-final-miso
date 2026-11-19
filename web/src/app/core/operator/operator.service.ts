import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, catchError, finalize, map, of, shareReplay } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { UserSession } from '../models/auth.model';
import { API_BASE_URL } from '../quotes/quote.service';

/** Usuario del token (`sub`), solo para saber si cambió la persona; el servidor es quien valida el token. */
function subjectOf(accessToken: string): string | null {
  try {
    const payload = accessToken.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const sub = JSON.parse(atob(payload)).sub;
    return typeof sub === 'string' && sub ? sub : null;
  } catch {
    return null;
  }
}

/** Misma persona y rol: renovar el token no cambia la sesión. Sin `sub` legible, cada token cuenta como otra sesión. */
function sessionKey(session: UserSession): string {
  return `${session.role}|${subjectOf(session.accessToken) ?? session.accessToken}`;
}

/**
 * Si el analista es del equipo de Solventa y no de un socio (FE-WEB-05, FE-WEB-08). Decide si se ven las
 * pestañas de Solventa («Socios», «Auditoría»); el servidor vuelve a validarlo en cada ruta.
 */
@Injectable({ providedIn: 'root' })
export class OperatorService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);
  private readonly auth = inject(AuthService);

  private readonly _solventa = signal(false);
  readonly solventa = this._solventa.asReadonly();
  private cachedFor: string | null = null;
  private inFlight: Observable<boolean> | null = null;
  private inFlightFor: string | null = null;

  /**
   * Una consulta por sesión; un error cuenta como «no es de Solventa» y se vuelve a intentar después.
   * Renovar el token de la misma sesión conserva la respuesta; otro usuario o rol empieza en `false`.
   * La respuesta de una sesión anterior se descarta, aunque llegue después de la de la sesión actual.
   */
  isSolventa(): Observable<boolean> {
    const session = this.auth.currentSession();
    if (session?.role !== 'analyst') {
      this.reset();
      return of(false);
    }
    const key = sessionKey(session);
    if (this.cachedFor === key) return of(this._solventa());
    if (this.inFlight && this.inFlightFor === key) return this.inFlight;
    this._solventa.set(false);
    this.cachedFor = null;
    const isCurrent = () => {
      const current = this.auth.currentSession();
      return current?.role === 'analyst' && sessionKey(current) === key;
    };
    this.inFlightFor = key;
    this.inFlight = this.http.get<{ solventa: boolean }>(`${this.baseUrl}/experience/operator`).pipe(
      map((profile) => {
        if (!isCurrent()) return false;
        const solventa = profile.solventa === true;
        this._solventa.set(solventa);
        this.cachedFor = key;
        return solventa;
      }),
      catchError(() => {
        if (isCurrent()) this._solventa.set(false);
        return of(false);
      }),
      finalize(() => {
        if (this.inFlightFor === key) {
          this.inFlight = null;
          this.inFlightFor = null;
        }
      }),
      shareReplay(1),
    );
    return this.inFlight;
  }

  reset(): void {
    this._solventa.set(false);
    this.cachedFor = null;
    this.inFlight = null;
    this.inFlightFor = null;
  }
}
