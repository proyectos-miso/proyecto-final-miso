import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ConsentRecord, ConsentRequest } from '../models/consent.model';
import { API_BASE_URL } from '../quotes/quote.service';

@Injectable({
  providedIn: 'root',
})
export class ConsentService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  requestOtp(documentNumber: string, phone?: string, documentType: string = 'CC'): Observable<{ status: string; message: string }> {
    return this.http.post<{ status: string; message: string }>(`${this.baseUrl}/experience/consent/otp-request`, {
      document_number: documentNumber,
      phone,
      document_type: documentType,
    });
  }

  registerConsent(request: ConsentRequest): Observable<ConsentRecord> {
    return this.http.post<ConsentRecord>(`${this.baseUrl}/experience/consent`, request);
  }

  getConsent(documentNumber: string): Observable<ConsentRecord> {
    return this.http.get<ConsentRecord>(`${this.baseUrl}/experience/consent/${documentNumber}`);
  }
}
