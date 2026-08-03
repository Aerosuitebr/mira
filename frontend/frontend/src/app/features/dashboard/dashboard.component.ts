import { DatePipe } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin, of, timeout } from 'rxjs';
import { catchError } from 'rxjs/operators';
import {
  AlertItem,
  ApiService,
  AppointmentItem,
  Campaign,
  KanbanBoard,
  Proposal
} from '../../core/api.service';
import { AuthService } from '../../core/auth.service';

interface PipelineStageSummary {
  id: string;
  name: string;
  color: string;
  count: number;
}

interface RecentCard {
  id: string;
  title: string;
  companyName: string;
  stageName: string;
  stageColor: string;
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [RouterLink, DatePipe],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss'
})
export class DashboardComponent implements OnInit {
  private readonly api = inject(ApiService);
  readonly auth = inject(AuthService);

  loading = true;
  alertsLoading = true;
  loadError = false;

  alerts: AlertItem[] = [];
  clientTotal = 0;
  crmCardTotal = 0;
  campaignTotal = 0;
  messagesSent = 0;
  proposalTotal = 0;
  openProposalTotal = 0;

  pipelineStages: PipelineStageSummary[] = [];
  recentCards: RecentCard[] = [];
  upcomingAppointments: AppointmentItem[] = [];
  recentCampaigns: Campaign[] = [];

  readonly funnelSteps = [
    { num: '01', title: 'Descobrir', desc: 'Empresas por CNAE, cidade ou mapa', route: '/discover' },
    { num: '02', title: 'Prospectar', desc: 'Qualifique listas e priorize oportunidades', route: '/prospecting' },
    { num: '03', title: 'Enriquecer', desc: 'Contatos de decisores em tempo real', route: '/enrich' },
    { num: '04', title: 'Abordar', desc: 'Campanhas com copy IA', route: '/outreach' },
    { num: '05', title: 'CRM', desc: 'Pipeline Kanban e próximos passos', route: '/crm' }
  ];

  get unreadAlerts(): number {
    return this.alerts.filter((alert) => !alert.read).length;
  }

  get hasOperationalData(): boolean {
    return (
      this.clientTotal > 0 ||
      this.crmCardTotal > 0 ||
      this.campaignTotal > 0 ||
      this.upcomingAppointments.length > 0 ||
      this.proposalTotal > 0
    );
  }

  ngOnInit(): void {
    this.loading = true;
    this.alertsLoading = true;
    this.loadError = false;

    // Núcleo rápido: não espera alertas (que podem sincronizar mercado em background).
    forkJoin({
      clients: this.api.getClientPortfolioStats().pipe(
        timeout(8000),
        catchError(() => of({ total: 0, empty: true }))
      ),
      board: this.api.board().pipe(
        timeout(8000),
        catchError(() => of(null as KanbanBoard | null))
      ),
      campaigns: this.api.campaigns().pipe(
        timeout(8000),
        catchError(() => of([] as Campaign[]))
      ),
      appointments: this.api.listAppointments().pipe(
        timeout(8000),
        catchError(() => of([] as AppointmentItem[]))
      ),
      proposals: this.api.listProposals().pipe(
        timeout(8000),
        catchError(() => of([] as Proposal[]))
      )
    }).subscribe({
      next: ({ clients, board, campaigns, appointments, proposals }) => {
        this.clientTotal = clients.total ?? 0;
        this.applyBoard(board);
        this.applyCampaigns(campaigns);
        this.applyAppointments(appointments);
        this.applyProposals(proposals);
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.loadError = true;
      }
    });

    this.api
      .alerts()
      .pipe(
        timeout(12000),
        catchError(() => of([] as AlertItem[]))
      )
      .subscribe({
        next: (alerts) => {
          this.alerts = alerts;
          this.alertsLoading = false;
        },
        error: () => {
          this.alertsLoading = false;
        }
      });
  }

  markRead(alert: AlertItem): void {
    this.api.markAlertRead(alert.id).subscribe((updated) => {
      this.alerts = this.alerts.map((item) => (item.id === updated.id ? updated : item));
    });
  }

  private applyBoard(board: KanbanBoard | null): void {
    if (!board?.stages?.length) {
      this.pipelineStages = [];
      this.recentCards = [];
      this.crmCardTotal = 0;
      return;
    }

    this.pipelineStages = board.stages.map((stage) => ({
      id: stage.id,
      name: stage.name,
      color: stage.color || '#6366f1',
      count: stage.cards?.length ?? 0
    }));

    this.crmCardTotal = this.pipelineStages.reduce((sum, stage) => sum + stage.count, 0);

    this.recentCards = board.stages
      .flatMap((stage) =>
        (stage.cards ?? []).map((card) => ({
          id: card.id,
          title: card.title,
          companyName: card.companyName,
          stageName: stage.name,
          stageColor: stage.color || '#6366f1',
          sortKey: card.createdAt ?? ''
        }))
      )
      .sort((a, b) => b.sortKey.localeCompare(a.sortKey))
      .slice(0, 5)
      .map(({ sortKey: _sortKey, ...card }) => card);
  }

  private applyCampaigns(campaigns: Campaign[]): void {
    this.campaignTotal = campaigns.length;
    this.messagesSent = campaigns.reduce((sum, campaign) => sum + (campaign.sentCount || 0), 0);
    this.recentCampaigns = [...campaigns]
      .sort((a, b) => (b.createdAt || '').localeCompare(a.createdAt || ''))
      .slice(0, 5);
  }

  private applyAppointments(appointments: AppointmentItem[]): void {
    const now = Date.now();
    this.upcomingAppointments = [...appointments]
      .filter((item) => {
        const status = (item.status || '').toUpperCase();
        if (status === 'CANCELLED' || status === 'DONE' || status === 'COMPLETED') {
          return false;
        }
        return new Date(item.startsAt).getTime() >= now - 60 * 60 * 1000;
      })
      .sort((a, b) => new Date(a.startsAt).getTime() - new Date(b.startsAt).getTime())
      .slice(0, 5);
  }

  private applyProposals(proposals: Proposal[]): void {
    this.proposalTotal = proposals.length;
    this.openProposalTotal = proposals.filter((proposal) => {
      const status = (proposal.status || '').toUpperCase();
      return status !== 'APPROVED' && status !== 'REJECTED' && status !== 'EXPIRED';
    }).length;
  }
}
