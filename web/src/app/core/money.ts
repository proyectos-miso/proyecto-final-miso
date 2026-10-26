const PESOS = new Intl.NumberFormat('es-CO', {
  style: 'currency',
  currency: 'COP',
  maximumFractionDigits: 0,
});

/** Pesos colombianos sin centavos y con el código, para que el símbolo $ no se lea como dólares. */
export const COP = {
  format: (amount: number): string => `${PESOS.format(amount)} COP`,
};
