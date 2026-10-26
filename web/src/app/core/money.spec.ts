import { COP } from './money';

describe('COP', () => {
  it('muestra pesos colombianos sin centavos y con el código de la moneda (TC-11)', () => {
    expect(COP.format(250_000_000).replace(/\s/g, ' ')).toBe('$ 250.000.000 COP');
  });

  it('redondea los centavos', () => {
    expect(COP.format(27_360.6).replace(/\s/g, ' ')).toBe('$ 27.361 COP');
  });
});
