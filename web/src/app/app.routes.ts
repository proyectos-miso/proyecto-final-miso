import { Routes } from '@angular/router';
import { LoginComponent } from './features/auth/login.component';
import { CotizadorComponent } from './features/cotizador/cotizador.component';
import { authGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';
import { GestionComponent } from './features/gestion/gestion.component';

export const routes: Routes = [
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: 'login', component: LoginComponent },
  { path: 'cotizador', component: CotizadorComponent, canActivate: [authGuard, roleGuard(['advisor'])] },
  { path: 'gestion', component: GestionComponent, canActivate: [authGuard, roleGuard(['analyst'])] },
  { path: '**', redirectTo: 'login' },
];

