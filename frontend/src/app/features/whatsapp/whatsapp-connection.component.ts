import { DatePipe } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService, WhatsAppConnection } from '../../core/api.service';

type UiPhase = 'loading' | 'offline' | 'disconnected' | 'qr' | 'connected' | 'error';

const FALLBACK_KEY = 'mira.whatsapp.emailFallback';
/** Polling de QR: contínuo enquanto aguarda pareamento. */
const POLL_MS = 2500;
/** Busca do número após conectar: no máx. ~30s (12 × 2,5s). */
const PHONE_SYNC_MAX_ATTEMPTS = 12;

@Component({
  selector: 'app-whatsapp-connection',
  standalone: true,
  imports: [RouterLink, DatePipe, FormsModule],
  templateUrl: './whatsapp-connection.component.html',
  styleUrl: './whatsapp-connection.component.scss'
})
export class WhatsAppConnectionComponent implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);

  connection: WhatsAppConnection | null = null;
  loading = true;
  acting = false;
  testing = false;
  error = '';
  lastCheckedAt: Date | null = null;
  justConnected = false;
  emailFallback = true;
  pollingLive = false;

  messagesSentToday = 0;
  queueStatus = '—';
  queueTone: 'ok' | 'busy' | 'off' = 'off';
  activeRateLabel = '—';
  metricsLoading = false;
  testResult: { ok: boolean; message: string } | null = null;
  toast: { ok: boolean; title: string; detail?: string } | null = null;

  private pollTimer: ReturnType<typeof setInterval> | null = null;
  private wasConnected = false;
  private celebrateTimer: ReturnType<typeof setTimeout> | null = null;
  private toastTimer: ReturnType<typeof setTimeout> | null = null;
  private metricsLoadedForConnected = false;
  /** Tentativas restantes só para descobrir o número (não o QR). */
  private phoneSyncAttemptsLeft = 0;
  phoneSyncExhausted = false;

  ngOnInit(): void {
    const stored = localStorage.getItem(FALLBACK_KEY);
    this.emailFallback = stored === null ? true : stored === 'true';
    this.refresh(false);
  }

  ngOnDestroy(): void {
    this.stopPolling();
    if (this.celebrateTimer) {
      clearTimeout(this.celebrateTimer);
    }
    if (this.toastTimer) {
      clearTimeout(this.toastTimer);
    }
  }

  get phase(): UiPhase {
    if (this.loading && !this.connection) {
      return 'loading';
    }
    if (this.error && !this.connection) {
      return 'error';
    }
    if (!this.connection?.providerEnabled) {
      return 'offline';
    }
    if (this.connection.connected) {
      return 'connected';
    }
    if (this.connection.qrCodeBase64 || this.connection.status === 'qr' || this.connection.status === 'connecting') {
      return 'qr';
    }
    return 'disconnected';
  }

  get phoneReady(): boolean {
    return !!this.connection?.phone?.trim();
  }

  /** Skeleton só enquanto ainda estamos tentando sincronizar o número. */
  get phoneSyncing(): boolean {
    return this.phase === 'connected' && !this.phoneReady && !this.phoneSyncExhausted;
  }

  get phoneDisplay(): string {
    const phone = this.connection?.phone;
    if (!phone) {
      return '';
    }
    const d = phone.replace(/\D/g, '');
    if (d.length >= 13 && d.startsWith('55')) {
      return `+${d.slice(0, 2)} ${d.slice(2, 4)} ${d.slice(4, 9)}-${d.slice(9)}`;
    }
    if (d.length >= 12 && d.startsWith('55')) {
      return `+${d.slice(0, 2)} ${d.slice(2, 4)} ${d.slice(4, 8)}-${d.slice(8)}`;
    }
    if (d.length >= 11) {
      return `+${d.slice(0, 2)} ${d.slice(2, 4)} ${d.slice(4, 9)}-${d.slice(9)}`;
    }
    return phone.startsWith('+') ? phone : `+${phone}`;
  }

  onFallbackChange(): void {
    localStorage.setItem(FALLBACK_KEY, String(this.emailFallback));
  }

  refresh(showLoader = true): void {
    if (showLoader) {
      this.loading = true;
    }
    this.error = '';
    // Clique manual: dá outra janela curta para buscar o número, sem polling eterno.
    if (this.phase === 'connected' && !this.phoneReady) {
      this.beginPhoneSyncBudget();
    }
    this.api.whatsappStatus().subscribe({
      next: (conn) => this.applyConnection(conn, showLoader),
      error: (err) => {
        this.loading = false;
        this.error = err?.error?.message || err?.error || 'Não foi possível consultar o status do WhatsApp.';
        this.stopPolling();
        this.resetMetrics();
      }
    });
  }

  testConnection(): void {
    this.testing = true;
    this.testResult = null;
    this.api.whatsappStatus().subscribe({
      next: (conn) => {
        this.testing = false;
        this.applyConnection(conn, false);
        if (conn.connected) {
          const phoneDetail = conn.phone
            ? this.formatPhone(conn.phone)
            : 'Número ainda sincronizando';
          this.testResult = {
            ok: true,
            message: `Conexão OK. Sessão ativa. ${phoneDetail}.`
          };
          this.showToast(true, 'Mensagem de teste enviada com sucesso!', phoneDetail);
        } else {
          const failMsg = conn.hint || 'Sessão não está conectada. Reconecte via QR Code.';
          this.testResult = { ok: false, message: failMsg };
          this.showToast(false, 'Falha no teste de conexão', failMsg);
        }
      },
      error: (err) => {
        this.testing = false;
        const msg = err?.error?.message || err?.error;
        const failMsg = typeof msg === 'string' && msg.trim() ? msg : 'Falha ao testar a conexão.';
        this.testResult = { ok: false, message: failMsg };
        this.showToast(false, 'Falha no teste de conexão', failMsg);
      }
    });
  }

  private showToast(ok: boolean, title: string, detail?: string): void {
    this.toast = { ok, title, detail };
    if (this.toastTimer) {
      clearTimeout(this.toastTimer);
    }
    this.toastTimer = setTimeout(() => {
      this.toast = null;
    }, 3600);
  }

  connect(): void {
    this.acting = true;
    this.error = '';
    this.api.whatsappConnect().subscribe({
      next: (conn) => {
        this.acting = false;
        this.applyConnection(conn, false);
        if (conn.status === 'disconnected' && conn.hint) {
          this.error = conn.hint;
        }
      },
      error: (err) => {
        this.acting = false;
        const msg = err?.error?.message || err?.error?.hint || err?.error;
        this.error = typeof msg === 'string' && msg.trim() ? msg : 'Falha ao iniciar a conexão.';
      }
    });
  }

  refreshQr(): void {
    this.acting = true;
    this.error = '';
    this.api.whatsappRefreshQr().subscribe({
      next: (conn) => {
        this.acting = false;
        this.applyConnection(conn, false);
      },
      error: (err) => {
        this.acting = false;
        this.error = err?.error?.message || err?.error || 'Não foi possível renovar o QR Code.';
      }
    });
  }

  configureWebhook(): void {
    this.acting = true;
    this.error = '';
    this.api.whatsappConfigureWebhook().subscribe({
      next: conn => {
        this.acting = false;
        this.applyConnection(conn, false);
        this.showToast(true, 'Webhook de respostas configurado', 'A Evolution enviará respostas ao MIRA.');
      },
      error: err => {
        this.acting = false;
        this.error = err?.error?.message || 'Não foi possível configurar o webhook.';
      }
    });
  }

  disconnect(): void {
    if (!confirm('Desconectar este WhatsApp? Os envios automáticos ficarão pausados até reconectar.')) {
      return;
    }
    this.acting = true;
    this.error = '';
    this.justConnected = false;
    this.testResult = null;
    this.api.whatsappDisconnect().subscribe({
      next: (conn) => {
        this.acting = false;
        this.wasConnected = false;
        this.applyConnection(conn, false);
        this.stopPolling();
        this.resetMetrics();
      },
      error: (err) => {
        this.acting = false;
        this.error = err?.error?.message || err?.error || 'Falha ao desconectar.';
      }
    });
  }

  private applyConnection(conn: WhatsAppConnection, endLoader: boolean): void {
    const becameConnected = conn.connected && !this.wasConnected;
    this.connection = { ...conn };
    if (endLoader) {
      this.loading = false;
    }
    this.lastCheckedAt = new Date();

    if (becameConnected) {
      this.celebrateConnect();
      this.metricsLoadedForConnected = false;
      this.beginPhoneSyncBudget();
    }
    if (!conn.connected) {
      this.phoneSyncAttemptsLeft = 0;
      this.phoneSyncExhausted = false;
    } else if (this.phoneReady) {
      this.phoneSyncAttemptsLeft = 0;
      this.phoneSyncExhausted = false;
    }
    this.wasConnected = conn.connected;

    if (conn.connected) {
      this.ensureMetrics();
    } else {
      this.resetMetrics();
    }
    this.syncPolling();
  }

  private beginPhoneSyncBudget(): void {
    this.phoneSyncExhausted = false;
    this.phoneSyncAttemptsLeft = PHONE_SYNC_MAX_ATTEMPTS;
  }

  private ensureMetrics(): void {
    if (this.metricsLoadedForConnected || this.metricsLoading) {
      return;
    }
    this.metricsLoading = true;
    this.api.campaigns().subscribe({
      next: (campaigns) => {
        this.metricsLoading = false;
        this.metricsLoadedForConnected = true;
        const startOfDay = new Date();
        startOfDay.setHours(0, 0, 0, 0);
        const wa = campaigns.filter((c) => (c.channel || '').toUpperCase() === 'WHATSAPP');
        this.messagesSentToday = wa
          .filter((c) => {
            if (!c.createdAt) {
              return false;
            }
            return new Date(c.createdAt).getTime() >= startOfDay.getTime();
          })
          .reduce((sum, c) => sum + (c.sentCount || 0), 0);

        const sending = wa.some((c) => {
          const status = (c.status || '').toUpperCase();
          return status === 'RUNNING' || status === 'SENDING' || status === 'IN_PROGRESS';
        });
        this.applyConnectionDerivedMetrics(sending);
        // Taxa ativa reflete saúde da sessão (online = 100%).
        this.activeRateLabel = this.phase === 'connected' ? '100%' : '0%';
      },
      error: () => {
        this.metricsLoading = false;
        this.metricsLoadedForConnected = true;
        this.messagesSentToday = 0;
        this.applyConnectionDerivedMetrics();
        this.activeRateLabel = this.phase === 'connected' ? '100%' : '0%';
      }
    });
  }

  private applyConnectionDerivedMetrics(sending = false): void {
    if (this.phase !== 'connected') {
      this.queueStatus = 'Pausada';
      this.queueTone = 'off';
      this.activeRateLabel = '0%';
      return;
    }
    if (sending) {
      this.queueStatus = 'Em envio';
      this.queueTone = 'busy';
    } else {
      this.queueStatus = 'Pronta';
      this.queueTone = 'ok';
    }
    if (!this.activeRateLabel || this.activeRateLabel === '—') {
      this.activeRateLabel = '100%';
    }
  }

  private resetMetrics(): void {
    this.metricsLoadedForConnected = false;
    this.metricsLoading = false;
    this.messagesSentToday = 0;
    this.queueStatus = 'Pausada';
    this.queueTone = 'off';
    this.activeRateLabel = '0%';
  }

  private formatPhone(phone: string): string {
    const d = phone.replace(/\D/g, '');
    if (d.length >= 13 && d.startsWith('55')) {
      return `+${d.slice(0, 2)} ${d.slice(2, 4)} ${d.slice(4, 9)}-${d.slice(9)}`;
    }
    if (d.length >= 12 && d.startsWith('55')) {
      return `+${d.slice(0, 2)} ${d.slice(2, 4)} ${d.slice(4, 8)}-${d.slice(8)}`;
    }
    return phone.startsWith('+') ? phone : `+${phone}`;
  }

  private celebrateConnect(): void {
    this.justConnected = true;
    if (this.celebrateTimer) {
      clearTimeout(this.celebrateTimer);
    }
    this.celebrateTimer = setTimeout(() => {
      this.justConnected = false;
    }, 3200);
  }

  private syncPolling(): void {
    const needsQrPoll = this.phase === 'qr';
    const needsPhonePoll = this.phase === 'connected' && !this.phoneReady && this.phoneSyncAttemptsLeft > 0;
    if (needsQrPoll || needsPhonePoll) {
      this.startPolling(needsQrPoll);
    } else {
      if (this.phase === 'connected' && !this.phoneReady && this.phoneSyncAttemptsLeft <= 0) {
        this.phoneSyncExhausted = true;
      }
      this.stopPolling();
    }
  }

  private startPolling(forQr: boolean): void {
    this.pollingLive = forQr;
    if (this.pollTimer) {
      return;
    }
    this.pollTimer = setInterval(() => {
      const forPhoneOnly = this.phase === 'connected' && !this.phoneReady;
      if (forPhoneOnly) {
        if (this.phoneSyncAttemptsLeft <= 0) {
          this.phoneSyncExhausted = true;
          this.stopPolling();
          return;
        }
        this.phoneSyncAttemptsLeft -= 1;
      }
      this.api.whatsappStatus().subscribe({
        next: (conn) => this.applyConnection(conn, false),
        error: () => {
          /* mantém polling; falha pontual não derruba a tela */
        }
      });
    }, POLL_MS);
  }

  private stopPolling(): void {
    this.pollingLive = false;
    if (this.pollTimer) {
      clearInterval(this.pollTimer);
      this.pollTimer = null;
    }
  }
}
