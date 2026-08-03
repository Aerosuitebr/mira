import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService, ChannelStatus, Company, Template } from '../../core/api.service';

interface AiPreviewCopy {
  subject: string;
  body: string;
}

interface ChannelUiStatus {
  connected: boolean;
  label: string;
  connectHint?: string;
}

interface PreviewSegment {
  text: string;
  kind: 'text' | 'token' | 'dynamic';
}

type SendPhase = 'idle' | 'preparing' | 'sending' | 'success' | 'partial' | 'error';

interface SendProgressState {
  open: boolean;
  /** Canal escolhido no formulário (intenção). */
  channel: 'EMAIL' | 'WHATSAPP';
  /** Canal efetivo do lead atual (animaçao durante o disparo). */
  activeChannel: 'EMAIL' | 'WHATSAPP';
  phase: SendPhase;
  progress: number;
  currentIndex: number;
  total: number;
  currentLeadName: string;
  campaignName: string;
  sentCount: number;
  waSent: number;
  emailSent: number;
  failedCount: number;
  detail: string;
  failureLines: string[];
  statusMessage: string;
}

@Component({
  selector: 'app-outreach',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './outreach.component.html',
  styleUrl: './outreach.component.scss'
})
export class OutreachComponent implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  private progressTimer: ReturnType<typeof setInterval> | null = null;

  readonly templateVariables = [
    { token: '{{companyName}}', label: 'Nome da empresa' },
    { token: '{{contactName}}', label: 'Nome do contato' },
    { token: '{{cnaeDescription}}', label: 'CNAE' },
    { token: '{{senderName}}', label: 'Seu nome' }
  ];

  companyIds: string[] = [];
  companies: Company[] = [];
  templates: Template[] = [];
  previewCompanyId = '';
  previewsByCompany: Record<string, AiPreviewCopy> = {};
  aiPreview = '';
  aiSubject = '';
  loading = false;
  generatingAi = false;
  sendingTestEmail = false;
  message = '';
  feedbackError = false;
  channels: ChannelStatus | null = null;

  sendProgress: SendProgressState = this.emptySendProgress();

  form = this.fb.nonNullable.group({
    campaignName: ['Campanha Prospecção Aero Suite'],
    templateId: [''],
    channel: ['EMAIL'],
    productDescription: [
      'Aero Suite · Comércio · Estoque · MRO · Global. Plataforma que conecta comércio, estoque e operações aeronáuticas com rastreabilidade internacional.'
    ]
  });

  ngOnInit(): void {
    this.restoreSelectedCompanies();

    this.api.outreachChannels().subscribe({
      next: (channels) => {
        this.channels = channels;
      }
    });

    this.api.templates().subscribe((templates) => {
      this.templates = templates;
      if (templates[0]) {
        this.form.patchValue({ templateId: templates[0].id });
      }
    });
  }

  ngOnDestroy(): void {
    this.clearProgressTimer();
  }

  private restoreSelectedCompanies(): void {
    const raw = sessionStorage.getItem('selected-companies');
    this.companyIds = raw ? (JSON.parse(raw) as string[]) : [];

    const cache = sessionStorage.getItem('selected-companies-cache');
    if (cache) {
      try {
        const cached = JSON.parse(cache) as Company[];
        if (Array.isArray(cached) && cached.length > 0) {
          this.companies = this.companyIds.length
            ? cached.filter((company) => this.companyIds.includes(company.id))
            : cached;
          if (this.companies.length === 0) {
            this.companies = cached;
            this.companyIds = cached.map((company) => company.id);
          }
          this.previewCompanyId = this.companies[0]?.id ?? '';
          this.syncPreviewFromCache();
          return;
        }
      } catch {
        // segue para API
      }
    }

    if (this.companyIds.length === 0) {
      this.message = 'Nenhuma empresa selecionada. Volte ao Enriquecer e escolha quem abordar.';
      this.feedbackError = true;
      return;
    }

    this.api.getCompaniesByIds(this.companyIds).subscribe({
      next: (companies) => {
        this.companies = companies;
        sessionStorage.setItem('selected-companies-cache', JSON.stringify(companies));
        if (companies[0]) {
          this.previewCompanyId = companies[0].id;
          this.syncPreviewFromCache();
        }
      },
      error: () => {
        this.message = 'Não foi possível carregar as empresas selecionadas.';
        this.feedbackError = true;
      }
    });
  }

  get hasPreviewReady(): boolean {
    return this.aiPreview.trim().length > 0;
  }

  get previewReadyCount(): number {
    return Object.values(this.previewsByCompany).filter((preview) => preview.body.trim().length > 0).length;
  }

  get canGenerateAi(): boolean {
    return (
      this.companies.length > 0 &&
      this.form.controls.templateId.value.trim().length > 0 &&
      this.form.controls.productDescription.value.trim().length > 0 &&
      !this.generatingAi
    );
  }

  get canSendCampaign(): boolean {
    return (
      this.companyIds.length > 0 &&
      this.form.controls.templateId.value.trim().length > 0 &&
      this.channelStatus.connected &&
      this.hasPreviewReady &&
      !this.loading
    );
  }

  get channelStatus(): ChannelUiStatus {
    const channel = this.form.controls.channel.value;
    if (channel === 'WHATSAPP') {
      const connected = !!this.channels?.whatsappConnected;
      return {
        connected,
        label: this.channels?.whatsappLabel || 'WhatsApp não conectado',
        connectHint: connected
          ? undefined
          : `Conecte a instância ${this.channels?.whatsappInstance || 'mira-prospect'} na Evolution API.`
      };
    }
    const emailOk = !!this.channels?.emailConfigured;
    return {
      connected: emailOk,
      label: this.channels?.emailLabel || 'E-mail SMTP não configurado'
    };
  }

  get previewCompany(): Company | undefined {
    return this.companies.find((c) => c.id === this.previewCompanyId);
  }

  get previewSubjectSegments(): PreviewSegment[] {
    return this.segmentPreviewText(this.aiSubject);
  }

  get previewBodySegments(): PreviewSegment[] {
    return this.segmentPreviewText(this.aiPreview);
  }

  get hasPreviewTokens(): boolean {
    return (
      this.previewSubjectSegments.some((s) => s.kind !== 'text') ||
      this.previewBodySegments.some((s) => s.kind !== 'text')
    );
  }

  get sendPhaseLabel(): string {
    switch (this.sendProgress.phase) {
      case 'preparing':
        return 'Preparando disparo';
      case 'sending':
        return this.sendProgress.activeChannel === 'WHATSAPP' ? 'Enviando WhatsApp' : 'Enviando e-mail';
      case 'success':
        return 'Disparo concluído';
      case 'partial':
        return 'Disparo parcial';
      case 'error':
        return 'Falha no disparo';
      default:
        return '';
    }
  }

  get isSendBusy(): boolean {
    return this.sendProgress.phase === 'preparing' || this.sendProgress.phase === 'sending';
  }

  onPreviewCompanyChange(companyId: string): void {
    this.persistCurrentPreview();
    this.previewCompanyId = companyId;
    this.syncPreviewFromCache();
  }

  onPreviewSubjectInput(event: Event): void {
    this.aiSubject = (event.target as HTMLInputElement).value;
    this.persistCurrentPreview();
  }

  onPreviewBodyInput(event: Event): void {
    this.aiPreview = (event.target as HTMLTextAreaElement).value;
    this.persistCurrentPreview();
  }

  onPreviewBodyBlur(): void {
    if (this.form.controls.channel.value !== 'WHATSAPP' || !this.aiPreview.trim()) {
      return;
    }
    const normalized = this.normalizeWhatsAppCopy(this.aiPreview);
    if (normalized !== this.aiPreview) {
      this.aiPreview = normalized;
      this.persistCurrentPreview();
    }
  }

  onPreviewEditorScroll(event: Event, backdrop: HTMLElement): void {
    const editor = event.target as HTMLTextAreaElement;
    backdrop.scrollTop = editor.scrollTop;
    backdrop.scrollLeft = editor.scrollLeft;
  }

  insertVariable(token: string): void {
    const control = this.form.controls.productDescription;
    const current = control.value.trim();
    control.setValue(current ? `${current} ${token}` : token);
  }

  closeSendProgress(): void {
    if (this.isSendBusy) {
      return;
    }
    this.clearProgressTimer();
    this.sendProgress = this.emptySendProgress();
  }

  sendTestEmail(): void {
    this.sendingTestEmail = true;
    this.api.sendTestEmail().subscribe({
      next: (result) => {
        this.sendingTestEmail = false;
        if (result.success) {
          this.showFeedback(
            `Teste SMTP ok: e-mail de diagnóstico enviado para ${result.deliveredTo}. Isso não dispara campanha para os leads.`,
            false
          );
        } else {
          this.showFeedback(result.error || 'Falha no e-mail de teste.', true);
        }
      },
      error: () => {
        this.sendingTestEmail = false;
        this.showFeedback('Falha no e-mail de teste.', true);
      }
    });
  }

  generateAi(): void {
    if (!this.canGenerateAi) {
      this.showFeedback('Preencha o template, a proposta e selecione ao menos um lead.', true);
      return;
    }

    const company = this.previewCompany ?? this.companies[0];
    if (!company) {
      this.showFeedback('Selecione empresas na etapa Descobrir.', true);
      return;
    }

    this.generatingAi = true;
    this.api
      .generateAiCopy({
        companyId: company.id,
        channel: this.form.controls.channel.value,
        productDescription: this.form.controls.productDescription.value
      })
      .subscribe({
        next: (copy) => {
          const channel = this.form.controls.channel.value;
          const body =
            channel === 'WHATSAPP' ? this.normalizeWhatsAppCopy(copy.body) : copy.body;
          const preview: AiPreviewCopy = {
            subject: copy.subject ?? '',
            body
          };
          this.previewsByCompany[company.id] = preview;
          this.aiSubject = preview.subject;
          this.aiPreview = preview.body;
          this.generatingAi = false;
          this.showFeedback(
            `Prévia gerada para ${company.tradeName || company.legalName}. Revise à direita e só então dispare.`,
            false
          );
        },
        error: () => {
          this.generatingAi = false;
          this.showFeedback('Falha ao gerar copy com IA.', true);
        }
      });
  }

  sendCampaign(): void {
    if (!this.hasPreviewReady) {
      this.showFeedback('Gere e revise a prévia IA antes de enviar a campanha.', true);
      return;
    }
    if (!this.canSendCampaign) {
      if (!this.channelStatus.connected) {
        this.showFeedback('Conecte o canal antes de enviar a campanha.', true);
      } else if (this.companyIds.length === 0) {
        this.showFeedback('Nenhuma empresa selecionada.', true);
      }
      return;
    }

    this.persistCurrentPreview();
    this.loading = true;
    this.message = '';

    const channel = this.form.controls.channel.value === 'WHATSAPP' ? 'WHATSAPP' : 'EMAIL';
    const campaignName = this.form.controls.campaignName.value;
    this.beginSendProgress(channel, campaignName);

    const messages: Record<string, { subject?: string; body?: string }> = {};
    for (const id of this.companyIds) {
      const cached = this.previewsByCompany[id];
      const rawBody = cached?.body?.trim() ? cached.body : this.aiPreview.trim() ? this.aiPreview : '';
      if (!rawBody) {
        continue;
      }
      const body = channel === 'WHATSAPP' ? this.normalizeWhatsAppCopy(rawBody) : rawBody;
      messages[id] = {
        subject: cached?.subject ?? this.aiSubject,
        body
      };
    }

    const emailFallback = localStorage.getItem('mira.whatsapp.emailFallback');
    this.api
      .sendBulk({
        campaignName,
        templateId: this.form.controls.templateId.value,
        channel,
        companyIds: this.companyIds,
        productDescription: this.form.controls.productDescription.value,
        messages,
        emailFallback: emailFallback === null ? true : emailFallback === 'true'
      })
      .subscribe({
        next: (campaign) => {
          const failed = campaign.failedCount ?? 0;
          const sent = campaign.sentCount ?? 0;
          const waSent = campaign.waSent ?? 0;
          const emailSent = campaign.emailSent ?? 0;
          const detail = campaign.detail?.trim() || '';
          this.finishSendProgress({
            campaignName: campaign.name || campaignName,
            sent,
            waSent,
            emailSent,
            failed,
            detail
          });
          this.loading = false;
        },
        error: (err) => {
          const msg = err?.error?.message || err?.error || 'Falha ao enviar campanha.';
          const detail = typeof msg === 'string' ? msg : 'Falha ao enviar campanha.';
          this.finishSendProgress({
            campaignName,
            sent: 0,
            waSent: 0,
            emailSent: 0,
            failed: this.companyIds.length,
            detail,
            hardError: true
          });
          this.loading = false;
        }
      });
  }

  private beginSendProgress(channel: 'EMAIL' | 'WHATSAPP', campaignName: string): void {
    this.clearProgressTimer();
    const total = Math.max(this.companyIds.length, 1);
    const firstLead = this.companies[0];
    const firstActive = this.effectiveChannelForCompany(firstLead, channel);
    this.sendProgress = {
      open: true,
      channel,
      activeChannel: firstActive,
      phase: 'preparing',
      progress: 4,
      currentIndex: 0,
      total,
      currentLeadName: firstLead ? firstLead.tradeName || firstLead.legalName : 'leads',
      campaignName,
      sentCount: 0,
      waSent: 0,
      emailSent: 0,
      failedCount: 0,
      detail: '',
      failureLines: [],
      statusMessage:
        channel === 'WHATSAPP'
          ? 'Validando sessão WhatsApp e montando mensagens…'
          : 'Validando SMTP e montando mensagens…'
    };

    window.setTimeout(() => {
      if (!this.sendProgress.open || this.sendProgress.phase !== 'preparing') {
        return;
      }
      this.sendProgress = {
        ...this.sendProgress,
        phase: 'sending',
        progress: 10,
        activeChannel: firstActive,
        statusMessage:
          firstActive === 'WHATSAPP'
            ? 'Disparando mensagens no WhatsApp…'
            : 'Disparando e-mails pela caixa SMTP…'
      };
      this.startSimulatedProgress();
    }, 700);
  }

  private startSimulatedProgress(): void {
    this.clearProgressTimer();
    const total = this.sendProgress.total;
    const tickMs = Math.min(900, Math.max(280, 2400 / total));
    const requested = this.sendProgress.channel;

    this.progressTimer = setInterval(() => {
      if (this.sendProgress.phase !== 'sending') {
        return;
      }

      const nextIndex = Math.min(this.sendProgress.currentIndex + 1, total);
      const lead = this.companies[Math.min(nextIndex - 1, this.companies.length - 1)];
      const leadName = lead ? lead.tradeName || lead.legalName : `Lead ${nextIndex}`;
      const active = this.effectiveChannelForCompany(lead, requested);
      const progress = Math.min(88, Math.round((nextIndex / total) * 82) + 8);

      this.sendProgress = {
        ...this.sendProgress,
        currentIndex: nextIndex,
        currentLeadName: leadName,
        activeChannel: active,
        progress: Math.max(this.sendProgress.progress, progress),
        statusMessage:
          active === 'WHATSAPP'
            ? `Enviando WhatsApp para ${leadName}…`
            : requested === 'WHATSAPP'
              ? `Sem telefone: enviando e-mail para ${leadName}…`
              : `Enviando e-mail para ${leadName}…`
      };

      if (nextIndex >= total) {
        this.sendProgress = {
          ...this.sendProgress,
          statusMessage: 'Aguardando confirmação do servidor…'
        };
        this.clearProgressTimer();
      }
    }, tickMs);
  }

  /** Com canal WhatsApp, lead sem telefone cai no e-mail (espelha o backend). */
  private effectiveChannelForCompany(
    company: Company | undefined,
    requested: 'EMAIL' | 'WHATSAPP'
  ): 'EMAIL' | 'WHATSAPP' {
    if (requested !== 'WHATSAPP') {
      return 'EMAIL';
    }
    const phone = company?.phone?.replace(/\D/g, '') ?? '';
    return phone.length >= 10 ? 'WHATSAPP' : 'EMAIL';
  }

  private finishSendProgress(result: {
    campaignName: string;
    sent: number;
    waSent: number;
    emailSent: number;
    failed: number;
    detail: string;
    hardError?: boolean;
  }): void {
    this.clearProgressTimer();
    const failureLines = this.parseFailureLines(result.detail, result.failed);
    let phase: SendPhase = 'success';
    let statusMessage = '';

    if (result.hardError || (result.sent === 0 && result.failed > 0)) {
      phase = 'error';
      statusMessage =
        result.detail ||
        `Nenhuma mensagem foi entregue. ${result.failed} falha(s) registrada(s).`;
    } else if (result.failed > 0) {
      phase = 'partial';
      statusMessage =
        result.detail ||
        `${result.sent} entregue(s): ${result.waSent} WhatsApp · ${result.emailSent} e-mail · ${result.failed} falha(s).`;
    } else {
      phase = 'success';
      statusMessage =
        result.detail ||
        `${result.sent} entregue(s): ${result.waSent} WhatsApp · ${result.emailSent} e-mail.`;
    }

    this.sendProgress = {
      ...this.sendProgress,
      phase,
      progress: 100,
      currentIndex: this.sendProgress.total,
      campaignName: result.campaignName,
      sentCount: result.sent,
      waSent: result.waSent,
      emailSent: result.emailSent,
      failedCount: result.failed,
      detail: result.detail,
      failureLines,
      statusMessage
    };

    this.showFeedback(statusMessage, phase === 'error' || phase === 'partial');
  }

  private parseFailureLines(detail: string, failedCount: number): string[] {
    if (!detail || failedCount <= 0) {
      return [];
    }
    // Formato backend: "N enviada(s), M falha(s). Empresa: motivo | Empresa2: motivo"
    const marker = 'falha(s).';
    const idx = detail.toLowerCase().indexOf(marker);
    const sample = idx >= 0 ? detail.slice(idx + marker.length).trim() : '';
    if (!sample) {
      return [];
    }
    return sample
      .split('|')
      .map((part) => part.trim())
      .filter((part) => part.length > 0 && !/enviada\(s\) de verdade/i.test(part))
      .slice(0, 8);
  }

  private emptySendProgress(): SendProgressState {
    return {
      open: false,
      channel: 'EMAIL',
      activeChannel: 'EMAIL',
      phase: 'idle',
      progress: 0,
      currentIndex: 0,
      total: 0,
      currentLeadName: '',
      campaignName: '',
      sentCount: 0,
      waSent: 0,
      emailSent: 0,
      failedCount: 0,
      detail: '',
      failureLines: [],
      statusMessage: ''
    };
  }

  private clearProgressTimer(): void {
    if (this.progressTimer) {
      clearInterval(this.progressTimer);
      this.progressTimer = null;
    }
  }

  private persistCurrentPreview(): void {
    if (!this.previewCompanyId) {
      return;
    }
    if (!this.aiPreview.trim() && !this.aiSubject.trim()) {
      return;
    }
    this.previewsByCompany[this.previewCompanyId] = {
      subject: this.aiSubject,
      body: this.aiPreview
    };
  }

  private syncPreviewFromCache(): void {
    const cached = this.previewsByCompany[this.previewCompanyId];
    if (cached) {
      this.aiSubject = cached.subject;
      this.aiPreview = cached.body;
    } else {
      this.aiSubject = '';
      this.aiPreview = '';
    }
  }

  private showFeedback(text: string, isError: boolean): void {
    this.message = text;
    this.feedbackError = isError;
  }

  /** Remove linhas vazias duplas e negrito em taglines (leitura WhatsApp no celular). */
  private normalizeWhatsAppCopy(raw: string): string {
    if (!raw) {
      return '';
    }
    let text = raw.replace(/\r\n/g, '\n').replace(/\r/g, '\n');
    text = text.replace(/^[ \t]+$/gm, '');
    text = text.replace(/\n{3,}/g, '\n\n');
    text = text.replace(/^\*(Comércio[^*\n]*)\*$/gm, '$1');
    text = text.replace(/^\*(Aero Suite · Comércio[^*\n]*)\*$/gm, '$1');
    text = text.replace(/\n{3,}/g, '\n\n');
    return text.trim();
  }

  /** Destaca {{tokens}}, {placeholders} e dados do lead atual na prévia. */
  private segmentPreviewText(raw: string): PreviewSegment[] {
    if (!raw) {
      return [];
    }

    const tokenSplit = /(\{\{[^}]+\}\}|\{[^}]+\})/g;
    const chunks = raw.split(tokenSplit);
    const segments: PreviewSegment[] = [];

    for (const chunk of chunks) {
      if (!chunk) {
        continue;
      }
      if (/^(\{\{[^}]+\}\}|\{[^}]+\})$/.test(chunk)) {
        segments.push({ text: chunk, kind: 'token' });
      } else {
        segments.push(...this.segmentDynamicValues(chunk));
      }
    }

    return segments;
  }

  private segmentDynamicValues(text: string): PreviewSegment[] {
    const values = this.dynamicHighlightValues();
    if (values.length === 0) {
      return [{ text, kind: 'text' }];
    }

    const pattern = new RegExp(
      `(${values.map((value) => this.escapeRegExp(value)).join('|')})`,
      'gi'
    );
    const parts = text.split(pattern);
    const lookup = new Set(values.map((value) => value.toLowerCase()));

    return parts
      .filter((part) => part.length > 0)
      .map((part) => ({
        text: part,
        kind: lookup.has(part.toLowerCase()) ? ('dynamic' as const) : ('text' as const)
      }));
  }

  private dynamicHighlightValues(): string[] {
    const company = this.previewCompany;
    if (!company) {
      return [];
    }

    const cityState =
      company.city && company.state ? `${company.city}/${company.state}` : '';
    const values = [
      company.tradeName,
      company.legalName,
      company.cnaeDescription,
      cityState,
      company.city,
      company.state,
      company.email,
      company.phone
    ]
      .map((value) => (value || '').trim())
      .filter((value) => value.length >= 3);

    return [...new Set(values)].sort((a, b) => b.length - a.length);
  }

  private escapeRegExp(value: string): string {
    return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  }
}
