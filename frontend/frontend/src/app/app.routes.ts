import { Routes } from '@angular/router';

import { authGuard } from './core/auth.guard';

import { ShellComponent } from './layout/shell.component';



export const routes: Routes = [

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

    path: 'login',

    loadComponent: () => import('./features/login/login.component').then((m) => m.LoginComponent),

    data: { animation: 'LoginPage' }

  },

  {

    path: '',

    component: ShellComponent,

    canActivate: [authGuard],

    children: [

      {

        path: 'dashboard',

        loadComponent: () => import('./features/dashboard/dashboard.component').then((m) => m.DashboardComponent),

        data: { animation: 'DashboardPage' }

      },

      {

        path: 'discover',

        loadComponent: () => import('./features/discover/discover.component').then((m) => m.DiscoverComponent),

        data: { animation: 'DiscoverPage' }

      },
      {

        path: 'prospecting',

        loadComponent: () => import('./features/prospecting/prospecting.component').then((m) => m.ProspectingComponent),

        data: { animation: 'ProspectingPage' }

      },

      {

        path: 'enrich',

        loadComponent: () => import('./features/enrich/enrich.component').then((m) => m.EnrichComponent),

        data: { animation: 'EnrichPage' }

      },

      {

        path: 'outreach',

        loadComponent: () => import('./features/outreach/outreach.component').then((m) => m.OutreachComponent),

        data: { animation: 'OutreachPage' }

      },

      {

        path: 'whatsapp',

        loadComponent: () =>
          import('./features/whatsapp/whatsapp-connection.component').then((m) => m.WhatsAppConnectionComponent),

        data: { animation: 'WhatsAppPage' }

      },

      {

        path: 'settings/envio',

        loadComponent: () =>
          import('./features/settings/outreach-settings.component').then((m) => m.OutreachSettingsComponent),

        data: { animation: 'OutreachSettingsPage' }

      },

      {

        path: 'crm',

        loadComponent: () => import('./features/crm/crm.component').then((m) => m.CrmComponent),

        data: { animation: 'CrmPage' }

      },

      {

        path: 'clients',

        loadComponent: () => import('./features/clients/clients.component').then((m) => m.ClientsComponent)

      },

      {

        path: 'clients/:id',

        loadComponent: () => import('./features/clients/client-detail.component').then((m) => m.ClientDetailComponent)

      },

      {

        path: 'proposals',

        loadComponent: () => import('./features/proposals/proposals.component').then((m) => m.ProposalsComponent)

      },

      {

        path: 'projects',

        loadComponent: () => import('./features/projects/projects.component').then((m) => m.ProjectsComponent)

      },

      {

        path: 'agenda',

        loadComponent: () => import('./features/appointments/appointments.component').then((m) => m.AppointmentsComponent),

        data: { animation: 'AgendaPage' }

      }

    ]

  },

  {

    path: 'proposta/:token',

    loadComponent: () => import('./features/public/public-proposal.component').then((m) => m.PublicProposalComponent)

  },

  { path: '**', redirectTo: 'escolher-busca' }

];


