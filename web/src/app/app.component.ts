import { Component, effect, inject, untracked } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth/auth.service';
import { OperatorService } from './core/operator/operator.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent {
  title = 'Solventa · Portal Asesores';
  private readonly authService = inject(AuthService);
  private readonly operator = inject(OperatorService);
  readonly currentSession = this.authService.currentSession;
  readonly solventa = this.operator.solventa;

  constructor() {
    // La pestaña «Socios» solo se muestra al analista que no es de un socio (FE-WEB-05).
    effect(() => {
      this.currentSession();
      untracked(() => this.operator.isSolventa().subscribe());
    });
  }

  roleLabel(role: string): string {
    return role === 'analyst' ? 'Operaciones' : 'Asesor';
  }

  logout(): void {
    this.authService.logout();
  }
}

export { AppComponent as App };
