import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { OperatorService } from '../operator/operator.service';

/** Pestañas de Solventa («Socios», «Auditoría»), solo de su equipo (FE-WEB-05, FE-WEB-08); el analista de un socio vuelve a «Pólizas». Solo oculta: el servidor decide. */
export const solventaGuard: CanActivateFn = () => {
  const router = inject(Router);
  return inject(OperatorService)
    .isSolventa()
    .pipe(map((solventa) => (solventa ? true : router.parseUrl('/gestion'))));
};
