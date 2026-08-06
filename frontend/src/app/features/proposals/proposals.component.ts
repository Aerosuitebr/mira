import { CurrencyPipe } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { AbstractControl, FormArray, FormBuilder, FormsModule, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { switchMap, of } from 'rxjs';
import {
  ApiService,
  CreateProposalPayload,
  Proposal,
  ProposalRecipientOption
} from '../../core/api.service';
import { formatBrMoney, moneyFromDigits } from '../../core/currency-br.util';
import { ActivatedRoute } from '@angular/router';

function integerQuantityValidator(control: AbstractControl): ValidationErrors | null {
  const raw = control.value;
  if (raw === null || raw === undefined || raw === '') {
    return null;
  }
  const value = Number(raw);
  if (!Number.isFinite(value) || !Number.isInteger(value)) {
    return { integer: true };
  }
  return null;
}

@Component({
  selector: 'app-proposals',
  standalone: true,
  imports: [FormsModule, ReactiveFormsModule, CurrencyPipe],
  templateUrl: './proposals.component.html',
  styleUrl: './proposals.component.scss'
})
export class ProposalsComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);

  proposals: Proposal[] = [];
  recipients: ProposalRecipientOption[] = [];
  filteredRecipients: ProposalRecipientOption[] = [];
  recentDiscoveries: ProposalRecipientOption[] = [];
  selectedRecipient: ProposalRecipientOption | null = null;
  recipientQuery = '';
  showRecipientList = false;

  loading = true;
  saving = false;
  publishing = false;
  formError = '';

  showLinkModal = false;
  publishedLink = '';
  publishedRecipientPhone = '';
  copyFeedback = '';

  discountMode: 'percent' | 'amount' = 'percent';
  discountValue = 0;
  taxPercent = 0;

  readonly form = this.fb.nonNullable.group({
    title: ['', Validators.required],
    paymentTerms: ['Pagamento em até 30 dias após aprovação.'],
    validityDays: [15, [Validators.required, Validators.min(1)]],
    items: this.fb.array([this.createItemGroup()])
  });

  ngOnInit(): void {
    this.load();
    this.loadRecipients();
    this.loadRecentDiscoveries();
    this.consumeClientPrefill();
  }

  get items(): FormArray {
    return this.form.controls.items;
  }

  get subtotalPreview(): number {
    return this.items.controls.reduce((sum, control) => sum + this.lineTotalFromControl(control), 0);
  }

  get discountPreview(): number {
    const subtotal = this.subtotalPreview;
    if (this.discountMode === 'percent') {
      return Math.min(subtotal, (subtotal * Math.max(0, this.discountValue)) / 100);
    }
    return Math.min(subtotal, Math.max(0, this.discountValue));
  }

  get taxPreview(): number {
    const base = Math.max(0, this.subtotalPreview - this.discountPreview);
    return (base * Math.max(0, this.taxPercent)) / 100;
  }

  get totalPreview(): number {
    return Math.max(0, this.subtotalPreview - this.discountPreview + this.taxPreview);
  }

  /** Liberado só com formulário válido e cliente/lead selecionado na lista. */
  get canPublish(): boolean {
    return this.form.valid && !!this.selectedRecipient && !this.saving && !this.publishing;
  }

  get canSaveDraft(): boolean {
    return this.form.valid && !this.saving && !this.publishing;
  }

  load(): void {
    this.loading = true;
    this.api.listProposals().subscribe({
      next: (proposals) => {
        this.proposals = proposals;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
      }
    });
  }

  loadRecipients(search = ''): void {
    this.api.listProposalRecipients(search).subscribe({
      next: (recipients) => {
        this.recipients = this.mergeRecipients(recipients, this.recentDiscoveries);
        this.filterRecipients();
        this.applyRouteRecipient();
      }
    });
  }

  private applyRouteRecipient(): void {
    if (this.selectedRecipient) return;
    const leadId = this.route.snapshot.queryParamMap.get('leadId');
    const company = this.route.snapshot.queryParamMap.get('company') || '';
    const match = this.recipients.find(item => (leadId && item.key === `LEAD:${leadId}`) ||
      (!!company && `${item.label} ${item.subtitle}`.toLocaleLowerCase('pt-BR').includes(company.toLocaleLowerCase('pt-BR'))));
    if (match) {
      this.selectedRecipient = match;
      this.recipientQuery = match.label;
      this.form.patchValue({ title: `Proposta comercial · ${match.label}` });
    }
  }

  loadRecentDiscoveries(): void {
    let ids: string[] = [];
    try {
      const raw = sessionStorage.getItem('selected-companies');
      ids = raw ? (JSON.parse(raw) as string[]) : [];
    } catch {
      ids = [];
    }
    if (ids.length === 0) {
      return;
    }
    this.api.getCompaniesByIds(ids).subscribe({
      next: (companies) => {
        this.recentDiscoveries = companies.map((company) => ({
          key: `COMPANY:${company.id}`,
          label: company.tradeName || company.legalName,
          subtitle: `Descoberta recente · ${company.city}/${company.state}`,
          phone: company.phone,
          source: 'Descoberta'
        }));
        this.recipients = this.mergeRecipients(this.recipients, this.recentDiscoveries);
        this.filterRecipients();
        this.applyRouteRecipient();
      }
    });
  }

  onRecipientFocus(): void {
    this.showRecipientList = true;
    this.filterRecipients();
  }

  onRecipientBlur(): void {
    setTimeout(() => {
      this.showRecipientList = false;
    }, 150);
  }

  onRecipientInput(): void {
    this.selectedRecipient = null;
    this.showRecipientList = true;
    this.filterRecipients();
    const term = this.recipientQuery.trim();
    if (term.length >= 2) {
      this.loadRecipients(term);
    } else {
      this.loadRecipients('');
    }
  }

  selectRecipient(recipient: ProposalRecipientOption): void {
    this.selectedRecipient = recipient;
    this.recipientQuery = recipient.label;
    this.showRecipientList = false;
  }

  private consumeClientPrefill(): void {
    const raw = sessionStorage.getItem('mira-proposal-client-prefill');
    if (!raw) {
      return;
    }
    sessionStorage.removeItem('mira-proposal-client-prefill');
    try {
      const data = JSON.parse(raw) as {
        clientId?: string;
        label?: string;
        subtitle?: string;
        phone?: string;
      };
      if (!data.clientId || !data.label) {
        return;
      }
      this.selectRecipient({
        key: `CLIENT:${data.clientId}`,
        label: data.label,
        subtitle: data.subtitle || 'Carteira',
        phone: data.phone,
        source: 'CLIENT'
      });
    } catch {
      // ignore
    }
  }

  filterRecipients(): void {
    const term = this.recipientQuery.trim().toLowerCase();
    this.filteredRecipients = this.recipients.filter(
      (recipient) =>
        !term ||
        recipient.label.toLowerCase().includes(term) ||
        recipient.subtitle.toLowerCase().includes(term)
    );
  }

  addItem(): void {
    this.items.push(this.createItemGroup());
  }

  removeItem(index: number): void {
    if (this.items.length === 1) {
      return;
    }
    this.items.removeAt(index);
  }

  lineTotal(index: number): number {
    const control = this.items.at(index);
    return control ? this.lineTotalFromControl(control) : 0;
  }

  unitPriceDisplay(index: number): string {
    const value = Number(this.items.at(index)?.get('unitPrice')?.value ?? 0);
    return formatBrMoney(value);
  }

  onUnitPriceInput(index: number, event: Event): void {
    const input = event.target as HTMLInputElement;
    const amount = moneyFromDigits(input.value);
    const control = this.items.at(index)?.get('unitPrice');
    control?.setValue(amount);
    control?.markAsDirty();
    control?.markAsTouched();
    input.value = formatBrMoney(amount);
  }

  onUnitPriceBlur(index: number): void {
    const control = this.items.at(index)?.get('unitPrice');
    control?.markAsTouched();
    const amount = Number(control?.value ?? 0);
    if (amount > 0 && amount < 0.01) {
      control?.setValue(0.01);
    }
  }

  get discountAmountDisplay(): string {
    return formatBrMoney(this.discountValue);
  }

  onDiscountAmountInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.discountValue = moneyFromDigits(input.value);
    input.value = formatBrMoney(this.discountValue);
  }

  saveDraft(): void {
    this.persistProposal(false);
  }

  saveAndPublish(): void {
    if (!this.selectedRecipient) {
      this.formError = 'Selecione o cliente ou lead de destino antes de gerar o link.';
      return;
    }
    this.persistProposal(true);
  }

  publish(proposal: Proposal): void {
    this.api.publishProposal(proposal.id).subscribe({
      next: (updated) => {
        this.openLinkModal(updated.approvalUrl);
        this.load();
      }
    });
  }

  async copyLink(url: string): Promise<void> {
    try {
      await navigator.clipboard.writeText(url);
      this.copyFeedback = 'Link copiado!';
      setTimeout(() => (this.copyFeedback = ''), 2000);
    } catch {
      this.copyFeedback = 'Não foi possível copiar.';
    }
  }

  openWhatsApp(): void {
    const phone = this.normalizePhone(this.publishedRecipientPhone);
    const message = encodeURIComponent(`Olá! Segue o link para aprovar nossa proposta: ${this.publishedLink}`);
    const url = phone ? `https://wa.me/${phone}?text=${message}` : `https://wa.me/?text=${message}`;
    window.open(url, '_blank', 'noopener');
  }

  closeLinkModal(): void {
    this.showLinkModal = false;
    this.publishedLink = '';
    this.publishedRecipientPhone = '';
  }

  statusLabel(status: string): string {
    const map: Record<string, string> = {
      DRAFT: 'Rascunho',
      PENDING: 'Aguardando cliente',
      APPROVED: 'Aprovada',
      REJECTED: 'Recusada',
      EXPIRED: 'Expirada'
    };
    return map[status] ?? status;
  }

  private persistProposal(publishAfterSave: boolean): void {
    this.formError = '';
    if (this.form.invalid || this.saving || this.publishing) {
      this.form.markAllAsTouched();
      return;
    }

    if (publishAfterSave) {
      this.publishing = true;
    } else {
      this.saving = true;
    }

    const payload = this.buildPayload();
    const recipientPhone = this.selectedRecipient?.phone ?? '';

    this.api.createProposal(payload).pipe(
      switchMap((created) => (publishAfterSave ? this.api.publishProposal(created.id) : of(created)))
    ).subscribe({
      next: (result) => {
        if (publishAfterSave && 'approvalUrl' in result) {
          this.openLinkModal(result.approvalUrl, recipientPhone);
        }
        this.resetForm();
        this.saving = false;
        this.publishing = false;
        this.load();
      },
      error: (err) => {
        this.saving = false;
        this.publishing = false;
        this.formError = err?.error?.message || err?.error || 'Não foi possível salvar a proposta.';
      }
    });
  }

  private openLinkModal(url: string, phone = ''): void {
    this.publishedLink = url;
    this.publishedRecipientPhone = phone;
    this.showLinkModal = true;
  }

  private resetForm(): void {
    this.form.reset({
      title: '',
      paymentTerms: 'Pagamento em até 30 dias após aprovação.',
      validityDays: 15
    });
    this.items.clear();
    this.items.push(this.createItemGroup());
    this.selectedRecipient = null;
    this.recipientQuery = '';
    this.discountMode = 'percent';
    this.discountValue = 0;
    this.taxPercent = 0;
  }

  private createItemGroup() {
    return this.fb.nonNullable.group({
      description: ['', Validators.required],
      quantity: [1, [Validators.required, Validators.min(1), integerQuantityValidator]],
      unitPrice: [0, [Validators.required, Validators.min(0.01)]]
    });
  }

  private buildPayload(): CreateProposalPayload {
    const raw = this.form.getRawValue();
    const commercialNotes = this.buildCommercialNotes();
    const paymentTerms = [raw.paymentTerms.trim(), commercialNotes].filter(Boolean).join('\n');

    const payload: CreateProposalPayload = {
      title: raw.title,
      paymentTerms,
      validityDays: raw.validityDays,
      items: raw.items.map((item) => ({
        description: item.description.trim(),
        quantity: Math.max(1, Math.round(Number(item.quantity) || 1)),
        unitPrice: Number(item.unitPrice)
      }))
    };

    if (this.selectedRecipient?.key.startsWith('LEAD:')) {
      payload.leadId = this.selectedRecipient.key.replace('LEAD:', '');
    } else if (this.selectedRecipient?.key.startsWith('CLIENT:')) {
      payload.clientId = this.selectedRecipient.key.replace('CLIENT:', '');
    } else if (this.selectedRecipient?.key.startsWith('COMPANY:')) {
      payload.companyId = this.selectedRecipient.key.replace('COMPANY:', '');
    }

    return payload;
  }

  private mergeRecipients(
    primary: ProposalRecipientOption[],
    extras: ProposalRecipientOption[]
  ): ProposalRecipientOption[] {
    const byKey = new Map<string, ProposalRecipientOption>();
    for (const item of [...extras, ...primary]) {
      byKey.set(item.key, item);
    }
    return [...byKey.values()];
  }

  private buildCommercialNotes(): string {
    const notes: string[] = [];
    if (this.discountPreview > 0) {
      notes.push(
        this.discountMode === 'percent'
          ? `Desconto aplicado: ${this.discountValue}% (${this.discountPreview.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })})`
          : `Desconto aplicado: ${this.discountPreview.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })}`
      );
    }
    if (this.taxPercent > 0) {
      notes.push(
        `Impostos / encargos: ${this.taxPercent}% (${this.taxPreview.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })})`
      );
    }
    if (notes.length > 0) {
      notes.push(
        `Total estimado com condições: ${this.totalPreview.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })}`
      );
    }
    return notes.join('\n');
  }

  private lineTotalFromControl(control: { get: (name: string) => { value?: unknown } | null }): number {
    const qty = Math.max(1, Math.round(Number(control.get('quantity')?.value ?? 0)));
    const price = Number(control.get('unitPrice')?.value ?? 0);
    return qty * price;
  }

  private normalizePhone(phone: string): string {
    return phone.replace(/\D/g, '');
  }
}
