import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Beneficiary } from '../../core/models/policy.model';
import { BeneficiariesEditorComponent } from './beneficiaries-editor.component';

// Beneficiarios de la póliza (HU-WEB-15: RC-01).
describe('BeneficiariesEditorComponent (HU-WEB-15)', () => {
  let fixture: ComponentFixture<BeneficiariesEditorComponent>;
  let emitted: (Beneficiary[] | null)[];

  const page = () => fixture.nativeElement as HTMLElement;
  const type = (id: string, value: string) => {
    const field = page().querySelector(`#${id}`) as HTMLInputElement | HTMLSelectElement;
    field.value = value;
    field.dispatchEvent(new Event(field.tagName === 'SELECT' ? 'change' : 'input'));
    fixture.detectChanges();
  };
  const fill = (i: number, name: string, doc: string, pct: string) => {
    type(`ben-name-${i}`, name);
    type(`ben-doc-${i}`, doc);
    type(`ben-pct-${i}`, pct);
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [BeneficiariesEditorComponent] }).compileComponents();
    fixture = TestBed.createComponent(BeneficiariesEditorComponent);
    emitted = [];
    fixture.componentInstance.changed.subscribe((value) => emitted.push(value));
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('empieza inválido y avisa qué falta', () => {
    expect(emitted).toEqual([null]);
    expect(page().querySelector('.total')?.textContent).toContain('Escriba el nombre');
  });

  it('con dos beneficiarios que suman 100 entrega la lista', async () => {
    fill(0, 'Lucía Prueba', '52000111', '60');
    (page().querySelector('.btn-secondary') as HTMLButtonElement).click();
    fixture.detectChanges();
    await fixture.whenStable();
    fill(1, 'Mateo Prueba', '1100220033', '40');
    type('ben-rel-1', 'HIJO');

    expect(emitted.at(-1)).toEqual([
      { name: 'Lucía Prueba', document_type: 'CC', document_number: '52000111', relationship: 'CONYUGE', percentage: 60 },
      { name: 'Mateo Prueba', document_type: 'CC', document_number: '1100220033', relationship: 'HIJO', percentage: 40 },
    ]);
    expect(page().querySelector('.total')?.textContent).toContain('Total: 100 %');
  });

  it('no acepta porcentajes que no sumen 100, documentos inválidos ni repetidos', async () => {
    fill(0, 'Lucía Prueba', '52000111', '90');
    expect(emitted.at(-1)).toBeNull();
    expect(page().querySelector('.total')?.textContent).toContain('deben sumar 100');

    type('ben-doc-0', '12ab');
    expect(page().querySelector('.total')?.textContent).toContain('Revise los documentos');

    type('ben-doc-0', '52000111');
    type('ben-pct-0', '50');
    (page().querySelector('.btn-secondary') as HTMLButtonElement).click();
    fixture.detectChanges();
    await fixture.whenStable();
    fill(1, 'Otro Nombre', '52000111', '50');
    expect(emitted.at(-1)).toBeNull();
    expect(page().querySelector('.total')?.textContent).toContain('repetido');
  });

  it('cada campo tiene su etiqueta', () => {
    const inputs = [...page().querySelectorAll('input, select')];
    expect(inputs.every((field) => page().querySelector(`label[for="${field.id}"]`))).toBe(true);
  });
});
