import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { forkJoin, from, of } from 'rxjs';
import { catchError, concatMap, finalize, map, mergeMap, tap, toArray } from 'rxjs/operators';
import { ApiService, Company, Contact } from '../../core/api.service';

interface CompanyRowSummary {
  partners: string[];
  phones: string[];
  emails: string[];
  whatsapps: string[];
  address: string;
  avgConfidence: number;
  hasUsefulContact: boolean;
  completenessHint: string;
}

@Component({
  selector: 'app-enrich',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './enrich.component.html',
  styleUrl: './enrich.component.scss'
})
export class EnrichComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  companyIds: string[] = [];
  companies: Company[] = [];
  contacts: Record<string, Contact[]> = {};
  selectedIds = new Set<string>();
  detailCompanyId: string | null = null;
  onlyWithWhatsApp = false;
  copiedKey = '';
  copyToast = '';
  private copyToastTimer: ReturnType<typeof setTimeout> | null = null;
  loading = false;
  loadingWeb = false;
  loadingContacts = false;
  enrichDone = 0;
  enrichTotal = 0;
  enrichingIds = new Set<string>();
  message = '';
  importFeedback = '';
  importFeedbackError = false;

  ngOnInit(): void {
    this.restoreSelection();
    if (this.companyIds.length === 0) {
      return;
    }
    this.loadCompanies(this.companyIds);
  }

  get hasAnyEnriched(): boolean {
    return this.companies.some((company) => !!this.contacts[company.id]);
  }

  get selectedCount(): number {
    return this.selectedIds.size;
  }

  get visibleCompanies(): Company[] {
    if (!this.onlyWithWhatsApp) {
      return this.companies;
    }
    return this.companies.filter((company) => this.summaryFor(company).whatsapps.length > 0);
  }

  get detailCompany(): Company | null {
    if (!this.detailCompanyId) {
      return null;
    }
    return this.companies.find((company) => company.id === this.detailCompanyId) ?? null;
  }

  get detailContacts(): Contact[] {
    if (!this.detailCompanyId) {
      return [];
    }
    return this.contacts[this.detailCompanyId] ?? [];
  }

  get allVisibleSelected(): boolean {
    const visible = this.visibleCompanies;
    return visible.length > 0 && visible.every((company) => this.selectedIds.has(company.id));
  }

  private restoreSelection(): void {
    const cache = sessionStorage.getItem('selected-companies-cache');
    if (cache) {
      try {
        const companies = JSON.parse(cache) as Company[];
        if (Array.isArray(companies) && companies.length > 0) {
          this.companies = companies;
          this.companyIds = companies.map((company) => company.id);
        }
      } catch {
        // ignore invalid cache
      }
    }

    if (this.companyIds.length === 0) {
      const raw = sessionStorage.getItem('selected-companies');
      this.companyIds = raw ? (JSON.parse(raw) as string[]) : [];
    }
  }

  goToDiscover(): void {
    void this.router.navigate(['/discover']);
  }

  goToOutreach(): void {
    if (this.selectedIds.size === 0) {
      this.message = 'Selecione ao menos uma empresa para seguir.';
      return;
    }
    const selected = this.companies.filter((company) => this.selectedIds.has(company.id));
    sessionStorage.setItem('selected-companies', JSON.stringify([...this.selectedIds]));
    sessionStorage.setItem('selected-companies-cache', JSON.stringify(selected));
    void this.router.navigate(['/outreach']);
  }

  importCsv(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) {
      return;
    }

    const reader = new FileReader();
    reader.onload = () => {
      const cnpjs = this.extractCnpjs(String(reader.result ?? ''));
      if (cnpjs.length === 0) {
        this.importFeedback = 'Nenhum CNPJ válido (14 dígitos) encontrado no arquivo.';
        this.importFeedbackError = true;
        return;
      }
      this.loadCompaniesByCnpjs(cnpjs.slice(0, 100));
    };
    reader.readAsText(file);
    input.value = '';
  }

  enrichAll(forceRefresh = false): void {
    const ids = this.selectedIds.size > 0 ? [...this.selectedIds] : this.companyIds;
    if (ids.length === 0) {
      return;
    }
    this.loading = true;
    this.loadingWeb = forceRefresh;
    this.enrichDone = 0;
    this.enrichTotal = ids.length;
    this.enrichingIds = new Set();
    this.message = forceRefresh
      ? `Atualizando na web ${this.enrichTotal} empresa(s)...`
      : `Consultando APIs públicas para ${this.enrichTotal} empresa(s)...`;

    let failures = 0;

    from(ids)
      .pipe(
        mergeMap(
          (id) => {
            this.enrichingIds = new Set([...this.enrichingIds, id]);
            return this.api.enrich([id], forceRefresh).pipe(
              catchError((err: { status?: number }) => {
                failures++;
                if (err.status === 401) {
                  this.message = 'Sua sessão expirou. Faça login novamente.';
                }
                return of([] as Contact[]);
              }),
              tap((list) => {
                this.contacts = { ...this.contacts, [id]: list };
              }),
              finalize(() => {
                this.enrichingIds.delete(id);
                this.enrichingIds = new Set(this.enrichingIds);
                this.enrichDone = Math.min(this.enrichTotal, this.enrichDone + 1);
                this.message = forceRefresh
                  ? `Atualização web ${this.enrichDone}/${this.enrichTotal}...`
                  : `Enriquecendo ${this.enrichDone}/${this.enrichTotal}...`;
              })
            );
          },
          3
        ),
        toArray(),
        concatMap(() =>
          this.api.getCompaniesByIds(this.companyIds).pipe(catchError(() => of(this.companies)))
        )
      )
      .subscribe({
        next: (companies) => {
          this.companies = companies;
          sessionStorage.setItem('selected-companies-cache', JSON.stringify(companies));
          this.loading = false;
          this.loadingWeb = false;
          this.enrichingIds = new Set();
          if (failures === this.enrichTotal) {
            this.message = 'Falha ao enriquecer. Tente novamente em instantes.';
          } else if (failures > 0) {
            this.message = `Enriquecimento concluído com ${failures} falha(s). Revise a tabela e continue com as selecionadas.`;
          } else {
            this.message = forceRefresh
              ? 'Dados atualizados na web. Revise a tabela, ajuste a seleção e siga para abordar.'
              : 'Enriquecimento concluído. Revise os contatos, marque quem segue e avance.';
          }
        },
        error: (err: { status?: number }) => {
          this.loading = false;
          this.loadingWeb = false;
          this.enrichingIds = new Set();
          if (err.status === 401) {
            this.message = 'Sua sessão expirou. Faça login novamente.';
          } else if (err.status === 403) {
            this.message = 'Sem permissão para enriquecer. Tente sair e entrar novamente.';
          } else if (err.status === 0) {
            this.message = 'Backend indisponível ou tempo esgotado. Verifique se o servidor está rodando.';
          } else {
            this.message = 'Falha ao enriquecer. Tente novamente em instantes.';
          }
        }
      });
  }

  toggleSelect(companyId: string): void {
    if (this.selectedIds.has(companyId)) {
      this.selectedIds.delete(companyId);
    } else {
      this.selectedIds.add(companyId);
    }
    this.selectedIds = new Set(this.selectedIds);
    this.persistSelection();
  }

  toggleSelectAllVisible(): void {
    const visible = this.visibleCompanies;
    if (this.allVisibleSelected) {
      visible.forEach((company) => this.selectedIds.delete(company.id));
    } else {
      visible.forEach((company) => this.selectedIds.add(company.id));
    }
    this.selectedIds = new Set(this.selectedIds);
    this.persistSelection();
  }

  removeUnselected(): void {
    if (this.selectedIds.size === 0) {
      this.message = 'Marque as empresas que deseja manter antes de descartar as demais.';
      return;
    }
    this.companies = this.companies.filter((company) => this.selectedIds.has(company.id));
    this.companyIds = this.companies.map((company) => company.id);
    const nextContacts: Record<string, Contact[]> = {};
    for (const id of this.companyIds) {
      if (this.contacts[id]) {
        nextContacts[id] = this.contacts[id];
      }
    }
    this.contacts = nextContacts;
    this.persistSelection();
    this.message = `Lista reduzida para ${this.companies.length} empresa(s).`;
  }

  openDetails(companyId: string): void {
    this.detailCompanyId = companyId;
  }

  closeDetails(): void {
    this.detailCompanyId = null;
  }

  rowStatus(companyId: string): 'done' | 'running' | 'queued' | 'idle' {
    if (this.contacts[companyId]) {
      return 'done';
    }
    if (!this.loading) {
      return 'idle';
    }
    return this.enrichingIds.has(companyId) ? 'running' : 'queued';
  }

  summaryFor(company: Company): CompanyRowSummary {
    const list = this.contacts[company.id] ?? [];
    const partners = list
      .filter((contact) => contact.source === 'PUBLIC_REGISTRY')
      .map((contact) => this.partnerName(contact))
      .filter(Boolean)
      .slice(0, 2);

    const phones = [
      ...list.map((contact) => contact.phone).filter(Boolean),
      ...list.map((contact) => contact.whatsapp).filter(Boolean)
    ]
      .map((value) => value.replace(/\D/g, ''))
      .filter((value, index, all) => value.length >= 10 && all.indexOf(value) === index)
      .slice(0, 2);

    const emails = list
      .map((contact) => contact.email)
      .filter(Boolean)
      .filter((value, index, all) => all.indexOf(value) === index)
      .slice(0, 2);

    const whatsapps = list
      .map((contact) => contact.whatsapp)
      .filter(Boolean)
      .map((value) => value.replace(/\D/g, ''))
      .filter((value, index, all) => value.length >= 10 && all.indexOf(value) === index)
      .slice(0, 2);

    const addressFromContact = list.find(
      (contact) =>
        (contact.source === 'RECEITA_FEDERAL' || contact.source === 'WEBSITE_CRAWL') &&
        contact.roleTitle &&
        /cep|rua|av|estrada|bairro/i.test(contact.roleTitle)
    )?.roleTitle;

    const address = addressFromContact || this.formatAddress(company) || `${company.city}/${company.state}`;
    const avgConfidence =
      list.length === 0
        ? 0
        : Math.round(list.reduce((sum, contact) => sum + (contact.confidence || 0), 0) / list.length);

    const missing: string[] = [];
    if (partners.length === 0) missing.push('sócios');
    if (phones.length === 0 && whatsapps.length === 0) missing.push('telefone');
    if (emails.length === 0) missing.push('e-mail');
    if (whatsapps.length === 0) missing.push('WhatsApp');
    if (!address || address === `${company.city}/${company.state}`) missing.push('endereço completo');
    const hasSocial = list.some(
      (contact) => contact.source === 'SOCIAL_LINKEDIN' || contact.source === 'SOCIAL_INSTAGRAM' || !!contact.websiteUrl
    );
    if (!hasSocial) missing.push('site/redes');

    const completenessHint =
      list.length === 0
        ? 'Ainda sem enriquecimento. Clique em Enriquecer selecionadas.'
        : missing.length === 0
          ? 'Cadastro bem preenchido para abordagem.'
          : `Faltam: ${missing.join(', ')}.`;

    return {
      partners,
      phones,
      emails,
      whatsapps,
      address,
      avgConfidence,
      hasUsefulContact: phones.length > 0 || emails.length > 0 || whatsapps.length > 0,
      completenessHint
    };
  }

  partnerName(contact: Contact): string {
    const role = contact.roleTitle || '';
    const beforeDash = role.split(' - ')[0]?.trim();
    if (beforeDash && !/^sócio/i.test(beforeDash)) {
      return beforeDash;
    }
    return contact.fullName?.replace(/sócio.*?público\)?/i, '').trim() || role;
  }

  async copyText(value: string, key: string): Promise<void> {
    if (!value) {
      return;
    }
    try {
      await navigator.clipboard.writeText(value);
      this.copiedKey = key;
      this.copyToast = 'Copiado!';
      if (this.copyToastTimer) {
        clearTimeout(this.copyToastTimer);
      }
      this.copyToastTimer = setTimeout(() => {
        if (this.copiedKey === key) {
          this.copiedKey = '';
        }
        this.copyToast = '';
        this.copyToastTimer = null;
      }, 1500);
    } catch {
      this.message = 'Não foi possível copiar. Tente selecionar o texto manualmente.';
    }
  }

  whatsappHref(digits: string): string {
    const normalized = digits.replace(/\D/g, '');
    const withCountry = normalized.startsWith('55') ? normalized : `55${normalized}`;
    return `https://wa.me/${withCountry}`;
  }

  sourceLabel(source: string): string {
    switch (source) {
      case 'WEBSITE_PROFILE':
      case 'WEBSITE_CRAWL':
        return 'Site';
      case 'SOCIAL_LINKEDIN':
        return 'LinkedIn';
      case 'SOCIAL_INSTAGRAM':
        return 'Instagram';
      case 'RECEITA_FEDERAL':
        return 'Receita Federal';
      case 'PUBLIC_REGISTRY':
        return 'Cadastro público';
      default:
        return source;
    }
  }

  formatPhone(digits: string): string {
    const d = digits.replace(/\D/g, '');
    if (d.length === 11) {
      return `(${d.slice(0, 2)}) ${d.slice(2, 7)}-${d.slice(7)}`;
    }
    if (d.length === 10) {
      return `(${d.slice(0, 2)}) ${d.slice(2, 6)}-${d.slice(6)}`;
    }
    return digits;
  }

  formatCnpj(value: string): string {
    const d = value.replace(/\D/g, '');
    if (d.length !== 14) {
      return value;
    }
    return `${d.slice(0, 2)}.${d.slice(2, 5)}.${d.slice(5, 8)}/${d.slice(8, 12)}-${d.slice(12)}`;
  }

  formatAddress(company: Company): string {
    const parts = [company.street, company.neighborhood, `${company.city}/${company.state}`];
    const line = parts.filter(Boolean).join(' · ');
    if (company.zipCode) {
      const zip = company.zipCode.replace(/\D/g, '');
      const formattedZip = zip.length === 8 ? `${zip.slice(0, 5)}-${zip.slice(5)}` : company.zipCode;
      return `${line} · CEP ${formattedZip}`;
    }
    return line;
  }

  confidenceLevel(score: number): string {
    if (score >= 80) return 'high';
    if (score >= 50) return 'mid';
    return 'low';
  }

  private persistSelection(): void {
    const selected = this.companies.filter((company) => this.selectedIds.has(company.id));
    const ids = selected.length > 0 ? selected.map((company) => company.id) : this.companyIds;
    sessionStorage.setItem('selected-companies', JSON.stringify(ids));
    sessionStorage.setItem(
      'selected-companies-cache',
      JSON.stringify(selected.length > 0 ? selected : this.companies)
    );
  }

  private loadCompanies(ids: string[]): void {
    this.loading = true;
    this.api.getCompaniesByIds(ids).subscribe({
      next: (companies) => {
        this.companies = companies;
        this.companyIds = companies.map((company) => company.id);
        this.selectedIds = new Set(this.companyIds);
        sessionStorage.setItem('selected-companies', JSON.stringify(this.companyIds));
        sessionStorage.setItem('selected-companies-cache', JSON.stringify(this.companies));
        this.loading = false;
        this.loadExistingContacts(this.companyIds);
      },
      error: () => {
        this.loading = false;
      }
    });
  }

  private loadExistingContacts(ids: string[]): void {
    if (ids.length === 0) {
      return;
    }
    this.loadingContacts = true;
    forkJoin(
      ids.map((id) =>
        this.api.listContacts(id).pipe(
          map((list) => ({ id, list })),
          catchError(() => of({ id, list: [] as Contact[] }))
        )
      )
    ).subscribe({
      next: (rows) => {
        const next: Record<string, Contact[]> = { ...this.contacts };
        for (const row of rows) {
          if (row.list.length > 0) {
            next[row.id] = row.list;
          }
        }
        this.contacts = next;
        this.loadingContacts = false;
        if (Object.keys(next).length > 0) {
          this.message = 'Dados já enriquecidos carregados. Revise a tabela ou atualize na web se precisar.';
        }
      },
      error: () => {
        this.loadingContacts = false;
      }
    });
  }

  private loadCompaniesByCnpjs(cnpjs: string[]): void {
    this.loading = true;
    this.importFeedback = '';
    this.importFeedbackError = false;

    forkJoin(
      cnpjs.map((cnpj) =>
        this.api.searchCompanies({ keyword: cnpj, page: 0, size: 10 }).pipe(
          map((page) => page.content.find((c) => this.normalizeCnpj(c.cnpj) === cnpj) ?? null),
          catchError(() => of(null))
        )
      )
    ).subscribe({
      next: (matches) => {
        const found = matches.filter((c): c is Company => c !== null);
        this.companies = found;
        this.companyIds = found.map((c) => c.id);
        this.selectedIds = new Set(this.companyIds);
        sessionStorage.setItem('selected-companies', JSON.stringify(this.companyIds));
        sessionStorage.setItem('selected-companies-cache', JSON.stringify(this.companies));
        this.loading = false;
        this.loadExistingContacts(this.companyIds);

        if (found.length === 0) {
          this.importFeedback = 'Nenhuma empresa correspondente encontrada na base para os CNPJs informados.';
          this.importFeedbackError = true;
        } else if (found.length < cnpjs.length) {
          this.importFeedback = `${found.length} de ${cnpjs.length} CNPJs encontrados na base.`;
          this.importFeedbackError = false;
        } else {
          this.importFeedback = `${found.length} empresas importadas com sucesso.`;
          this.importFeedbackError = false;
        }
      },
      error: () => {
        this.loading = false;
        this.importFeedback = 'Falha ao processar o arquivo. Tente novamente.';
        this.importFeedbackError = true;
      }
    });
  }

  private extractCnpjs(text: string): string[] {
    const results = new Set<string>();
    for (const line of text.split(/\r?\n/)) {
      for (const cell of line.split(/[;,]/)) {
        const digits = cell.replace(/\D/g, '');
        if (digits.length === 14) {
          results.add(digits);
        }
      }
    }
    if (results.size === 0) {
      const global = text.match(/(?<!\d)\d{14}(?!\d)/g);
      global?.forEach((cnpj) => results.add(cnpj));
    }
    return [...results];
  }

  private normalizeCnpj(value: string): string {
    return value.replace(/\D/g, '');
  }
}
