import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { ThemeService } from '../../core/theme.service';

import { BrandMarkComponent } from '../../shared/brand-mark/brand-mark.component';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, BrandMarkComponent],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss'
})
export class LoginComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  readonly theme = inject(ThemeService);

  loading = false;
  error = '';
  publicAccess = false;
  registering = false;

  form = this.fb.nonNullable.group({
    fullName: ['', [Validators.maxLength(200)]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]]
  });

  constructor() {
    this.publicAccess =
      this.route.snapshot.queryParamMap.get('origem') === 'resolva-jato' || this.auth.publicMode();
    if (this.publicAccess) {
      this.auth.activatePublicMode();
      this.registering = true;
    } else {
      this.form.patchValue({ email: 'demo@prospectportal.com', password: 'demo123' });
    }
  }

  submit(): void {
    if (this.form.invalid || (this.publicAccess && this.registering && !this.form.controls.fullName.value.trim())) {
      return;
    }
    this.loading = true;
    this.error = '';
    const { fullName, email, password } = this.form.getRawValue();
    const request = this.publicAccess && this.registering
      ? this.auth.registerPublic(fullName, email, password)
      : this.auth.login(email, password);
    request.subscribe({
      next: () => {
        const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
        void this.router.navigateByUrl(this.safeReturnUrl(returnUrl));
      },
      error: (response) => {
        this.error = response.status === 409
          ? 'Este e-mail já possui cadastro. Entre na sua conta.'
          : this.publicAccess
            ? 'Não foi possível continuar. Confira os dados informados.'
            : 'Credenciais inválidas. Use demo@prospectportal.com / demo123';
        this.loading = false;
      },
      complete: () => {
        this.loading = false;
      }
    });
  }

  toggleMode(): void {
    this.registering = !this.registering;
    this.error = '';
  }

  private safeReturnUrl(value: string | null): string {
    if (!value || !value.startsWith('/') || value.startsWith('//')) {
      return this.publicAccess ? '/escolher-busca' : '/';
    }
    return value;
  }
}
