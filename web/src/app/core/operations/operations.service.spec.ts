import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { API_BASE_URL } from '../quotes/quote.service';
import { OperationsService } from './operations.service';

// Tablero operativo (HOTFIX-tablero-operativo, FE-WEB-07).
describe('OperationsService (FE-WEB-07)', () => {
  let http: HttpTestingController;
  let operations: OperationsService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: API_BASE_URL, useValue: '/api/v1' }],
    });
    http = TestBed.inject(HttpTestingController);
    operations = TestBed.inject(OperationsService);
  });

  afterEach(() => http.verify());

  it('pide los indicadores del periodo a experience', () => {
    operations.indicators('7d').subscribe();
    const call = http.expectOne((req) => req.url === '/api/v1/experience/operations/indicators');
    expect(call.request.method).toBe('GET');
    expect(call.request.params.get('period')).toBe('7d');
    call.flush({});
  });
});
