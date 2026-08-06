import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (state.url.includes('origem=resolva-jato')) {
    auth.activatePublicMode();
  }
  if (auth.isAuthenticated()) {
    return true;
  }
  return router.createUrlTree(['/login'], {
    queryParams: {
      returnUrl: state.url,
      ...(auth.publicMode() ? { origem: 'resolva-jato' } : {})
    }
  });
};

export const staffGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.isAuthenticated() && !auth.isPublicUser()
    ? true
    : router.createUrlTree(['/escolher-busca']);
};
