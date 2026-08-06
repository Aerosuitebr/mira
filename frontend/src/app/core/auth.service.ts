import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { tap } from 'rxjs/operators';
import { environment } from '../../environments/environment';

export interface AuthSession {
  token: string;
  userId: string;
  tenantId: string;
  fullName: string;
  email: string;
  role: 'ADMIN' | 'SELLER' | 'PUBLIC_USER';
  planCode: string;
  creditsRemaining: number;
  monthlyCredits: number;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly adminStorageKey = 'prospect-portal-session';
  private readonly publicStorageKey = 'mira-public-session';
  private readonly originKey = 'mira-origin';
  readonly publicMode = signal(sessionStorage.getItem(this.originKey) === 'resolva-jato');
  readonly session = signal<AuthSession | null>(this.readSession());

  constructor(private readonly http: HttpClient, private readonly router: Router) {}

  login(email: string, password: string, opts?: { publicOnly?: boolean }) {
    const publicOnly = opts?.publicOnly ?? this.publicMode();
    const path = publicOnly ? '/auth/public/login' : '/auth/login';
    return this.http
      .post<AuthSession>(`${environment.apiUrl}${path}`, { email, password })
      .pipe(tap((session) => this.persist(session)));
  }

  registerPublic(fullName: string, email: string, password: string) {
    this.activatePublicMode();
    return this.http
      .post<AuthSession>(`${environment.apiUrl}/auth/public/register`, { fullName, email, password })
      .pipe(tap((session) => this.persist(session)));
  }

  activatePublicMode(): void {
    sessionStorage.setItem(this.originKey, 'resolva-jato');
    if (!this.publicMode()) {
      this.publicMode.set(true);
      this.session.set(this.readSession());
    }
  }

  /** Volta ao login corporativo e limpa sessão/origem públicas grudadas. */
  clearPublicMode(): void {
    sessionStorage.removeItem(this.originKey);
    sessionStorage.removeItem(this.publicStorageKey);
    if (this.publicMode()) {
      this.publicMode.set(false);
      this.session.set(this.readSession());
    }
  }

  logout(): void {
    if (this.publicMode()) {
      sessionStorage.removeItem(this.publicStorageKey);
    } else {
      localStorage.removeItem(this.adminStorageKey);
    }
    this.session.set(null);
    void this.router.navigate(['/login'], {
      queryParams: this.publicMode() ? { origem: 'resolva-jato' } : undefined
    });
  }

  token(): string | null {
    return this.session()?.token ?? null;
  }

  isAuthenticated(): boolean {
    return !!this.token();
  }

  isPublicUser(): boolean {
    return this.session()?.role === 'PUBLIC_USER';
  }

  private persist(session: AuthSession): void {
    const storage = this.publicMode() ? sessionStorage : localStorage;
    storage.setItem(this.publicMode() ? this.publicStorageKey : this.adminStorageKey, JSON.stringify(session));
    this.session.set(session);
  }

  private readSession(): AuthSession | null {
    const storage = this.publicMode() ? sessionStorage : localStorage;
    const raw = storage.getItem(this.publicMode() ? this.publicStorageKey : this.adminStorageKey);
    if (!raw) {
      return null;
    }
    try {
      const parsed = JSON.parse(raw) as AuthSession;
      return {
        ...parsed,
        monthlyCredits: parsed.monthlyCredits ?? 2000
      };
    } catch {
      return null;
    }
  }
}
