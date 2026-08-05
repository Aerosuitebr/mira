import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ApiService, FollowUpReviewItem } from '../../core/api.service';

@Component({
  selector: 'app-public-followup-approval',
  standalone: true,
  template: `
    <main class="public-page"><section class="card">
      @if (loading) { <p>Carregando solicitação...</p> }
      @else if (error) { <p>{{ error }}</p> }
      @else if (sent) { <h1>Etapa 2 iniciada</h1><p>A mensagem foi enviada ao contato.</p> }
      @else if (item) {
        <small>Resposta recebida</small><h1>{{ item.companyName }}</h1>
        <p>Contato: {{ item.recipient }}</p><p>{{ item.body }}</p>
        <button type="button" class="cta" (click)="approve()" [disabled]="approving">
          {{ approving ? 'Enviando...' : 'Aprovar envio da etapa 2' }}
        </button>
      }
    </section></main>`,
  styleUrl: './public-proposal.component.scss'
})
export class PublicFollowUpApprovalComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  item: FollowUpReviewItem | null = null;
  loading = true;
  approving = false;
  sent = false;
  error = '';
  private token = '';

  ngOnInit(): void {
    this.token = this.route.snapshot.paramMap.get('token') || '';
    this.api.publicFollowUp(this.token).subscribe({
      next: item => { this.item = item; this.loading = false; },
      error: () => { this.error = 'Link inválido, expirado ou já utilizado.'; this.loading = false; }
    });
  }

  approve(): void {
    this.approving = true;
    this.api.approvePublicFollowUp(this.token).subscribe({
      next: result => { this.approving = false; this.sent = !result.error; this.error = result.error || ''; },
      error: () => { this.approving = false; this.error = 'Não foi possível autorizar o envio.'; }
    });
  }
}
