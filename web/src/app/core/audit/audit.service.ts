import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ConsentRevocation, DecisionDetail, ReplayResult } from '../models/audit.model';
import { API_BASE_URL } from '../quotes/quote.service';

/** Identificador de una cotización (`QUO-`) o de un rechazo (`UWR-`). */
export const DECISION_ID = /^(QUO|UWR)-[0-9A-F]{12}$/;

/** Auditoría de suscripción (FE-WEB-08); solo el equipo de Solventa, y el servidor lo valida en cada llamada. */
@Injectable({ providedIn: 'root' })
export class AuditService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  detail(decisionId: string): Observable<DecisionDetail> {
    return this.http.get<DecisionDetail>(this.decision(decisionId));
  }

  replay(decisionId: string): Observable<ReplayResult> {
    return this.http.post<ReplayResult>(`${this.decision(decisionId)}/replay`, {});
  }

  /** El documento va en el cuerpo, no en la URL, para que no quede en los registros de acceso. */
  revokeConsent(documentNumber: string): Observable<ConsentRevocation> {
    return this.http.post<ConsentRevocation>(`${this.baseUrl}/experience/consents/revoke`, {
      document_number: documentNumber.trim(),
    });
  }

  private decision(decisionId: string): string {
    return `${this.baseUrl}/experience/decisions/${encodeURIComponent(decisionId)}`;
  }
}
