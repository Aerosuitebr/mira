import { Component, HostListener, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiService, Campaign, CampaignDetail, CampaignMessageDetail, OutreachBotStatus, WhatsAppConnection } from '../../core/api.service';

interface RetryProgressSession {
  campaignId: string;
  campaignName: string;
  messageIds: string[];
  startedAt: string;
}

@Component({
  selector: 'app-campaign-control', standalone: true, imports: [FormsModule, DatePipe, RouterLink],
  templateUrl: './campaign-control.component.html', styleUrl: './campaign-control.component.scss'
})
export class CampaignControlComponent implements OnInit, OnDestroy {
  private readonly retryStorageKey = 'mira.campaignRetryProgress';
  private api = inject(ApiService);
  campaigns: Campaign[] = [];
  detail: CampaignDetail | null = null;
  bot: OutreachBotStatus | null = null;
  whatsapp: WhatsAppConnection | null = null;
  selectedId = '';
  search = '';
  statusFilter = 'ALL';
  stepFilter = 0;
  loading = true;
  busy = false;
  feedback = '';
  error = '';
  editing: CampaignMessageDetail | null = null;
  editBody = '';
  editSubject = '';
  editFollowUp = false;
  followUpDraft = '';
  detailModalOpen = false;
  modalSearch = '';
  modalFilter = 'ALL';
  retryDialog: 'confirm' | 'progress' | null = null;
  campaignActionDialog: 'cancel' | null = null;
  retrySession: RetryProgressSession | null = null;
  retryProgressDetail: CampaignDetail | null = null;
  private openModalAfterLoad = false;
  private poll: ReturnType<typeof setInterval> | null = null;
  private retryPoll: ReturnType<typeof setInterval> | null = null;

  ngOnInit(): void {
    this.restoreRetryProgress();
    this.loadCampaigns();
    this.poll = setInterval(() => this.refresh(false), 10_000);
  }
  ngOnDestroy(): void {
    if (this.poll) clearInterval(this.poll);
    if (this.retryPoll) clearInterval(this.retryPoll);
  }

  get filteredMessages(): CampaignMessageDetail[] {
    const query = this.search.trim().toLocaleLowerCase('pt-BR');
    return (this.detail?.messages || []).filter(m =>
      (!this.stepFilter || m.step === this.stepFilter) &&
      (this.statusFilter === 'ALL' || m.status === this.statusFilter) &&
      (!query || [m.companyName, m.cnpj, m.contactName, m.recipient, m.email].some(v => String(v || '').toLocaleLowerCase('pt-BR').includes(query)))
    );
  }

  get uniqueCompanies(): number { return new Set((this.detail?.messages || []).map(m => m.companyId)).size; }
  get retryableMessages(): CampaignMessageDetail[] { return (this.detail?.messages || []).filter(message => message.retryable); }
  get retryableCount(): number { return this.retryableMessages.length; }
  get retryProgressMessages(): CampaignMessageDetail[] {
    if (!this.retrySession || !this.retryProgressDetail) return [];
    const tracked = new Set(this.retrySession.messageIds);
    return this.retryProgressDetail.messages.filter(message => tracked.has(message.id));
  }
  get retrySentCount(): number {
    return this.retryProgressMessages.filter(message => ['SENT', 'WAITING_REPLY', 'REPLIED', 'AWAITING_APPROVAL'].includes(message.status)).length;
  }
  get retryFailedCount(): number {
    return this.retryProgressMessages.filter(message => ['FAILED', 'SKIPPED', 'THROTTLED'].includes(message.status)).length;
  }
  get retryPendingCount(): number { return Math.max(0, this.retryProgressTotal - this.retrySentCount - this.retryFailedCount); }
  get retryProgressTotal(): number { return this.retrySession?.messageIds.length || 0; }
  get retryProcessedCount(): number { return this.retrySentCount + this.retryFailedCount; }
  get retryProgressPercent(): number {
    return this.retryProgressTotal ? Math.round((this.retryProcessedCount / this.retryProgressTotal) * 100) : 0;
  }
  get retryProgressComplete(): boolean { return this.retryProgressTotal > 0 && this.retryPendingCount === 0; }
  get modalMessages(): CampaignMessageDetail[] {
    const query = this.modalSearch.trim().toLocaleLowerCase('pt-BR');
    return (this.detail?.messages || []).filter(message => {
      const matchesSearch = !query || [message.companyName, message.cnpj, message.city, message.state].some(value => String(value || '').toLocaleLowerCase('pt-BR').includes(query));
      const matchesFilter = this.modalFilter === 'ALL' ||
        (this.modalFilter === 'QUEUE_1' && message.step === 1 && !message.sentAt && !['FAILED', 'SKIPPED'].includes(message.status)) ||
        (this.modalFilter === 'SENT_1' && message.step === 1 && !!message.sentAt) ||
        (this.modalFilter === 'REPLIED' && (!!message.repliedAt || ['REPLIED', 'AWAITING_APPROVAL'].includes(message.status))) ||
        (this.modalFilter === 'PENDING_2' && message.step === 2 && !message.sentAt) ||
        (this.modalFilter === 'SENT_2' && message.step === 2 && !!message.sentAt) ||
        (this.modalFilter === 'FAILED' && ['FAILED', 'SKIPPED', 'CANCELLED'].includes(message.status));
      return matchesSearch && matchesFilter;
    });
  }
  get deliveryPercent(): number {
    if (!this.detail || !this.uniqueCompanies) return 0;
    return Math.round((this.detail.sent / this.uniqueCompanies) * 100);
  }

  get hasWhatsAppConnectionIssue(): boolean {
    return this.whatsapp !== null && !this.whatsapp.connected;
  }
  get hasPreviousConnectionFailures(): boolean {
    return (this.detail?.messages || []).some(message => this.isConnectionError(message.errorDetail));
  }
  get currentWhatsAppNumber(): string { return this.whatsapp?.phone?.trim() || 'número atual'; }

  friendlyDeliveryIssue(message: CampaignMessageDetail): string {
    const detail = message.errorDetail || '';
    if (this.isConnectionError(detail)) return this.whatsapp?.connected
      ? `Tentativa anterior não concluída. O WhatsApp atual (${this.currentWhatsAppNumber}) está conectado; reenfileire para tentar novamente.`
      : 'WhatsApp desconectado. Reconecte a conta para continuar o envio.';
    if (/sem whatsapp|sem destinat[aá]rio|telefone|n[uú]mero inv[aá]lido/i.test(detail)) return 'Este contato precisa de um número de WhatsApp válido.';
    if (/limit|thrott|rate|restri[cç][aã]o/i.test(detail)) return 'Envio temporariamente pausado para proteger sua conta.';
    return 'Não foi possível concluir este envio. Verifique a conexão e tente novamente.';
  }

  campaignStatusLabel(campaign: Campaign): string {
    if (campaign.id === this.selectedId && (this.hasWhatsAppConnectionIssue || !this.bot?.deliveryEnabled || this.bot?.paused)) return 'PAUSADA';
    return ({QUEUED:'NA FILA',SENDING:'EM ANDAMENTO',RUNNING:'EM ANDAMENTO',SENT:'CONCLUÍDA',COMPLETED:'CONCLUÍDA',FAILED:'REQUER ATENÇÃO',PAUSED:'PAUSADA',CANCELLED:'CANCELADA'} as Record<string,string>)[campaign.status] || campaign.status;
  }

  loadCampaigns(): void {
    this.loading = true;
    this.api.campaigns().subscribe({ next: rows => {
      this.campaigns = rows; this.loading = false;
      if (!this.selectedId && rows[0]) this.selectCampaign(rows[0].id);
    }, error: () => { this.loading = false; this.error = 'Não foi possível carregar as campanhas.'; } });
  }

  selectCampaign(id: string, openModal = false): void {
    this.selectedId = id;
    this.openModalAfterLoad = openModal;
    this.refresh(true);
  }
  refresh(showLoading = true): void {
    if (!this.selectedId) return;
    if (showLoading) this.loading = true;
    this.api.campaignDetail(this.selectedId).subscribe({ next: detail => {
      this.detail = detail; this.followUpDraft = detail.followUpBody || ''; this.loading = false;
      if (this.openModalAfterLoad) { this.detailModalOpen = true; this.openModalAfterLoad = false; }
    }, error: e => { this.loading = false; this.error = e?.error?.message || 'Falha ao consultar a campanha.'; } });
    this.api.outreachBotStatus().subscribe({ next: status => this.bot = status, error: () => this.bot = null });
    this.api.whatsappStatus().subscribe({ next: status => this.whatsapp = status, error: () => this.whatsapp = null });
  }

  closeDetailModal(): void { this.detailModalOpen = false; }
  setModalFilter(filter: string): void { this.modalFilter = this.modalFilter === filter ? 'ALL' : filter; }
  refreshModal(): void { this.refresh(false); }
  openEditFromModal(message: CampaignMessageDetail): void { this.closeDetailModal(); this.openEdit(message); }
  @HostListener('document:keydown.escape')
  closeModalWithEscape(): void {
    if (this.campaignActionDialog) this.campaignActionDialog = null;
    else if (this.retryDialog === 'confirm') this.retryDialog = null;
    else if (this.retryDialog === 'progress') this.minimizeRetryProgress();
    else if (this.detailModalOpen) this.closeDetailModal();
  }

  toggleCampaign(): void {
    if (!this.detail || this.detail.status === 'CANCELLED') return; this.busy = true; this.clearFeedback();
    const paused = this.detail.status === 'PAUSED';
    (paused ? this.api.resumeCampaign(this.detail.id) : this.api.pauseCampaign(this.detail.id)).subscribe({
      next: d => { this.detail = d; this.syncCampaignSummary(d); this.busy = false; this.feedback = paused ? 'Campanha reativada. As mensagens pendentes foram realinhadas na fila.' : 'Campanha pausada. Os eventos foram retirados da fila do robô e permanecem preservados.'; },
      error: e => { this.busy = false; this.error = e?.error?.message || 'Não foi possível alterar a campanha.'; }
    });
  }

  requestCancelCampaign(): void {
    if (!this.detail || this.detail.status === 'CANCELLED' || this.busy) return;
    this.clearFeedback();
    this.campaignActionDialog = 'cancel';
  }

  confirmCancelCampaign(): void {
    if (!this.detail || this.detail.status === 'CANCELLED' || this.busy) return;
    this.busy = true;
    this.clearFeedback();
    this.api.cancelCampaign(this.detail.id).subscribe({
      next: detail => {
        this.detail = detail;
        this.syncCampaignSummary(detail);
        this.busy = false;
        this.campaignActionDialog = null;
        this.feedback = 'Campanha cancelada definitivamente. Todos os eventos pendentes foram removidos da fila do robô.';
      },
      error: e => {
        this.busy = false;
        this.error = e?.error?.message || 'Não foi possível cancelar a campanha.';
      }
    });
  }

  openEdit(message: CampaignMessageDetail): void { this.editing = message; this.editBody = message.body; this.editSubject = message.subject || ''; this.clearFeedback(); }
  closeEdit(): void { this.editing = null; }
  saveMessage(): void {
    if (!this.detail || !this.editing || !this.editBody.trim()) return; this.busy = true;
    this.api.updateCampaignMessage(this.detail.id, this.editing.id, { subject: this.editSubject, body: this.editBody }).subscribe({
      next: updated => { this.replaceMessage(updated); this.editing = null; this.busy = false; this.feedback = 'Mensagem atualizada também no item pendente da fila.'; },
      error: e => { this.busy = false; this.error = e?.error?.message || 'Não foi possível editar a mensagem.'; }
    });
  }

  saveFollowUp(): void {
    if (!this.detail || !this.followUpDraft.trim()) return; this.busy = true;
    this.api.updateCampaign(this.detail.id, { followUpBody: this.followUpDraft }).subscribe({
      next: d => { this.detail = d; this.editFollowUp = false; this.busy = false; this.feedback = 'Mensagem padrão da etapa 2 atualizada.'; },
      error: e => { this.busy = false; this.error = e?.error?.message || 'Não foi possível atualizar a etapa 2.'; }
    });
  }

  approve(message: CampaignMessageDetail): void { this.busy = true; this.api.approveFollowUp(message.id).subscribe({
    next: () => { this.busy = false; this.feedback = `Etapa 2 de ${message.companyName} aprovada e enviada à fila prioritária.`; this.refresh(false); },
    error: e => { this.busy = false; this.error = e?.error?.message || 'Falha ao aprovar.'; }
  }); }

  retry(message: CampaignMessageDetail): void { if (!this.detail) return; this.busy = true; this.api.retryCampaignMessage(this.detail.id, message.id).subscribe({
    next: updated => { this.replaceMessage(updated); this.busy = false; this.feedback = `${message.companyName} voltou para a fila.`; },
    error: e => { this.busy = false; this.error = e?.error?.message || 'Falha ao reenfileirar.'; }
  }); }

  retryProblems(): void {
    if (!this.detail || !this.retryableCount || this.busy) return;
    this.clearFeedback();
    this.retryDialog = 'confirm';
  }

  confirmRetryProblems(): void {
    if (!this.detail || !this.retryableCount || this.busy) return;
    const campaignId = this.detail.id;
    const campaignName = this.detail.name;
    const messageIds = this.retryableMessages.map(message => message.id);
    this.busy = true;
    this.clearFeedback();
    this.api.retryCampaignProblems(campaignId).subscribe({
      next: detail => {
        this.detail = detail;
        this.busy = false;
        this.retrySession = { campaignId, campaignName, messageIds, startedAt: new Date().toISOString() };
        this.retryProgressDetail = detail;
        localStorage.setItem(this.retryStorageKey, JSON.stringify(this.retrySession));
        this.retryDialog = 'progress';
        this.startRetryPolling();
      },
      error: e => {
        this.busy = false;
        this.error = e?.error?.message || 'Não foi possível reenfileirar as mensagens com problema.';
      }
    });
  }

  minimizeRetryProgress(): void { this.retryDialog = null; }
  reopenRetryProgress(): void { this.retryDialog = 'progress'; this.pollRetryProgress(); }
  finishRetryProgress(): void {
    if (!this.retryProgressComplete) return;
    this.retryDialog = null;
    this.retrySession = null;
    this.retryProgressDetail = null;
    localStorage.removeItem(this.retryStorageKey);
    if (this.retryPoll) { clearInterval(this.retryPoll); this.retryPoll = null; }
  }

  private restoreRetryProgress(): void {
    const raw = localStorage.getItem(this.retryStorageKey);
    if (!raw) return;
    try {
      const session = JSON.parse(raw) as RetryProgressSession;
      if (!session.campaignId || !session.messageIds?.length) throw new Error('invalid retry session');
      this.retrySession = session;
      this.retryDialog = 'progress';
      this.pollRetryProgress();
      this.startRetryPolling();
    } catch {
      localStorage.removeItem(this.retryStorageKey);
    }
  }

  private startRetryPolling(): void {
    if (this.retryPoll) clearInterval(this.retryPoll);
    this.retryPoll = setInterval(() => this.pollRetryProgress(), 5_000);
  }

  pollRetryProgress(): void {
    if (!this.retrySession) return;
    this.api.campaignDetail(this.retrySession.campaignId).subscribe({
      next: detail => {
        this.retryProgressDetail = detail;
        if (detail.id === this.selectedId) this.detail = detail;
      },
      error: () => {
        this.error = 'Não foi possível atualizar o acompanhamento do reenvio.';
      }
    });
  }

  statusLabel(status: string): string { return ({QUEUED_BOT:'Na fila',WAITING_REPLY:'Aguardando resposta',REPLIED:'Respondeu',AWAITING_APPROVAL:'Aguardando aprovação',SENT:'Enviada',FAILED:'Não enviado',SKIPPED:'Requer contato',THROTTLED:'Pausada por segurança',PENDING:'Pendente',CANCELLED:'Cancelada'} as Record<string,string>)[status] || status; }
  private isConnectionError(detail: string | null): boolean { return /connection\s*closed|disconnected|conex[aã]o.*fechada|evolution\s*http/i.test(detail || ''); }
  private replaceMessage(updated: CampaignMessageDetail): void { if (this.detail) this.detail = { ...this.detail, messages: this.detail.messages.map(m => m.id === updated.id ? updated : m) }; }
  private syncCampaignSummary(detail: CampaignDetail): void {
    this.campaigns = this.campaigns.map(campaign => campaign.id === detail.id ? { ...campaign, status: detail.status } : campaign);
  }
  private clearFeedback(): void { this.feedback = ''; this.error = ''; }
}
