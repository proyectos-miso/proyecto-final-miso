import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, of } from 'rxjs';
import {
  PartnerApproval,
  PartnerCredentials,
  PartnerPage,
  PartnerRegistration,
  PartnerSummary,
} from '../models/partner.model';
import { API_BASE_URL } from '../quotes/quote.service';

/** Gestión de socios B2B (FE-WEB-05); solo el equipo de Solventa, y el servidor lo valida en cada llamada. */
@Injectable({ providedIn: 'root' })
export class PartnerService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  list(): Observable<PartnerPage> {
    return this.http.get<PartnerPage>(`${this.baseUrl}/experience/partners`);
  }

  /** Si el contrato OpenAPI está publicado; en entornos sin documentación responde 404 y el enlace no se muestra. */
  openapiAvailable(url: string): Observable<boolean> {
    return this.http.get(url, { observe: 'response', responseType: 'text' }).pipe(
      map(() => true),
      catchError(() => of(false)),
    );
  }

  register(name: string, contactEmail: string): Observable<PartnerRegistration> {
    return this.http.post<PartnerRegistration>(`${this.baseUrl}/quote-edge/partners/register`, {
      name: name.trim(),
      contact_email: contactEmail.trim(),
    });
  }

  approve(partnerId: string): Observable<PartnerApproval> {
    return this.http.post<PartnerApproval>(`${this.partner(partnerId)}/approve`, {});
  }

  rotate(partnerId: string): Observable<PartnerCredentials> {
    return this.http.post<PartnerCredentials>(`${this.partner(partnerId)}/credentials`, {});
  }

  revoke(partnerId: string): Observable<PartnerSummary> {
    return this.http.post<PartnerSummary>(`${this.partner(partnerId)}/revoke`, {});
  }

  private partner(partnerId: string): string {
    return `${this.baseUrl}/experience/partners/${encodeURIComponent(partnerId)}`;
  }
}
