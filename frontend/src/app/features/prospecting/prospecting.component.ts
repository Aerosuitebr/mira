import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, HostListener, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  ApiService,
  AppointmentItem,
  Company,
  CreateAppointmentPayload,
  CreateClientPayload,
  ProspectJob,
  OutreachReport,
  OutreachBotStatus,
  FollowUpReviewItem,
  WhatsAppConnection,
  OutreachSettings
} from '../../core/api.service';
import { AerosuiteFitAnalysis, AerosuiteFitService, AerosuitePlanId } from '../../core/aerosuite-fit.service';
import { formatBrazilPhoneDisplay } from '../../core/phone.util';
import { loadDiscoverSession } from '../../core/discover-session';

type ProspectMode = 'pj' | 'pf';
type MetricLensId = 'icp' | 'plans' | 'promo' | 'sources' | 'routes' | 'speed';
type ProspectStep = 1 | 2 | 3;
type OutreachFlow = 'TWO_STEP' | 'DIRECT';

interface ProspectPreset {
  id: string;
  title: string;
  subtitle: string;
  cnae: string;
  state: string;
  city: string;
  revenue: string;
  pitch: string;
}

interface FunnelMetric {
  id: MetricLensId;
  label: string;
  value: string;
  hint: string;
}

interface PlanBreakdownItem {
  id: AerosuitePlanId;
  label: string;
  shortLabel: string;
  count: number;
  mrr: number;
  monthlyPrice: number;
  setupPrice: number;
}

interface PlaybookStep {
  title: string;
  body: string;
  channel: string;
}

@Component({
  selector: 'app-prospecting',
  standalone: true,
  imports: [FormsModule, RouterLink, DecimalPipe, CurrencyPipe, DatePipe],
  templateUrl: './prospecting.component.html',
  styleUrl: './prospecting.component.scss'
})
export class ProspectingComponent implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly aerosuiteFit = inject(AerosuiteFitService);
  private readonly fitByCompanyId = new Map<string, AerosuiteFitAnalysis>();

  mode: ProspectMode = 'pj';
  activeStep: ProspectStep = 1;
  outreachFlow: OutreachFlow = 'TWO_STEP';
  expandedCompanyId: string | null = null;
  loadingCompanies = false;
  savingPerson = false;
  scheduling = false;
  statusMessage = '';
  errorMessage = '';

  companies: Company[] = [];
  importedResultCount = 0;
  selectedCompanyIds = new Set<string>();
  appointments: AppointmentItem[] = [];
  lastCreatedAppointment: AppointmentItem | null = null;
  meetingDraftOpen = false;

  readonly defaultPresetId = 'mro-aeronaves';
  activePresetId = this.defaultPresetId;
  activeMetricId: MetricLensId = 'icp';
  activePlanFilter: AerosuitePlanId | null = null;

  filters = {
    keyword: '',
    cnae: '33163',
    state: 'RJ',
    city: '',
    revenue: ''
  };

  personForm = this.emptyPersonForm();
  quickMeeting = this.emptyMeetingForm();

  startingAutoProspect = false;
  sendingTestEmail = false;
  sendingTestWhatsApp = false;
  activeJob: ProspectJob | null = null;
  outreachReport: OutreachReport | null = null;
  outreachBotStatus: OutreachBotStatus | null = null;
  whatsappConnection: WhatsAppConnection | null = null;
  outreachSettings: OutreachSettings | null = null;
  loadingReadiness = true;
  updatingOutreachBot = false;
  followUpsAwaitingApproval: FollowUpReviewItem[] = [];
  approvingFollowUpId: string | null = null;
  private jobPollHandle: ReturnType<typeof setInterval> | null = null;
  private outreachPollHandle: ReturnType<typeof setInterval> | null = null;

  autoProspect = {
    limit: 5,
    testMode: false,
    openingMessage: `${this.saoPauloGreeting()}! Tudo bem? Nesse contato falo com o responsável comercial da {{empresa}}?`
  };

  get openingMessagePreview(): string {
    const company = this.selectedCompanies[0];
    const companyName = company ? (company.tradeName || company.legalName) : 'Empresa selecionada';
    return this.autoProspect.openingMessage.replaceAll('{{empresa}}', companyName);
  }

  get openingPreviewCompanyName(): string {
    const company = this.selectedCompanies[0];
    return company ? (company.tradeName || company.legalName) : 'um lead';
  }

  get approvalAdminCount(): number {
    return this.approvalAdmins.length;
  }

  get approvalAdmins(): string[] {
    return [this.outreachSettings?.approvalRecipient1, this.outreachSettings?.approvalRecipient2]
      .filter((recipient): recipient is string => !!recipient?.trim())
      .map((recipient) => formatBrazilPhoneDisplay(recipient));
  }

  get connectedWhatsAppLabel(): string {
    if (!this.whatsappConnection?.connected) return 'Não conectado';
    return formatBrazilPhoneDisplay(this.whatsappConnection.phone)
      || this.whatsappConnection.instanceName?.trim()
      || 'Conectado';
  }

  private saoPauloGreeting(): string {
    const hour = Number(new Intl.DateTimeFormat('pt-BR', {
      timeZone: 'America/Sao_Paulo',
      hour: '2-digit',
      hourCycle: 'h23'
    }).format(new Date()));
    if (hour < 12) return 'Olá, bom dia';
    if (hour < 18) return 'Olá, boa tarde';
    return 'Olá, boa noite';
  }

  readonly presets: ProspectPreset[] = [
    {
      id: 'mro-aeronaves',
      title: 'MRO Aeronaves',
      subtitle: 'Oficinas de manutenção e reparação de aeronaves (CNAE 33163)',
      cnae: '33163',
      state: 'RJ',
      city: '',
      revenue: '',
      pitch: 'Enquadrar oficinas Part 145 nos planos Aero Suite: proposta comercial até CRS rastreável.'
    },
    {
      id: 'premium-local',
      title: 'Comércio premium local',
      subtitle: 'Lojas, clínicas e serviços com alto potencial regional',
      cnae: '',
      state: 'RJ',
      city: 'Cabo Frio',
      revenue: 'MEDIUM',
      pitch: 'Aumentar agenda, recorrência e recompras com uma carteira ativa.'
    },
    {
      id: 'b2b-services',
      title: 'Serviços B2B escaláveis',
      subtitle: 'Empresas que vendem para outras empresas e precisam de previsibilidade',
      cnae: '',
      state: 'RJ',
      city: '',
      revenue: 'LARGE',
      pitch: 'Organizar prospecção, propostas e follow-up para encurtar o ciclo comercial.'
    },
    {
      id: 'liberals',
      title: 'Profissionais liberais',
      subtitle: 'Consultórios, escritórios, corretores e especialistas independentes',
      cnae: '',
      state: 'RJ',
      city: '',
      revenue: 'SMALL',
      pitch: 'Capturar indicações, agendar conversas e manter relacionamento ativo.'
    }
  ];

  readonly aerosuiteMetrics: FunnelMetric[] = [
    { id: 'icp', label: 'ICP MRO no RJ', value: '80+', hint: 'Estabelecimentos ativos CNAE 33163 na base RF (RJ)' },
    { id: 'plans', label: 'Planos Aero Suite', value: '3', hint: 'Tenant nuvem · VPS dedicada · On-premise' },
    { id: 'promo', label: 'Promo lançamento', value: '24m', hint: 'Valor promocional garantido + trial 7 dias (Plano I)' }
  ];

  readonly aerosuitePlaybook: PlaybookStep[] = [
    {
      title: 'Diagnóstico MRO',
      body: 'Abra citando a dor da proposta desconectada da OS e peça como rastreiam peças e certificados hoje.',
      channel: 'ICP'
    },
    {
      title: 'Demo focada',
      body: 'Mostre o fluxo Proposta → OS → Hangar → Estoque FIFO → CRS. Use o plano sugerido no card.',
      channel: 'Demo'
    },
    {
      title: 'Trial / PoC',
      body: 'Oficinas enxutas: trial 7 dias (Plano I). Médias/grandes: piloto com SGQ incluso no Plano II.',
      channel: 'WhatsApp'
    },
    {
      title: 'Fechamento',
      body: 'Apresente setup promocional + mensalidade travada 24 meses. Mês 1 já inclui implantação assistida.',
      channel: 'Proposta'
    }
  ];
  readonly metrics: FunnelMetric[] = [
    { id: 'sources', label: 'Fontes ativas', value: '6', hint: 'Receita Federal, carteira, agenda, indicação, mapa e campanha' },
    { id: 'routes', label: 'Rotas prontas', value: '2', hint: 'Pessoa jurídica e pessoa física' },
    { id: 'speed', label: 'Tempo até ação', value: '< 2 min', hint: 'Buscar, selecionar e enviar para o próximo passo' }
  ];

  readonly playbook: PlaybookStep[] = [
    {
      title: 'Sinal',
      body: 'Escolha um nicho claro, cidade e porte. Uma lista pequena e precisa converte melhor que uma busca genérica.',
      channel: 'ICP'
    },
    {
      title: 'Primeiro toque',
      body: 'Use dor específica: agenda vazia, baixa recompra, perda de follow-up ou carteira sem rotina.',
      channel: 'WhatsApp'
    },
    {
      title: 'Compromisso',
      body: 'Convide para uma conversa curta e registre a reunião imediatamente para não perder timing.',
      channel: 'Agenda'
    },
    {
      title: 'Loop viral',
      body: 'Depois de entregar valor, peça uma indicação com mensagem pronta e benefício claro para quem indica.',
      channel: 'Indicação'
    }
  ];

  ngOnInit(): void {
    const hasIncomingSelection = this.restoreIncomingSelection();
    const hasDiscoverContext = this.restoreDiscoverContext();
    if (this.route.snapshot.queryParamMap.get('step') === '3') {
      this.activeStep = 3;
    }
    if (this.route.snapshot.queryParamMap.get('step') !== '3' && (hasIncomingSelection || hasDiscoverContext)) {
      this.activeStep = 2;
    }
    this.loadAppointments();
    this.refreshLatestJob();
    this.loadOutreachReport();
    this.loadOutreachBotStatus();
    this.loadSendingReadiness();
    this.loadFollowUpsAwaitingApproval();
    this.startOutreachPolling();
  }

  private restoreDiscoverContext(): boolean {
    const session = loadDiscoverSession();
    if (!session?.hasSearched || session.companies.length === 0) return false;
    this.filters = {
      keyword: session.filters.keyword || '', cnae: session.filters.cnae || '',
      state: session.filters.state || '', city: session.filters.city || '', revenue: session.filters.revenue || ''
    };
    this.importedResultCount = session.totalElements || session.companies.length;
    if (this.companies.length === 0) {
      this.companies = session.companies;
      this.selectedCompanyIds = new Set(session.selectedIds || []);
      this.rebuildFitCache(this.companies);
    }
    return true;
  }

  get importedSegmentTitle(): string {
    const preset = this.presets.find(item => item.cnae && item.cnae === this.filters.cnae);
    return preset?.title || this.filters.keyword || (this.filters.cnae ? 'Pesquisa por CNAE' : 'Pesquisa personalizada');
  }

  get importedSegmentSummary(): string {
    const parts = [this.filters.cnae ? `CNAE ${this.filters.cnae}` : '', this.filters.state,
      this.filters.city, this.filters.revenue ? this.revenueLabel(this.filters.revenue) : ''].filter(Boolean);
    return parts.length ? parts.join(' · ') : 'Pesquisa ampla, sem filtros geográficos adicionais';
  }

  ngOnDestroy(): void {
    if (this.jobPollHandle) clearInterval(this.jobPollHandle);
    if (this.outreachPollHandle) clearInterval(this.outreachPollHandle);
  }

  private restoreIncomingSelection(): boolean {
    try {
      const rawIds = sessionStorage.getItem('selected-companies');
      const ids = rawIds ? (JSON.parse(rawIds) as string[]) : [];
      if (!Array.isArray(ids) || ids.length === 0) {
        return false;
      }
      this.selectedCompanyIds = new Set(ids);
      const rawCompanies = sessionStorage.getItem('selected-companies-cache');
      const selected = rawCompanies ? (JSON.parse(rawCompanies) as Company[]) : [];
      if (Array.isArray(selected) && selected.length > 0) {
        this.companies = selected.filter((company) => this.selectedCompanyIds.has(company.id));
        this.importedResultCount = this.companies.length;
        this.rebuildFitCache(this.companies);
      }
      return true;
    } catch {
      return false;
    }
  }

  startAutoProspect(): void {
    this.startingAutoProspect = true;
    this.errorMessage = '';
    this.api
      .startProspectJob({
        name: `Auto · ${this.filters.cnae || 'segmento'} · ${this.filters.state || 'BR'}`,
        cnae: this.filters.cnae || undefined,
        state: this.filters.state || undefined,
        city: this.filters.city || undefined,
        keyword: this.filters.keyword || undefined,
        companyLimit: this.autoProspect.limit,
        testMode: this.autoProspect.testMode,
        dryRun: false
      })
      .subscribe({
        next: (job) => {
          this.activeJob = job;
          this.loadOutreachReport();
          this.startingAutoProspect = false;
          this.activeStep = 3;
          this.statusMessage = `Fila preparada (${job.status}). Nesta fase, nenhuma mensagem é enviada automaticamente.`;
          this.startJobPolling(job.id);
        },
        error: (err) => {
          this.startingAutoProspect = false;
          this.errorMessage = err?.error?.message || 'Falha ao iniciar prospecção automática.';
        }
      });
  }

  sendTestEmail(): void {
    this.sendingTestEmail = true;
    this.errorMessage = '';
    this.api.sendTestEmail().subscribe({
      next: (result) => {
        this.sendingTestEmail = false;
        if (result.success) {
          this.statusMessage = `E-mail de teste enviado para ${result.deliveredTo}. Assunto: ${result.subject}`;
        } else {
          this.errorMessage = result.error || 'Falha no e-mail de teste.';
        }
      },
      error: (err) => {
        this.sendingTestEmail = false;
        this.errorMessage = err?.error?.message || err?.error?.error || 'Falha no e-mail de teste.';
      }
    });
  }

  sendTestWhatsApp(): void {
    this.sendingTestWhatsApp = true;
    this.errorMessage = '';
    this.api.sendTestWhatsApp(undefined).subscribe({
      next: (result) => {
        this.sendingTestWhatsApp = false;
        if (result.success) {
          this.statusMessage = `WhatsApp de teste enviado para ${result.phone} (${result.mode}).`;
        } else if (result.mode === 'needs_qr') {
          this.errorMessage =
            result.error ||
            'WhatsApp desconectado. Escaneie o QR da Evolution (instância aerosuite-default) e tente de novo.';
        } else {
          this.errorMessage = result.error || 'Falha no WhatsApp de teste.';
        }
      },
      error: (err) => {
        this.sendingTestWhatsApp = false;
        this.errorMessage = err?.error?.error || err?.error?.message || 'Falha no WhatsApp de teste.';
      }
    });
  }

  pauseJob(): void {
    if (!this.activeJob) {
      return;
    }
    this.api.pauseProspectJob(this.activeJob.id).subscribe({
      next: (job) => {
        this.activeJob = job;
        this.statusMessage = 'Prospecção pausada.';
      },
      error: () => {
        this.errorMessage = 'Não foi possível pausar o job.';
      }
    });
  }

  resumeJob(): void {
    if (!this.activeJob) {
      return;
    }
    this.api.resumeProspectJob(this.activeJob.id).subscribe({
      next: (job) => {
        this.activeJob = job;
        this.statusMessage = 'Prospecção retomada.';
        this.startJobPolling(job.id);
      },
      error: () => {
        this.errorMessage = 'Não foi possível retomar o job.';
      }
    });
  }

  private refreshLatestJob(): void {
    this.api.listProspectJobs().subscribe({
      next: (jobs) => {
        const latest = jobs[0] ?? null;
        this.activeJob = latest;
        if (latest && (latest.status === 'RUNNING' || latest.status === 'QUEUED')) {
          this.startJobPolling(latest.id);
        }
      }
    });
  }

  startSelectedTwoStepQueue(): void {
    if (this.selectedCompanyIds.size === 0) {
      this.errorMessage = 'Selecione ao menos uma empresa na etapa Lista antes de preparar a etapa 1.';
      this.activeStep = 2;
      return;
    }
    if (!this.autoProspect.openingMessage.trim()) {
      this.errorMessage = 'Escreva a primeira mensagem antes de preparar a fila.';
      return;
    }
    this.startingAutoProspect = true;
    this.errorMessage = '';
    const selectedIds = [...this.selectedCompanyIds];
    this.api.startProspectJob({
      name: `WhatsApp 2 etapas · ${selectedIds.length} leads`,
      companyLimit: selectedIds.length,
      testMode: this.autoProspect.testMode,
      dryRun: false,
      selectedCompanyIds: selectedIds,
      openingMessage: this.autoProspect.openingMessage.trim()
    }).subscribe({
      next: (job) => {
        this.activeJob = job;
        this.startingAutoProspect = false;
        this.statusMessage = `Etapa 1 preparada para ${selectedIds.length} lead(s). Nenhuma mensagem foi enviada automaticamente.`;
        this.loadOutreachReport();
        this.startJobPolling(job.id);
      },
      error: (err) => {
        this.startingAutoProspect = false;
        this.errorMessage = err?.error?.message || 'Não foi possível preparar a etapa 1.';
      }
    });
  }

  openDirectCampaign(): void {
    this.forwardSelection('/outreach');
  }

  private loadOutreachReport(): void {
    this.api.outreachReport().subscribe({
      next: (report) => (this.outreachReport = report)
    });
  }

  private loadOutreachBotStatus(): void {
    this.api.outreachBotStatus().subscribe({
      next: (status) => (this.outreachBotStatus = status),
      error: () => (this.outreachBotStatus = null)
    });
  }

  private loadSendingReadiness(): void {
    this.loadingReadiness = true;
    let pending = 2;
    const completeOne = () => {
      pending -= 1;
      if (pending === 0) this.loadingReadiness = false;
    };
    this.api.whatsappStatus().subscribe({
      next: (status) => { this.whatsappConnection = status; completeOne(); },
      error: () => { this.whatsappConnection = null; completeOne(); }
    });
    this.api.getOutreachSettings().subscribe({
      next: (settings) => { this.outreachSettings = settings; completeOne(); },
      error: () => { this.outreachSettings = null; completeOne(); }
    });
  }

  setOutreachBotPaused(paused: boolean): void {
    this.updatingOutreachBot = true;
    const request = paused ? this.api.pauseOutreachBot() : this.api.resumeOutreachBot();
    request.subscribe({
      next: (status) => {
        this.outreachBotStatus = status;
        this.updatingOutreachBot = false;
        this.statusMessage = paused
          ? 'Robô pausado. Nenhuma nova conversa será iniciada.'
          : 'Robô retomado. Ele respeitará a cadência e o limite diário configurados.';
      },
      error: () => {
        this.updatingOutreachBot = false;
        this.errorMessage = 'Não foi possível atualizar o estado do robô de outreach.';
      }
    });
  }

  private loadFollowUpsAwaitingApproval(): void {
    this.api.followUpsAwaitingApproval().subscribe({
      next: (items) => (this.followUpsAwaitingApproval = items)
    });
  }

  private startOutreachPolling(): void {
    this.outreachPollHandle = setInterval(() => {
      if (this.activeStep !== 3) return;
      this.loadOutreachBotStatus();
      this.loadSendingReadiness();
      this.loadOutreachReport();
      this.loadFollowUpsAwaitingApproval();
    }, 15_000);
  }

  approveFollowUp(id: string): void {
    this.approvingFollowUpId = id;
    this.errorMessage = '';
    this.api.approveFollowUp(id).subscribe({
      next: (result) => {
        this.approvingFollowUpId = null;
        if (result.error) {
          this.errorMessage = result.error;
          return;
        }
        this.statusMessage = 'Segunda mensagem aprovada e entregue à fila protegida do robô.';
        this.loadFollowUpsAwaitingApproval();
        this.loadOutreachReport();
      },
      error: (err) => {
        this.approvingFollowUpId = null;
        this.errorMessage = err?.error?.message || 'Não foi possível aprovar a mensagem.';
      }
    });
  }

  private startJobPolling(jobId: string): void {
    if (this.jobPollHandle) {
      clearInterval(this.jobPollHandle);
    }
    this.jobPollHandle = setInterval(() => {
      this.api.getProspectJob(jobId).subscribe({
        next: (job) => {
          this.activeJob = job;
          if (job.status === 'COMPLETED' || job.status === 'FAILED' || job.status === 'PAUSED') {
            if (this.jobPollHandle) {
              clearInterval(this.jobPollHandle);
              this.jobPollHandle = null;
            }
            if (job.status === 'COMPLETED') {
              this.statusMessage = `Preparação concluída: ${job.queuedCount} mensagens aguardam revisão.`;
            }
          }
        }
      });
    }, 8000);
  }

  get selectedCompanies(): Company[] {
    return this.companies.filter((company) => this.selectedCompanyIds.has(company.id));
  }

  get activeMetrics(): FunnelMetric[] {
    if (!this.isAerosuiteMode) {
      return this.metrics;
    }
    return this.aerosuiteMetrics.map((metric) => {
      if (metric.id === 'icp') {
        const count = this.icpMatchCount;
        return { ...metric, value: this.companies.length ? `${count}` : metric.value };
      }
      if (metric.id === 'plans') {
        const withPlan = this.companies.filter((c) => this.aerosuiteFitFor(c).icpMatch).length;
        return { ...metric, value: withPlan ? `${this.planBreakdown.filter((p) => p.count > 0).length}` : metric.value };
      }
      if (metric.id === 'promo') {
        return { ...metric, value: this.promoEligibleCount ? `${this.promoEligibleCount}` : metric.value };
      }
      return metric;
    });
  }

  get metricDetailTitle(): string {
    const titles: Record<MetricLensId, string> = {
      icp: 'Oficinas no ICP MRO',
      plans: 'Enquadramento por plano Aero Suite',
      promo: 'Oportunidades na promo de lançamento',
      sources: 'Empresas por fonte do radar',
      routes: 'Rotas de prospecção',
      speed: 'Leads com maior prontidão'
    };
    return titles[this.activeMetricId];
  }

  get metricDetailDescription(): string {
    const descriptions: Record<MetricLensId, string> = {
      icp: 'Empresas com CNAE de manutenção aeronáutica alinhadas ao perfil Part 145 da Aero Suite.',
      plans: 'Clique em um plano para filtrar oficinas enquadradas na faixa comercial correspondente.',
      promo: 'Oficinas elegíveis ao trial de 7 dias (Plano I) ou com melhor encaixe na promo de setup reduzido.',
      sources: 'Lista completa retornada pela busca atual no radar PJ.',
      routes: 'Alterne entre captura PJ (empresas) e PF (contatos/indicações).',
      speed: 'Empresas com score de prontidão alta para abordagem imediata.'
    };
    return descriptions[this.activeMetricId];
  }

  get displayedCompanies(): Company[] {
    if (!this.isAerosuiteMode) {
      if (this.activeMetricId === 'speed') {
        return this.companies.filter((company) => this.scoreCompany(company) >= 80);
      }
      return this.companies;
    }

    switch (this.activeMetricId) {
      case 'icp':
        return this.companies.filter((company) => this.aerosuiteFitFor(company).icpMatch);
      case 'plans':
        return this.companies.filter((company) => {
          const fit = this.aerosuiteFitFor(company);
          if (!fit.icpMatch) {
            return false;
          }
          if (!this.activePlanFilter) {
            return true;
          }
          return fit.plan.id === this.activePlanFilter;
        });
      case 'promo':
        return this.companies.filter((company) => {
          const fit = this.aerosuiteFitFor(company);
          return fit.trialEligible || fit.plan.recommended || fit.readiness === 'alta';
        });
      default:
        return this.companies;
    }
  }

  get planBreakdown(): PlanBreakdownItem[] {
    const buckets: Record<AerosuitePlanId, PlanBreakdownItem> = {
      PLANO_I: {
        id: 'PLANO_I',
        label: 'Plano I · Tenant na Nuvem',
        shortLabel: 'Plano I',
        count: 0,
        mrr: 0,
        monthlyPrice: 2490,
        setupPrice: 990
      },
      PLANO_II: {
        id: 'PLANO_II',
        label: 'Plano II · Dedicada na Nuvem',
        shortLabel: 'Plano II',
        count: 0,
        mrr: 0,
        monthlyPrice: 4990,
        setupPrice: 1490
      },
      PLANO_III: {
        id: 'PLANO_III',
        label: 'Plano III · Dedicada Física',
        shortLabel: 'Plano III',
        count: 0,
        mrr: 0,
        monthlyPrice: 5480,
        setupPrice: 4900
      }
    };

    this.companies.forEach((company) => {
      const fit = this.aerosuiteFitFor(company);
      if (!fit.icpMatch) {
        return;
      }
      const bucket = buckets[fit.plan.id];
      bucket.count += 1;
      bucket.mrr += fit.plan.monthlyPrice;
    });

    return Object.values(buckets);
  }

  get promoEligibleCount(): number {
    return this.companies.filter((company) => this.aerosuiteFitFor(company).trialEligible).length;
  }

  get activePlaybook(): PlaybookStep[] {
    return this.isAerosuiteMode ? this.aerosuitePlaybook : this.playbook;
  }

  get isAerosuiteMode(): boolean {
    return this.activePresetId === 'mro-aeronaves';
  }

  get pipelineMrr(): number {
    return this.selectedCompanies.reduce(
      (sum, company) => sum + this.aerosuiteFitFor(company).plan.monthlyPrice,
      0
    );
  }

  get pipelineValue(): number {
    return this.selectedCompanies.reduce((sum, company) => sum + this.estimateTicket(company), 0);
  }

  get pipelineSetup(): number {
    return this.selectedCompanies.reduce(
      (sum, company) => sum + this.aerosuiteFitFor(company).plan.setupPrice,
      0
    );
  }

  get icpMatchCount(): number {
    return this.companies.filter((company) => this.aerosuiteFitFor(company).icpMatch).length;
  }

  get upcomingMeetings(): AppointmentItem[] {
    const now = Date.now();
    return this.appointments
      .filter((item) => item.status === 'SCHEDULED' && new Date(item.startsAt).getTime() >= now - 60_000)
      .sort((a, b) => new Date(a.startsAt).getTime() - new Date(b.startsAt).getTime())
      .slice(0, 4);
  }

  get meetingDate(): string {
    return this.quickMeeting.startsAt.slice(0, 10);
  }

  set meetingDate(value: string) {
    const time = this.meetingTime || '09:00';
    this.quickMeeting.startsAt = value ? `${value}T${time}` : '';
    this.lastCreatedAppointment = null;
  }

  get meetingTime(): string {
    return this.quickMeeting.startsAt.slice(11, 16);
  }

  set meetingTime(value: string) {
    const date = this.meetingDate || this.toDatetimeLocal(new Date()).slice(0, 10);
    this.quickMeeting.startsAt = value ? `${date}T${value}` : '';
    this.lastCreatedAppointment = null;
  }

  setMode(mode: ProspectMode): void {
    this.mode = mode;
    this.errorMessage = '';
    this.statusMessage = '';
    if (mode === 'pj') {
      this.activeStep = 1;
    }
  }

  goToStep(step: ProspectStep): void {
    if (step === 2 && this.companies.length === 0 && !this.loadingCompanies) {
      this.activeStep = 1;
      this.errorMessage = 'Faça uma pesquisa em Descobrir para formar a lista da campanha.';
      return;
    }
    if (step === 2 && this.companies.length > 0 && this.displayedCompanies.length === 0) {
      this.activeMetricId = 'sources';
      this.activePlanFilter = null;
    }
    this.activeStep = step;
  }

  toggleCompanyExpand(companyId: string): void {
    this.expandedCompanyId = this.expandedCompanyId === companyId ? null : companyId;
  }

  isCompanyExpanded(companyId: string): boolean {
    return this.expandedCompanyId === companyId;
  }

  applyPreset(preset: ProspectPreset, options: { announce?: boolean } = {}): void {
    this.activePresetId = preset.id;
    this.activeMetricId = preset.id === 'mro-aeronaves' ? 'icp' : 'sources';
    this.activePlanFilter = null;
    this.filters = {
      keyword: '',
      cnae: preset.cnae,
      state: preset.state,
      city: preset.city,
      revenue: preset.revenue
    };
    if (options.announce !== false) {
      this.statusMessage = `Radar ajustado para ${preset.title.toLowerCase()}.`;
    }
    this.searchCompanies();
  }

  isPresetActive(presetId: string): boolean {
    return this.activePresetId === presetId;
  }

  selectMetric(metricId: MetricLensId): void {
    if (metricId === 'routes' && !this.isAerosuiteMode) {
      this.setMode('pf');
      this.statusMessage = 'Rota PF ativa: capture contatos e indicações.';
      return;
    }

    this.activeMetricId = metricId;
    this.activePlanFilter = null;
    this.mode = 'pj';
    this.statusMessage = `Visualizando: ${this.metricDetailTitle}.`;
  }

  selectPlanFilter(planId: AerosuitePlanId | null): void {
    this.activeMetricId = 'plans';
    this.activePlanFilter = this.activePlanFilter === planId ? null : planId;
    const plan = this.planBreakdown.find((item) => item.id === planId);
    this.statusMessage = plan
      ? `Filtrando ${plan.shortLabel}: ${plan.count} oficina(s) no radar.`
      : 'Mostrando todos os planos Aero Suite.';
  }

  isMetricActive(metricId: MetricLensId): boolean {
    return this.activeMetricId === metricId;
  }

  searchCompanies(options: { advanceToList?: boolean } = {}): void {
    this.loadingCompanies = true;
    this.errorMessage = '';
    this.api.searchCompanies({ ...this.filters, page: 0, size: 100 }).subscribe({
      next: (page) => {
        this.companies = page.content;
        this.rebuildFitCache(page.content);
        this.loadingCompanies = false;
        if (options.advanceToList) {
          this.activeMetricId = 'sources';
          this.activePlanFilter = null;
          this.activeStep = 2;
        }
      },
      error: () => {
        this.loadingCompanies = false;
        this.errorMessage = 'Não foi possível buscar empresas agora.';
      }
    });
  }

  toggleCompany(companyId: string): void {
    if (this.selectedCompanyIds.has(companyId)) {
      this.selectedCompanyIds.delete(companyId);
    } else {
      this.selectedCompanyIds.add(companyId);
    }
    this.selectedCompanyIds = new Set(this.selectedCompanyIds);
  }

  clearSelection(): void {
    this.selectedCompanyIds = new Set();
  }

  sendSelectionToEnrichment(): void {
    this.forwardSelection('/enrich');
  }

  sendSelectionToOutreach(): void {
    this.forwardSelection('/outreach');
  }

  fillMeetingFromCompany(company: Company): void {
    const fit = this.aerosuiteFitFor(company);
    const companyName = company.tradeName || company.legalName;
    this.lastCreatedAppointment = null;
    this.quickMeeting.clientName = companyName;
    this.quickMeeting.clientCompany = company.legalName;
    this.quickMeeting.clientEmail = company.email ?? '';
    this.quickMeeting.clientPhone = company.phone ?? '';
    this.quickMeeting.title = `Aero Suite · diagnóstico MRO: ${companyName}`;
    this.quickMeeting.description = fit.pitchLine;
    this.quickMeeting.location = company.city ? `${company.city}/${company.state}` : '';
    this.meetingDraftOpen = true;
    this.statusMessage = `Apresentação preparada para ${companyName}. Escolha a data e confirme o agendamento.`;
  }

  closeMeetingDraft(): void {
    this.meetingDraftOpen = false;
    this.lastCreatedAppointment = null;
  }

  @HostListener('document:keydown.escape')
  closeMeetingDraftWithEscape(): void {
    if (this.meetingDraftOpen) {
      this.closeMeetingDraft();
    }
  }

  returnToCompanyList(): void {
    this.mode = 'pj';
    this.activeStep = this.companies.length > 0 ? 2 : 1;
    this.errorMessage = '';
  }

  returnToSegment(): void {
    this.mode = 'pj';
    this.activeStep = 1;
    this.errorMessage = '';
  }

  aerosuiteFitFor(company: Company): AerosuiteFitAnalysis {
    return this.fitByCompanyId.get(company.id) ?? this.aerosuiteFit.analyze(company);
  }

  segmentLabel(segment: string): string {
    const labels: Record<string, string> = {
      MRO_HANGAR: 'MRO hangar',
      MRO_PISTA: 'MRO pista',
      MRO_AERONAVES: 'MRO aeronaves',
      FABRICANTE_AERONAVES: 'Fabricante',
      FABRICANTE_PECAS: 'Peças aeronáuticas',
      OPERADOR_AEREO: 'Operador aéreo',
      AVIACAO_RELACIONADA: 'Aviação relacionada',
      FORA_ICP: 'Fora do ICP'
    };
    return labels[segment] ?? segment;
  }

  readinessLabel(readiness: AerosuiteFitAnalysis['readiness']): string {
    const labels = { alta: 'Prontidão alta', media: 'Prontidão média', baixa: 'Prontidão baixa' };
    return labels[readiness];
  }

  savePerson(): void {
    this.errorMessage = '';
    this.statusMessage = '';
    const payload: CreateClientPayload = {
      legalName: this.personForm.name.trim(),
      tradeName: this.personForm.company.trim() || undefined,
      document: this.personForm.document.trim(),
      email: this.personForm.email.trim() || undefined,
      phone: this.personForm.phone.trim() || undefined,
      city: this.personForm.city.trim(),
      state: this.personForm.state.trim().toUpperCase(),
      initialValue: this.personForm.initialValue ? Number(this.personForm.initialValue) : undefined
    };

    if (!payload.legalName || !payload.document || !payload.city || !payload.state) {
      this.errorMessage = 'Preencha nome, documento, cidade e UF para salvar na carteira.';
      return;
    }

    this.savingPerson = true;
    this.api.createClient(payload).subscribe({
      next: () => {
        this.savingPerson = false;
        this.statusMessage = 'Contato salvo na carteira. Próximo passo: agendar uma conversa.';
        this.quickMeeting.clientName = payload.legalName;
        this.quickMeeting.clientEmail = payload.email ?? '';
        this.quickMeeting.clientPhone = payload.phone ?? '';
        this.quickMeeting.clientCompany = payload.tradeName ?? '';
        this.personForm = this.emptyPersonForm();
      },
      error: (err) => {
        this.savingPerson = false;
        this.errorMessage = err?.error?.message || err?.error || 'Não foi possível salvar este contato.';
      }
    });
  }

  scheduleMeeting(): void {
    this.errorMessage = '';
    const startsAt = this.quickMeeting.startsAt;
    if (!this.quickMeeting.clientName.trim() || !this.quickMeeting.title.trim() || !startsAt) {
      this.errorMessage = 'Informe nome, título e data da reunião.';
      return;
    }

    const payload: CreateAppointmentPayload = {
      clientName: this.quickMeeting.clientName.trim(),
      clientEmail: this.quickMeeting.clientEmail.trim() || undefined,
      clientPhone: this.quickMeeting.clientPhone.trim() || undefined,
      clientCompany: this.quickMeeting.clientCompany.trim() || undefined,
      title: this.quickMeeting.title.trim(),
      description: this.quickMeeting.description.trim() || undefined,
      location: this.quickMeeting.videoConference ? undefined : (this.quickMeeting.location.trim() || undefined),
      videoConference: this.quickMeeting.videoConference,
      startsAt: new Date(startsAt).toISOString(),
      reminderMinutesBefore: 30
    };

    this.scheduling = true;
    this.api.createAppointment(payload).subscribe({
      next: (appointment) => {
        this.scheduling = false;
        this.lastCreatedAppointment = appointment;
        this.statusMessage = `Reunião marcada para ${new Date(appointment.startsAt).toLocaleString('pt-BR')}.`;
        this.appointments = [appointment, ...this.appointments.filter((item) => item.id !== appointment.id)];
        this.loadAppointments();
      },
      error: (err) => {
        this.scheduling = false;
        this.errorMessage = err?.error?.message || err?.error || 'Não foi possível agendar a reunião.';
      }
    });
  }

  estimateTicket(company: Company): number {
    switch (company.estimatedRevenue) {
      case 'LARGE':
        return 28000;
      case 'MEDIUM':
        return 12000;
      case 'SMALL':
        return 4500;
      default:
        return 2500;
    }
  }

  scoreCompany(company: Company): number {
    if (this.isAerosuiteMode) {
      return this.aerosuiteFitFor(company).fitScore;
    }
    let score = 56;
    if (company.website) score += 8;
    if (company.latitude && company.longitude) score += 7;
    if (company.estimatedRevenue === 'LARGE') score += 18;
    if (company.estimatedRevenue === 'MEDIUM') score += 12;
    if (company.openedAt && new Date(company.openedAt).getFullYear() <= 2020) score += 6;
    return Math.min(score, 98);
  }

  private rebuildFitCache(companies: Company[]): void {
    this.fitByCompanyId.clear();
    companies.forEach((company) => {
      this.fitByCompanyId.set(company.id, this.aerosuiteFit.analyze(company));
    });
  }

  revenueLabel(value: string): string {
    const labels: Record<string, string> = {
      SMALL: 'Pequeno',
      MEDIUM: 'Médio',
      LARGE: 'Grande'
    };
    return labels[value] ?? 'Não informado';
  }

  referralText(): string {
    const name = this.personForm.name.trim() || this.quickMeeting.clientName.trim() || 'um contato';
    if (this.isAerosuiteMode) {
      return `Olá! Estamos apresentando a Aero Suite para oficinas MRO: proposta, OS, estoque rastreável e SGQ em um só fluxo. Acho que ${name} se beneficiaria de uma demo de 20 min. Posso agendar?`;
    }
    return `Oi! Estou usando um portal para organizar carteira, prospecção e agenda. Pensei que ${name} também pode se beneficiar. Quer que eu te apresente?`;
  }

  private forwardSelection(route: '/enrich' | '/outreach'): void {
    if (this.selectedCompanyIds.size === 0) {
      this.errorMessage = 'Selecione ao menos uma empresa para avançar.';
      return;
    }
    const selected = this.companies.filter((company) => this.selectedCompanyIds.has(company.id));
    sessionStorage.setItem('selected-companies', JSON.stringify([...this.selectedCompanyIds]));
    sessionStorage.setItem('selected-companies-cache', JSON.stringify(selected));
    void this.router.navigate([route]);
  }

  private loadAppointments(): void {
    this.api.listAppointments().subscribe({
      next: (items) => {
        this.appointments = items;
      }
    });
  }

  private emptyPersonForm() {
    return {
      name: '',
      company: '',
      document: '',
      email: '',
      phone: '',
      city: '',
      state: 'RJ',
      initialValue: undefined as number | undefined
    };
  }

  private emptyMeetingForm() {
    const startsAt = new Date();
    startsAt.setMinutes(startsAt.getMinutes() + 60 - (startsAt.getMinutes() % 15));
    startsAt.setSeconds(0, 0);
    return {
      clientName: '',
      clientEmail: '',
      clientPhone: '',
      clientCompany: '',
      title: 'Conversa de diagnóstico',
      description: '',
      location: '',
      videoConference: true,
      startsAt: this.toDatetimeLocal(startsAt)
    };
  }

  private toDatetimeLocal(date: Date): string {
    const offsetDate = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
    return offsetDate.toISOString().slice(0, 16);
  }
}
