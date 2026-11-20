import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ClaimDecisionRequest, ClaimDetail, ClaimList, ClaimStatus, Expert } from '../models/claim.model';
import { API_BASE_URL } from '../quotes/quote.service';

/** Back office de siniestros (HU-WEB-15); el alcance por socio lo decide el servidor. */
@Injectable({ providedIn: 'root' })
export class ClaimService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  list(status: ClaimStatus | '' = ''): Observable<ClaimList> {
    const params = status ? new HttpParams().set('status', status) : undefined;
    return this.http.get<ClaimList>(`${this.baseUrl}/experience/claims`, { params });
  }

  detail(claimNumber: string): Observable<ClaimDetail> {
    return this.http.get<ClaimDetail>(`${this.baseUrl}/experience/claims/${encodeURIComponent(claimNumber)}`);
  }

  assign(claimNumber: string, expertId: string | null): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/experience/claims/${encodeURIComponent(claimNumber)}/assign`, {
      expert_id: expertId,
    });
  }

  decide(claimNumber: string, request: ClaimDecisionRequest): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/experience/claims/${encodeURIComponent(claimNumber)}/decision`, request);
  }

  retryPayouts(claimNumber: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/experience/claims/${encodeURIComponent(claimNumber)}/payouts/retry`, {});
  }

  experts(city = ''): Observable<Expert[]> {
    const params = city ? new HttpParams().set('city', city) : undefined;
    return this.http.get<Expert[]>(`${this.baseUrl}/experience/experts`, { params });
  }
}
