import { Component, OnDestroy, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService, Professional, WhatsAppConnection } from '../../core/api.service';
import { BrandMarkComponent } from '../../shared/brand-mark/brand-mark.component';

@Component({
  selector: 'app-professionals', standalone: true, imports: [FormsModule, RouterLink, BrandMarkComponent],
  templateUrl: './professionals.component.html', styleUrl: './professionals.component.scss'
})
export class ProfessionalsComponent implements OnDestroy {
  private readonly api = inject(ApiService);
  query = '';
  locationMode: 'device' | 'service' = 'device';
  locationText = '';
  locationLabel = '';
  radiusKm = 10;
  latitude?: number;
  longitude?: number;
  loading = false;
  searched = false;
  error = '';
  results: Professional[] = [];
  readonly fromResolvaJato = sessionStorage.getItem('mira-origin') === 'resolva-jato';
  contactModal?: Professional;
  greeting = '';
  whatsapp?: WhatsAppConnection;
  checkingConnection = false;
  sendingMessage = false;
  contactFeedback = '';
  private connectionTimer?: ReturnType<typeof setInterval>;

  useDeviceLocation(): void {
    this.locationMode = 'device'; this.error = '';
    if (!navigator.geolocation) { this.error = 'Seu navegador não oferece localização. Use o local do serviço.'; return; }
    this.loading = true;
    navigator.geolocation.getCurrentPosition(
      ({ coords }) => { this.latitude = coords.latitude; this.longitude = coords.longitude; this.locationLabel = 'Sua localização atual'; this.loading = false; },
      () => { this.loading = false; this.locationMode = 'service'; this.error = 'Não foi possível acessar sua localização. Informe o bairro ou CEP do serviço.'; },
      { enableHighAccuracy: true, timeout: 9000, maximumAge: 300000 }
    );
  }

  search(): void {
    this.error = '';
    if (this.query.trim().length < 2) { this.error = 'Informe qual profissional ou serviço você procura.'; return; }
    if (this.locationMode === 'service') {
      if (this.locationText.trim().length < 3) { this.error = 'Informe o bairro e a cidade ou o CEP do serviço.'; return; }
      this.loading = true;
      this.api.resolveProfessionalSearchLocation(this.locationText).subscribe({
        next: (point) => { this.latitude = point.latitude; this.longitude = point.longitude; this.locationLabel = point.label; this.loadResults(); },
        error: () => { this.loading = false; this.error = 'Local não encontrado. Tente “bairro, cidade - UF” ou um CEP.'; }
      });
      return;
    }
    if (this.latitude === undefined || this.longitude === undefined) { this.useDeviceLocation(); this.error = 'Autorize a localização e toque em buscar novamente.'; return; }
    this.loadResults();
  }

  formatDistance(value: number): string { return value < 1 ? `${Math.round(value * 1000)} m` : `${value.toFixed(1).replace('.', ',')} km`; }
  trackById(_: number, item: Professional): string { return item.id; }

  ngOnDestroy(): void { this.stopConnectionPolling(); }

  openWhatsApp(professional: Professional): void {
    this.contactModal = professional;
    this.greeting = `Olá, ${professional.name}! Encontrei seu perfil no MIRA e estou precisando dos seus serviços de ${professional.occupation}. Podemos conversar?`;
    this.contactFeedback = '';
    this.whatsapp = undefined;
    this.refreshConnection();
  }

  whatsappAction(professional: Professional): void {
    if (this.fromResolvaJato) this.openWhatsApp(professional);
    else this.contact(professional, 'WHATSAPP');
  }

  closeContactModal(): void { this.stopConnectionPolling(); this.contactModal = undefined; }

  refreshConnection(): void {
    this.checkingConnection = true;
    this.api.whatsappStatus().subscribe({
      next: (status) => { this.whatsapp = status; this.checkingConnection = false; if (status.connected) this.stopConnectionPolling(); },
      error: () => { this.checkingConnection = false; this.contactFeedback = 'Não foi possível verificar a conexão.'; }
    });
  }

  connectWhatsApp(): void {
    this.checkingConnection = true; this.contactFeedback = '';
    this.api.whatsappConnect().subscribe({
      next: (status) => { this.whatsapp = status; this.checkingConnection = false; this.startConnectionPolling(); },
      error: () => { this.checkingConnection = false; this.contactFeedback = 'Não foi possível iniciar a conexão.'; }
    });
  }

  sendGreeting(): void {
    const professional = this.contactModal;
    if (!professional || !this.whatsapp?.connected || !this.greeting.trim()) return;
    this.sendingMessage = true; this.contactFeedback = '';
    this.api.claimProfessionalContact(professional.id, 'WHATSAPP').subscribe({
      next: ({ value }) => this.api.sendWhatsAppMessage({ phone: value, message: this.greeting.trim() }).subscribe({
        next: (result) => { this.sendingMessage = false; this.contactFeedback = result.success ? 'Mensagem enviada com sucesso.' : (result.error || 'Não foi possível enviar.'); },
        error: () => { this.sendingMessage = false; this.contactFeedback = 'Falha no envio da mensagem.'; }
      }),
      error: (response) => { this.sendingMessage = false; this.contactFeedback = response?.error?.message || 'Seu contato gratuito já foi utilizado.'; }
    });
  }

  call(professional: Professional): void {
    this.api.claimProfessionalContact(professional.id, 'PHONE').subscribe({
      next: ({ value }) => { window.location.href = `tel:${value.replace(/[^\d+]/g, '')}`; },
      error: (response) => { this.error = response?.error?.message || 'Seu contato gratuito já foi utilizado.'; }
    });
  }

  contact(professional: Professional, channel: 'WHATSAPP' | 'EMAIL'): void {
    this.error = '';
    this.api.claimProfessionalContact(professional.id, channel).subscribe({
      next: ({ value }) => {
        const url = channel === 'WHATSAPP' ? `https://wa.me/${value.replace(/\D/g, '')}` : `mailto:${value}`;
        window.open(url, '_blank', 'noopener');
      },
      error: (response) => { this.error = response?.error?.message || 'Seu contato gratuito do MIRA já foi utilizado.'; }
    });
  }

  private startConnectionPolling(): void {
    this.stopConnectionPolling();
    this.connectionTimer = setInterval(() => this.refreshConnection(), 3000);
  }

  private stopConnectionPolling(): void {
    if (this.connectionTimer) clearInterval(this.connectionTimer);
    this.connectionTimer = undefined;
  }

  private loadResults(): void {
    this.loading = true;
    this.api.searchProfessionals(this.query.trim(), this.latitude!, this.longitude!, this.radiusKm).subscribe({
      next: (items) => { this.results = items; this.searched = true; this.loading = false; },
      error: () => { this.loading = false; this.error = 'Não foi possível concluir a busca agora. Tente novamente.'; }
    });
  }
}
