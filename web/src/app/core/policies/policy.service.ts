import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
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
}
