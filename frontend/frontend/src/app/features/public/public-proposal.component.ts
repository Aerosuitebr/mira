import { CurrencyPipe } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ApiService, PublicProposal } from '../../core/api.service';

import { BrandMarkComponent } from '../../shared/brand-mark/brand-mark.component';

@Component({
  selector: 'app-public-proposal',
  standalone: true,
  imports: [FormsModule, CurrencyPipe, BrandMarkComponent],
  templateUrl: './public-proposal.component.html',
  styleUrl: './public-proposal.component.scss'
})
export class PublicProposalComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);

  proposal?: PublicProposal;
  loading = true;
  approved = false;
  signerName = '';
  signerDocument = '';
  error = '';

  ngOnInit(): void {
    const token = this.route.snapshot.paramMap.get('token');
    if (!token) return;
    this.api.getPublicProposal(token).subscribe({
      next: (proposal) => {
        this.proposal = proposal;
        this.loading = false;
      },
      error: () => {
        this.error = 'Proposta não encontrada ou expirada.';
        this.loading = false;
      }
    });
  }

  approve(): void {
    const token = this.route.snapshot.paramMap.get('token');
    if (!token || !this.signerName || !this.signerDocument) return;
    this.api.approvePublicProposal(token, {
      signerName: this.signerName,
      signerDocument: this.signerDocument
    }).subscribe({
      next: () => {
        this.approved = true;
      },
      error: () => {
        this.error = 'Não foi possível aprovar a proposta.';
      }
    });
  }
}
