import { CurrencyPipe } from '@angular/common';
import { Component, HostListener, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import {
  ApiService,
  ClientListItem,
  CreateClientPayload,
  PortfolioStats,
  WhatsAppConnection
} from '../../core/api.service';
import { stashAppointmentPrefill } from '../../core/appointment-prefill';

type PortfolioView = 'grid' | 'table';

interface WaComposeState {
  open: boolean;
  client: ClientListItem | null;
  phone: string;
  phoneDisplay: string;
  message: string;
  sending: boolean;
  sent: boolean;
  error: string;
}

@Component({
  selector: 'app-clients',
  standalone: true,
  imports: [FormsModule, RouterLink, CurrencyPipe],
  templateUrl: './clients.component.html',
  styleUrl: './clients.component.scss'
})
export class ClientsComponent implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private waStatusTimer: ReturnType<typeof setInterval> | null = null;

  clients: ClientListItem[] = [];
  stats: PortfolioStats | null = null;
  loading = true;
  portfolioEmpty = true;
  tenureDays = '';
  minLtv = '';
  serviceStatus = 'ALL';
  viewMode: PortfolioView = 'grid';
  openMenuId: string | null = null;

  waConnection: WhatsAppConnection | null = null;
  waStatusLoading = true;

  waCompose: WaComposeState = this.emptyWaCompose();

  showManualForm = false;
  savingManual = false;
  manualError = '';
  manualForm: CreateClientPayload = this.emptyManualForm();

  ngOnInit(): void {
    const saved = localStorage.getItem('mira.clients.viewMode');
    if (saved === 'grid' || saved === 'table') {
      this.viewMode = saved;
    }
    this.refreshPortfolioState();
    this.refreshWhatsAppStatus();
    this.waStatusTimer = setInterval(() => this.refreshWhatsAppStatus(true), 20000);
  }

  ngOnDestroy(): void {
    if (this.waStatusTimer) {
      clearInterval(this.waStatusTimer);
      this.waStatusTimer = null;
    }
  }

  @HostListener('document:click')
  onDocumentClick(): void {
    this.openMenuId = null;
  }

  get filtersDisabled(): boolean {
    return this.portfolioEmpty || this.loading;
  }

  get kpiActive(): number {
    return this.stats?.activeCount ?? this.clients.filter((c) => c.status === 'ACTIVE').length;
  }

  get kpiTotalLtv(): number {
    if (this.stats?.totalLtv != null) {
      return Number(this.stats.totalLtv);
    }
    return this.clients.reduce((sum, c) => sum + (Number(c.lifetimeValue) || 0), 0);
  }

  get kpiAvgLtv(): number {
    if (this.stats?.avgLtv != null) {
      return Number(this.stats.avgLtv);
    }
    const total = this.stats?.total ?? this.clients.length;
    return total > 0 ? this.kpiTotalLtv / total : 0;
  }

  get waConnected(): boolean {
    return !!this.waConnection?.connected;
  }

  get canSendWa(): boolean {
    return (
      this.waConnected &&
      !!this.waCompose.phone &&
      this.waCompose.message.trim().length > 0 &&
      !this.waCompose.sending
    );
  }

  setViewMode(mode: PortfolioView): void {
    this.viewMode = mode;
    localStorage.setItem('mira.clients.viewMode', mode);
  }

  load(): void {
    if (this.portfolioEmpty) {
      this.clients = [];
      this.loading = false;
      return;
    }

    this.loading = true;
    const params: { tenureDays?: number; minLtv?: number; serviceStatus?: string } = {};
    if (this.tenureDays) params.tenureDays = Number(this.tenureDays);
    if (this.minLtv) params.minLtv = Number(this.minLtv);
    if (this.serviceStatus !== 'ALL') params.serviceStatus = this.serviceStatus;

    this.api.listClients(params).subscribe({
      next: (clients) => {
        this.clients = clients;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
      }
    });
  }

  toggleMenu(event: Event, clientId: string): void {
    event.preventDefault();
    event.stopPropagation();
    this.openMenuId = this.openMenuId === clientId ? null : clientId;
  }

  openWhatsApp(event: Event, client: ClientListItem): void {
    event.preventDefault();
    event.stopPropagation();
    this.openMenuId = null;

    const phone = this.cleanPhone(client.phone);
    if (!phone) {
      return;
    }

    this.refreshWhatsAppStatus();

    this.waCompose = {
      open: true,
      client,
      phone,
      phoneDisplay: this.formatPhoneDisplay(phone),
      message: this.defaultWaMessage(client),
      sending: false,
      sent: false,
      error: ''
    };
  }

  closeWaCompose(): void {
    if (this.waCompose.sending) {
      return;
    }
    this.waCompose = this.emptyWaCompose();
  }

  sendWaMessage(): void {
    if (!this.canSendWa) {
      if (!this.waConnected) {
        this.waCompose = {
          ...this.waCompose,
          error: 'WhatsApp desconectado. Conecte a sessão em Conexões → WhatsApp.'
        };
      }
      return;
    }

    this.waCompose = { ...this.waCompose, sending: true, error: '', sent: false };
    this.api
      .sendWhatsAppMessage({
        phone: this.waCompose.phone,
        message: this.waCompose.message.trim(),
        clientId: this.waCompose.client?.id
      })
      .subscribe({
        next: (result) => {
          if (result.success) {
            this.waCompose = {
              ...this.waCompose,
              sending: false,
              sent: true,
              error: ''
            };
          } else {
            this.waCompose = {
              ...this.waCompose,
              sending: false,
              sent: false,
              error: result.error || 'Não foi possível enviar a mensagem.'
            };
          }
        },
        error: (err) => {
          const msg = err?.error?.message || err?.error || 'Falha ao enviar pelo WhatsApp conectado.';
          this.waCompose = {
            ...this.waCompose,
            sending: false,
            sent: false,
            error: typeof msg === 'string' ? msg : 'Falha ao enviar pelo WhatsApp conectado.'
          };
          this.refreshWhatsAppStatus();
        }
      });
  }

  openProposal(event: Event, client: ClientListItem): void {
    event.preventDefault();
    event.stopPropagation();
    this.openMenuId = null;
    sessionStorage.setItem(
      'mira-proposal-client-prefill',
      JSON.stringify({
        clientId: client.id,
        label: client.displayName,
        subtitle: `${client.city}/${client.state} · Carteira`,
        phone: client.phone || undefined
      })
    );
    void this.router.navigate(['/proposals']);
  }

  openAppointment(event: Event, client: ClientListItem): void {
    event.preventDefault();
    event.stopPropagation();
    this.openMenuId = null;
    stashAppointmentPrefill({
      clientId: client.id,
      clientName: client.displayName,
      clientCompany: client.displayName,
      clientEmail: client.email,
      clientPhone: client.phone,
      title: `Alinhamento · ${client.displayName}`,
      description: 'Reunião de alinhamento / pós-venda com cliente da carteira.'
    });
    void this.router.navigate(['/agenda']);
  }

  hasPhone(client: ClientListItem): boolean {
    return !!this.cleanPhone(client.phone);
  }

  serviceBadgeClass(status: string): string {
    const normalized = (status || '').toUpperCase();
    if (normalized === 'ACTIVE') return 'badge--active';
    if (normalized === 'NONE') return 'badge--none';
    return 'badge--neutral';
  }

  formatDocument(doc: string): string {
    const digits = (doc || '').replace(/\D/g, '');
    if (digits.length !== 14) {
      return doc;
    }
    return digits.replace(/^(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})$/, '$1.$2.$3/$4-$5');
  }

  formatPhoneDisplay(phone: string): string {
    const digits = phone.replace(/\D/g, '');
    if (digits.length === 13 && digits.startsWith('55')) {
      return `+${digits.slice(0, 2)} (${digits.slice(2, 4)}) ${digits.slice(4, 9)}-${digits.slice(9)}`;
    }
    if (digits.length === 12 && digits.startsWith('55')) {
      return `+${digits.slice(0, 2)} (${digits.slice(2, 4)}) ${digits.slice(4, 8)}-${digits.slice(8)}`;
    }
    return phone;
  }

  openManualForm(): void {
    this.manualError = '';
    this.manualForm = this.emptyManualForm();
    this.showManualForm = true;
  }

  closeManualForm(): void {
    this.showManualForm = false;
    this.manualError = '';
  }

  submitManualForm(): void {
    this.manualError = '';
    const payload: CreateClientPayload = {
      legalName: this.manualForm.legalName.trim(),
      tradeName: this.manualForm.tradeName?.trim() || undefined,
      document: this.manualForm.document.trim(),
      email: this.manualForm.email?.trim() || undefined,
      phone: this.manualForm.phone?.trim() || undefined,
      city: this.manualForm.city.trim(),
      state: this.manualForm.state.trim().toUpperCase(),
      initialValue: this.manualForm.initialValue ? Number(this.manualForm.initialValue) : undefined
    };

    if (!payload.legalName || !payload.document || !payload.city || !payload.state) {
      this.manualError = 'Preencha razão social, CNPJ, cidade e UF.';
      return;
    }

    this.savingManual = true;
    this.api.createClient(payload).subscribe({
      next: () => {
        this.savingManual = false;
        this.closeManualForm();
        this.refreshPortfolioState();
      },
      error: (err) => {
        this.savingManual = false;
        this.manualError = err?.error?.message || err?.error || 'Não foi possível cadastrar o cliente.';
      }
    });
  }

  private defaultWaMessage(client: ClientListItem): string {
    const first = (client.displayName || 'tudo bem').trim().split(/\s+/)[0];
    return `Olá, ${first}! Passando para um alinhamento rápido sobre a conta. Posso te ajudar com algo agora?`;
  }

  private refreshWhatsAppStatus(silent = false): void {
    if (!silent) {
      this.waStatusLoading = true;
    }
    this.api.whatsappStatus().subscribe({
      next: (status) => {
        this.waConnection = status;
        this.waStatusLoading = false;
      },
      error: () => {
        this.waConnection = null;
        this.waStatusLoading = false;
      }
    });
  }

  private cleanPhone(phone?: string): string | null {
    if (!phone) {
      return null;
    }
    let digits = phone.replace(/\D/g, '');
    if (digits.length < 10) {
      return null;
    }
    if (!digits.startsWith('55')) {
      digits = `55${digits}`;
    }
    return digits;
  }

  private refreshPortfolioState(): void {
    this.loading = true;
    this.api.getClientPortfolioStats().subscribe({
      next: (stats) => {
        this.stats = stats;
        this.portfolioEmpty = stats.empty;
        this.load();
      },
      error: () => {
        this.portfolioEmpty = true;
        this.stats = null;
        this.loading = false;
      }
    });
  }

  private emptyWaCompose(): WaComposeState {
    return {
      open: false,
      client: null,
      phone: '',
      phoneDisplay: '',
      message: '',
      sending: false,
      sent: false,
      error: ''
    };
  }

  private emptyManualForm(): CreateClientPayload {
    return {
      legalName: '',
      tradeName: '',
      document: '',
      email: '',
      phone: '',
      city: '',
      state: '',
      initialValue: undefined
    };
  }
}
