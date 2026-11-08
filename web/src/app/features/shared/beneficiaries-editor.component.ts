import { Component, OnInit, computed, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Beneficiary, Relationship } from '../../core/models/policy.model';

interface Row {
  name: string;
  document_type: string;
  document_number: string;
  relationship: Relationship;
  percentage: number | null;
}

export const RELATIONSHIPS: { value: Relationship; label: string }[] = [
  { value: 'CONYUGE', label: 'Cónyuge o compañero' },
  { value: 'HIJO', label: 'Hijo o hija' },
  { value: 'PADRE', label: 'Padre' },
  { value: 'MADRE', label: 'Madre' },
  { value: 'HERMANO', label: 'Hermano o hermana' },
  { value: 'OTRO', label: 'Otro' },
];

const empty = (): Row => ({ name: '', document_type: 'CC', document_number: '', relationship: 'CONYUGE', percentage: null });

/** Beneficiarios de la póliza de vida: nombre, documento, parentesco y porcentajes que suman 100 (HU-WEB-15). */
@Component({
  selector: 'app-beneficiaries-editor',
  imports: [FormsModule],
  template: `
    <fieldset class="beneficiaries">
      <legend>{{ legend() }}</legend>
      @for (row of rows(); track $index; let i = $index) {
        <div class="row" role="group" [attr.aria-label]="'Beneficiario ' + (i + 1)">
          <div class="field grow">
            <label [for]="id('name', i)">Nombre completo</label>
            <input [id]="id('name', i)" [name]="id('name', i)" maxlength="150" autocomplete="off"
              [ngModel]="row.name" (ngModelChange)="update(i, 'name', $event)" />
          </div>
          <div class="field small">
            <label [for]="id('type', i)">Tipo</label>
            <select [id]="id('type', i)" [name]="id('type', i)" [ngModel]="row.document_type"
              (ngModelChange)="update(i, 'document_type', $event)">
              <option value="CC">CC</option>
              <option value="CE">CE</option>
              <option value="TI">TI</option>
              <option value="PA">PA</option>
            </select>
          </div>
          <div class="field">
            <label [for]="id('doc', i)">Documento</label>
            <input [id]="id('doc', i)" [name]="id('doc', i)" inputmode="numeric" maxlength="15" autocomplete="off"
              [ngModel]="row.document_number" (ngModelChange)="update(i, 'document_number', $event)" />
          </div>
          <div class="field">
            <label [for]="id('rel', i)">Parentesco</label>
            <select [id]="id('rel', i)" [name]="id('rel', i)" [ngModel]="row.relationship"
              (ngModelChange)="update(i, 'relationship', $event)">
              @for (option of relationships; track option.value) {
                <option [value]="option.value">{{ option.label }}</option>
              }
            </select>
          </div>
          <div class="field small">
            <label [for]="id('pct', i)">%</label>
            <input [id]="id('pct', i)" [name]="id('pct', i)" type="number" min="1" max="100" step="1"
              [ngModel]="row.percentage" (ngModelChange)="update(i, 'percentage', $event)" />
          </div>
          @if (rows().length > 1) {
            <button type="button" class="btn-link" (click)="remove(i)" [attr.aria-label]="'Quitar beneficiario ' + (i + 1)">
              Quitar
            </button>
          }
        </div>
      }
      <div class="footer">
        @if (rows().length < 10) {
          <button type="button" class="btn-secondary" (click)="add()">Agregar beneficiario</button>
        }
        <p class="total" [class.ok]="!error()" aria-live="polite">
          Total: {{ total() }} %@if (error(); as message) { · {{ message }} }
        </p>
      </div>
    </fieldset>
  `,
  styles: [`
    .beneficiaries { border: 1px solid var(--borde); border-radius: 12px; padding: 16px; margin: 16px 0; }
    legend { font-weight: 600; padding: 0 6px; color: var(--texto); }
    .row { display: flex; flex-wrap: wrap; gap: 10px; align-items: flex-end; padding: 8px 0; border-bottom: 1px solid var(--borde); }
    .field { display: flex; flex-direction: column; gap: 4px; min-width: 120px; flex: 1; }
    .field.grow { flex: 2; min-width: 180px; }
    .field.small { flex: 0 0 80px; min-width: 80px; }
    label { font-size: var(--t-xs); font-weight: 600; color: var(--n-700); }
    input, select { min-height: 40px; padding: 0 10px; border: 1px solid var(--n-300); border-radius: 8px; font: inherit; background: var(--superficie); }
    input:focus, select:focus, button:focus-visible { outline: 3px solid rgba(15, 109, 148, 0.35); outline-offset: 1px; }
    .footer { display: flex; flex-wrap: wrap; gap: 12px; align-items: center; justify-content: space-between; margin-top: 12px; }
    .btn-secondary { min-height: 40px; padding: 0 14px; border: 1px solid var(--marca-600); border-radius: 8px; background: var(--superficie); color: var(--marca-600); font-weight: 600; cursor: pointer; }
    .btn-link { min-height: 40px; padding: 0 8px; border: none; background: none; color: #b91c1c; font-weight: 600; cursor: pointer; text-decoration: underline; }
    .total { margin: 0; font-size: var(--t-sm); color: #b45309; }
    .total.ok { color: #15803d; }
  `],
})
export class BeneficiariesEditorComponent implements OnInit {
  readonly legend = input('Beneficiarios');
  readonly prefix = input('ben');
  readonly changed = output<Beneficiary[] | null>();

  readonly relationships = RELATIONSHIPS;
  readonly rows = signal<Row[]>([empty()]);
  readonly total = computed(() => this.rows().reduce((sum, row) => sum + (Number(row.percentage) || 0), 0));
  readonly error = computed(() => this.validate(this.rows()));

  ngOnInit(): void {
    this.changed.emit(null);
  }

  id(field: string, index: number): string {
    return `${this.prefix()}-${field}-${index}`;
  }

  update(index: number, field: keyof Row, value: string | number | null): void {
    this.rows.update((rows) => rows.map((row, i) => (i === index ? { ...row, [field]: value } : row)));
    this.emit();
  }

  add(): void {
    this.rows.update((rows) => [...rows, empty()]);
    this.emit();
  }

  remove(index: number): void {
    this.rows.update((rows) => rows.filter((_, i) => i !== index));
    this.emit();
  }

  private emit(): void {
    const rows = this.rows();
    this.changed.emit(
      this.validate(rows)
        ? null
        : rows.map((row) => ({ ...row, name: row.name.trim(), percentage: Number(row.percentage) }) as Beneficiary),
    );
  }

  private validate(rows: Row[]): string | null {
    if (rows.some((row) => row.name.trim().length < 2)) return 'Escriba el nombre de cada beneficiario';
    if (rows.some((row) => !/^[0-9]{5,15}$/.test(row.document_number))) return 'Revise los documentos (5 a 15 dígitos)';
    if (new Set(rows.map((row) => row.document_number)).size !== rows.length) return 'Un beneficiario está repetido';
    if (rows.some((row) => !row.percentage || row.percentage <= 0)) return 'Cada beneficiario necesita su porcentaje';
    if (Math.abs(this.total() - 100) > 0.001) return 'Los porcentajes deben sumar 100';
    return null;
  }
}
