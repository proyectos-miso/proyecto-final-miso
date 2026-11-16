import { Routes } from '@angular/router';
import { LoginComponent } from './features/auth/login.component';
import { authGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';
import { solventaGuard } from './core/guards/solventa.guard';

// Cada pantalla se descarga al abrirla; solo el ingreso va en el bundle inicial.
export const routes: Routes = [
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: 'login', component: LoginComponent },
  {
    path: 'cotizador',
    loadComponent: () => import('./features/cotizador/cotizador.component').then((m) => m.CotizadorComponent),
    canActivate: [authGuard, roleGuard(['advisor'])],
  },
  {
    path: 'gestion',
    loadComponent: () => import('./features/gestion/gestion.component').then((m) => m.GestionComponent),
    canActivate: [authGuard, roleGuard(['analyst'])],
  },
  {
    path: 'siniestros',
    loadComponent: () => import('./features/siniestros/claims-list.component').then((m) => m.ClaimsListComponent),
    canActivate: [authGuard, roleGuard(['analyst'])],
  },
  {
    path: 'siniestros/:claimNumber',
    loadComponent: () => import('./features/siniestros/claim-file.component').then((m) => m.ClaimFileComponent),
    canActivate: [authGuard, roleGuard(['analyst'])],
  },
  {
    path: 'socios',
    loadComponent: () => import('./features/socios/partners.component').then((m) => m.PartnersComponent),
    canActivate: [authGuard, roleGuard(['analyst']), solventaGuard],
  },
  {
    path: 'auditoria',
    loadComponent: () => import('./features/auditoria/audit.component').then((m) => m.AuditComponent),
    canActivate: [authGuard, roleGuard(['analyst']), solventaGuard],
  },
  {
    path: 'operacion',
    loadComponent: () => import('./features/operacion/operations.component').then((m) => m.OperationsComponent),
    canActivate: [authGuard, roleGuard(['analyst']), solventaGuard],
  },
  { path: '**', redirectTo: 'login' },
];
