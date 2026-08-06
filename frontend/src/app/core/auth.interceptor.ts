import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const token = auth.token();
  const isPublicApiRequest =
    (req.url.includes('/api/public/') && !req.url.includes('/api/auth/public/')) ||
    req.url.includes('/webhooks/evolution/approvals/');
  if (token && !isPublicApiRequest) {
    req = req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
  }

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      const isAuthRequest = req.url.includes('/auth/login') || req.url.includes('/auth/public/');
      const isCnaeCatalogRequest = req.url.includes('/discovery/cnaes');
      const isDiscoverySearchRequest = req.url.includes('/discovery/companies');
      // WhatsApp connect/qr pode falhar no provedor sem invalidar a sessão do MIRA.
      const isWhatsAppSessionRequest =
        req.url.includes('/whatsapp/connect') ||
        req.url.includes('/whatsapp/qr') ||
        req.url.includes('/whatsapp/status') ||
        req.url.includes('/whatsapp/disconnect') ||
        req.url.includes('/whatsapp/send');
      const isPublicOutreachRequest = req.url.includes('/public/outreach/');
      const isPublicProposalRequest = req.url.includes('/public/proposals');
      if (
        !isAuthRequest &&
        !isCnaeCatalogRequest &&
        !isDiscoverySearchRequest &&
        !isWhatsAppSessionRequest &&
        !isPublicOutreachRequest &&
        !isPublicProposalRequest &&
        error.status === 401 &&
        auth.isAuthenticated()
      ) {
        auth.logout();
      }
      return throwError(() => error);
    })
  );
};
