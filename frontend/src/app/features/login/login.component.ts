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
    // Cadastro publico exige 8+; login corporativo (demo123) aceita a partir de 6.
    password: ['', [Validators.required, Validators.minLength(6)]]
  });

  constructor() {
    const fromResolvaJato = this.route.snapshot.queryParamMap.get('origem') === 'resolva-jato';
    if (fromResolvaJato) {
      this.auth.activatePublicMode();
      this.publicAccess = true;
      this.registering = true;
      this.form.controls.password.setValidators([Validators.required, Validators.minLength(8)]);
      this.form.controls.password.updateValueAndValidity({ emitEvent: false });
    } else {
      // /login sem origem=resolva-jato sempre e corporativo, mesmo se a aba
      // ainda tiver mira-origin no sessionStorage de uma visita anterior.
      this.auth.clearPublicMode();
      this.publicAccess = false;
      this.form.patchValue({ email: 'demo@prospectportal.com', password: 'demo123' });
    }
  }

  submit(): void {
    if (this.form.invalid || (this.publicAccess && this.registering && !this.form.controls.fullName.value.trim())) {
      this.error = this.publicAccess && this.registering && this.form.controls.password.hasError('minlength')
        ? 'A senha precisa ter pelo menos 8 caracteres.'
        : 'Preencha os campos obrigatórios para continuar.';
      return;
    }
    this.loading = true;
    this.error = '';
    const { fullName, email, password } = this.form.getRawValue();
    const request = this.publicAccess && this.registering
      ? this.auth.registerPublic(fullName, email, password)
      : this.auth.login(email, password, { publicOnly: this.publicAccess });
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
    if (this.publicAccess && this.registering) {
      this.form.controls.password.setValidators([Validators.required, Validators.minLength(8)]);
    } else {
      this.form.controls.password.setValidators([Validators.required, Validators.minLength(6)]);
    }
    this.form.controls.password.updateValueAndValidity({ emitEvent: false });
  }

  private safeReturnUrl(value: string | null): string {
    if (!value || !value.startsWith('/') || value.startsWith('//')) {
      return this.publicAccess ? '/escolher-busca' : '/';
    }
    return value;
  }
}
