import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  CaratulaLink,
  EndorsementRequest,
  PolicyAudit,
  PolicyOperationResult,
  PolicyDetail,
  PolicyIssueRequest,
  PolicyRecord,
  PolicySearchResponse,
  PolicyStatus,
} from '../models/policy.model';
import { API_BASE_URL } from '../quotes/quote.service';

@Injectable({
  providedIn: 'root',
})
export class PolicyService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  issuePolicy(request: PolicyIssueRequest): Observable<PolicyRecord> {
    return this.http.post<PolicyRecord>(`${this.baseUrl}/experience/policies`, request);
  }

  /** Búsqueda de operaciones (HU-WEB-08); el alcance por socio lo decide el servidor. */
  searchPolicies(query: string, status: PolicyStatus | '' = ''): Observable<PolicySearchResponse> {
    let params = new HttpParams().set('q', query.trim());
    if (status) params = params.set('status', status);
    return this.http.get<PolicySearchResponse>(`${this.baseUrl}/experience/policies/search`, { params });
  }

  /** Detalle de operaciones (HU-WEB-09); una póliza fuera del alcance responde 404 como una inexistente. */
  getPolicyDetail(policyNumber: string): Observable<PolicyDetail> {
    return this.http.get<PolicyDetail>(`${this.baseUrl}/experience/policies/${encodeURIComponent(policyNumber)}`);
  }

  /** Enlace de 15 minutos a la carátula (HU-WEB-16); el backend la genera la primera vez. */
  getCaratulaLink(policyNumber: string): Observable<CaratulaLink> {
    return this.http.get<CaratulaLink>(`${this.baseUrl}/experience/policies/${encodeURIComponent(policyNumber)}/caratula`);
  }

  /** Endoso de beneficiarios o de suma asegurada (HU-WEB-15). */
  endorse(policyNumber: string, request: EndorsementRequest): Observable<PolicyOperationResult> {
    return this.http.post<PolicyOperationResult>(
      `${this.baseUrl}/experience/policies/${encodeURIComponent(policyNumber)}/endorsements`,
      request,
    );
  }

  /** Cancelación a pedido del cliente con devolución proporcional (HU-WEB-15). */
  renew(policyNumber: string): Observable<PolicyOperationResult> {
    return this.http.post<PolicyOperationResult>(
      `${this.baseUrl}/experience/policies/${encodeURIComponent(policyNumber)}/renewal`,
      {},
    );
  }

  cancel(policyNumber: string, reason: string): Observable<PolicyOperationResult> {
    return this.http.post<PolicyOperationResult>(
      `${this.baseUrl}/experience/policies/${encodeURIComponent(policyNumber)}/cancellation`,
      { reason },
    );
  }

  /** Decisiones sobre la póliza en orden (HU-WEB-15). */
  audit(policyNumber: string): Observable<PolicyAudit> {
    return this.http.get<PolicyAudit>(`${this.baseUrl}/experience/policies/${encodeURIComponent(policyNumber)}/audit`);
  }

  /** Reporte mensual al regulador y al reasegurador, como archivo (HU-WEB-15). */
  monthlyReport(period: string, format: 'csv' | 'pdf'): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/experience/reports/${period}`, {
      params: new HttpParams().set('format', format),
      responseType: 'blob',
    });
  }
}
