import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { OperationsIndicators, OperationsPeriod } from '../models/operations.model';
import { API_BASE_URL } from '../quotes/quote.service';

/** Tablero operativo (FE-WEB-07); solo el equipo de Solventa, y el servidor lo valida en cada llamada. */
@Injectable({ providedIn: 'root' })
export class OperationsService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  indicators(period: OperationsPeriod): Observable<OperationsIndicators> {
    return this.http.get<OperationsIndicators>(`${this.baseUrl}/experience/operations/indicators`, { params: { period } });
  }
}
