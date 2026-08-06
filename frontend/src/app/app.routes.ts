import { Routes } from '@angular/router';

import { authGuard, staffGuard } from './core/auth.guard';

import { ShellComponent } from './layout/shell.component';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/login/login.component').then((m) => m.LoginComponent),
    data: { animation: 'LoginPage' }
  },
  {
    path: 'proposta/:token',
    loadComponent: () => import('./features/public/public-proposal.component').then((m) => m.PublicProposalComponent)
  },
  {
    path: 'aprovar-abordagem/:token',
    loadComponent: () =>
      import('./features/public/public-followup-approval.component').then((m) => m.PublicFollowUpApprovalComponent)
  },
  {
    path: '',
    pathMatch: 'full',
    canActivate: [authGuard],
    loadComponent: () => import('./features/search-choice/search-choice.component').then((m) => m.SearchChoiceComponent),
    data: { animation: 'SearchChoicePage' }
  },
  {
    path: 'escolher-busca',
    loadComponent: () => import('./features/search-choice/search-choice.component').then((m) => m.SearchChoiceComponent),
    data: { animation: 'SearchChoicePage' }
  },
  {
    path: 'profissionais',
    canActivate: [authGuard],
    loadComponent: () => import('./features/professionals/professionals.component').then((m) => m.ProfessionalsComponent),
    data: { animation: 'ProfessionalsPage' }
  },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [
      {
        path: 'dashboard',
        canActivate: [staffGuard],
        loadComponent: () => import('./features/dashboard/dashboard.component').then((m) => m.DashboardComponent),
        data: { animation: 'DashboardPage' }
      },
      {
        path: 'discover/enrich',
        loadComponent: () => import('./features/enrich/enrich.component').then((m) => m.EnrichComponent),
        data: { animation: 'DiscoverEnrichPage' }
      },
      {
        path: 'discover',
        loadComponent: () => import('./features/discover/discover.component').then((m) => m.DiscoverComponent),
        data: { animation: 'DiscoverPage' }
      },
      {
        path: 'prospecting',
        canActivate: [staffGuard],
        loadComponent: () => import('./features/prospecting/prospecting.component').then((m) => m.ProspectingComponent),
        data: { animation: 'ProspectingPage' }
      },
      {
        path: 'enrich',
        redirectTo: 'discover/enrich',
        pathMatch: 'full'
      },
      {
        path: 'outreach',
        canActivate: [staffGuard],
        loadComponent: () => import('./features/outreach/outreach.component').then((m) => m.OutreachComponent),
        data: { animation: 'OutreachPage' }
      },
      {
        path: 'campaigns',
        canActivate: [staffGuard],
        loadComponent: () => import('./features/campaigns/campaign-control.component').then((m) => m.CampaignControlComponent),
        data: { animation: 'CampaignControlPage' }
      },
      {
        path: 'whatsapp',
        canActivate: [staffGuard],
        loadComponent: () =>
          import('./features/whatsapp/whatsapp-connection.component').then((m) => m.WhatsAppConnectionComponent),
        data: { animation: 'WhatsAppPage' }
      },
      {
        path: 'settings/envio',
        canActivate: [staffGuard],
        loadComponent: () =>
          import('./features/settings/outreach-settings.component').then((m) => m.OutreachSettingsComponent),
        data: { animation: 'OutreachSettingsPage' }
      },
      {
        path: 'crm',
        canActivate: [staffGuard],
        loadComponent: () => import('./features/crm/crm.component').then((m) => m.CrmComponent),
        data: { animation: 'CrmPage' }
      },
      {
        path: 'clients',
        canActivate: [staffGuard],
        loadComponent: () => import('./features/clients/clients.component').then((m) => m.ClientsComponent)
      },
      {
        path: 'clients/:id',
        canActivate: [staffGuard],
        loadComponent: () => import('./features/clients/client-detail.component').then((m) => m.ClientDetailComponent)
      },
      {
        path: 'proposals',
        canActivate: [staffGuard],
        loadComponent: () => import('./features/proposals/proposals.component').then((m) => m.ProposalsComponent)
      },
      {
        path: 'projects',
        canActivate: [staffGuard],
        loadComponent: () => import('./features/projects/projects.component').then((m) => m.ProjectsComponent)
      },
      {
        path: 'agenda',
        canActivate: [staffGuard],
        loadComponent: () => import('./features/appointments/appointments.component').then((m) => m.AppointmentsComponent),
        data: { animation: 'AgendaPage' }
      }
    ]
  },
  { path: '**', redirectTo: 'escolher-busca' }
];
