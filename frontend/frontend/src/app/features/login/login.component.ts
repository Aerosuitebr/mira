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

  form = this.fb.nonNullable.group({
    email: ['demo@prospectportal.com', [Validators.required, Validators.email]],
    password: ['demo123', Validators.required]
  });

  submit(): void {
    if (this.form.invalid) {
      return;
    }
    this.loading = true;
    this.error = '';
    const { email, password } = this.form.getRawValue();
    this.auth.login(email, password).subscribe({
      next: () => {
        const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
        void this.router.navigateByUrl(this.safeReturnUrl(returnUrl));
      },
      error: () => {
        this.error = 'Credenciais inválidas. Use demo@prospectportal.com / demo123';
        this.loading = false;
      },
      complete: () => {
        this.loading = false;
      }
    });
  }

  private safeReturnUrl(value: string | null): string {
    if (!value || !value.startsWith('/') || value.startsWith('//')) return '/';
    return value;
  }
}
