import { Component, InjectionToken, inject, signal } from '@angular/core';
import { DOCUMENT } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { finalize } from 'rxjs';
import { PolicyService } from '../../core/policies/policy.service';

/** Guarda el archivo descargado; se reemplaza en las pruebas. */
export const SAVE_FILE = new InjectionToken<(blob: Blob, filename: string) => void>('SAVE_FILE', {
  providedIn: 'root',
  factory: () => {
    const document = inject(DOCUMENT);
    return (blob: Blob, filename: string) => {
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = filename;
      link.click();
      setTimeout(() => URL.revokeObjectURL(url), 0);
    };
  },
});

function previousMonth(): string {
  const now = new Date();
  const date = new Date(now.getFullYear(), now.getMonth() - 1, 1);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
}

/** Reporte mensual al regulador y al reasegurador en CSV o PDF (HU-WEB-15, RC-22). */
@Component({
  selector: 'app-monthly-report',
  imports: [FormsModule],
  template: `
    <form class="report" (ngSubmit)="download('csv')" aria-labelledby="reportTitle">
      <span id="reportTitle" class="title">Reporte al regulador y al reasegurador</span>
      <label for="reportPeriod" class="sr-only">Mes del reporte</label>
      <input id="reportPeriod" name="period" type="month" [ngModel]="period()" (ngModelChange)="period.set($event)" />
      <button type="submit" class="btn" [disabled]="busy() || !period()">CSV</button>
      <button type="button" class="btn" [disabled]="busy() || !period()" (click)="download('pdf')">PDF</button>
      <span class="status" role="status">{{ status() }}</span>
    </form>
  `,
  styles: [`
    .report { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin: 0 0 20px; font-size: var(--t-sm); }
    .title { font-weight: 600; color: var(--n-700); }
    input { min-height: 40px; padding: 0 10px; border: 1px solid var(--n-300); border-radius: 8px; font: inherit; }
    .btn { min-height: 40px; padding: 0 14px; border: 1px solid var(--marca-600); border-radius: 8px; background: var(--superficie); color: var(--marca-600); font-weight: 600; cursor: pointer; }
    .btn:disabled { opacity: 0.6; cursor: progress; }
    input:focus, .btn:focus-visible { outline: 3px solid rgba(15, 109, 148, 0.35); outline-offset: 1px; }
    .status { color: var(--texto-sec); }
    .sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }
  `],
})
export class MonthlyReportComponent {
  private readonly policies = inject(PolicyService);
  private readonly save = inject(SAVE_FILE);

  readonly period = signal(previousMonth());
  readonly busy = signal(false);
  readonly status = signal('');

  download(format: 'csv' | 'pdf'): void {
    const period = this.period();
    this.busy.set(true);
    this.status.set('Generando el reporte…');
    this.policies
      .monthlyReport(period, format)
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: (blob) => {
          this.save(blob, `reporte-${period}.${format}`);
          this.status.set(`Reporte de ${period} descargado.`);
        },
        error: (error: HttpErrorResponse) =>
          this.status.set(error.status === 403 ? 'Los reportes son solo para operaciones de Solventa.' : 'No se pudo generar el reporte.'),
      });
  }
}
