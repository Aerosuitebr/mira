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
  planCode: string;
  creditsRemaining: number;
  monthlyCredits: number;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly storageKey = 'prospect-portal-session';
  readonly session = signal<AuthSession | null>(this.readSession());

  constructor(private readonly http: HttpClient, private readonly router: Router) {}

  login(email: string, password: string) {
    return this.http
      .post<AuthSession>(`${environment.apiUrl}/auth/login`, { email, password })
      .pipe(tap((session) => this.persist(session)));
  }

  logout(): void {
    localStorage.removeItem(this.storageKey);
    this.session.set(null);
    void this.router.navigate(['/login']);
  }

  token(): string | null {
    return this.session()?.token ?? null;
  }

  isAuthenticated(): boolean {
    return !!this.token();
  }

  private persist(session: AuthSession): void {
    localStorage.setItem(this.storageKey, JSON.stringify(session));
    this.session.set(session);
  }

  private readSession(): AuthSession | null {
    const raw = localStorage.getItem(this.storageKey);
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
