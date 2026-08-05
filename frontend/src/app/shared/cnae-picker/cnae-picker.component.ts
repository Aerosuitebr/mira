import {
  ChangeDetectorRef,
  Component,
  ElementRef,
  HostListener,
  OnDestroy,
  forwardRef,
  inject
} from '@angular/core';
import { NgClass, NgStyle } from '@angular/common';
import { ControlValueAccessor, FormsModule, NG_VALUE_ACCESSOR } from '@angular/forms';
import { ApiService, CnaeActivityOption, CnaeSectionCatalog } from '../../core/api.service';

interface FallbackCnaeSection {
  code: string;
  title: string;
  searchHint: string;
  divisions: { code: string; label: string }[];
}

interface CnaeQuickShortcut {
  id: string;
  label: string;
  hint: string;
  filterValue: string;
  searchTerms: string[];
  tone: string;
}

const FALLBACK_CNAE_SECTIONS: FallbackCnaeSection[] = [
  {
    code: 'G',
    title: 'Comércio e reparação',
    searchHint: 'Varejo, atacado, distribuição, lojas locais e reparação de veículos.',
    divisions: [
      { code: '45', label: 'Comércio e reparação de veículos' },
      { code: '46', label: 'Comércio por atacado' },
      { code: '47', label: 'Comércio varejista' }
    ]
  },
  {
    code: 'I',
    title: 'Alojamento e alimentação',
    searchHint: 'Hotéis, pousadas, restaurantes, bares, cafeterias e food service.',
    divisions: [
      { code: '55', label: 'Alojamento' },
      { code: '56', label: 'Alimentação' }
    ]
  },
  {
    code: 'J',
    title: 'Informação e comunicação',
    searchHint: 'Software, tecnologia, telecom, mídia, produção digital e serviços online.',
    divisions: [
      { code: '61', label: 'Telecomunicações' },
      { code: '62', label: 'Tecnologia da informação' },
      { code: '63', label: 'Serviços de informação' }
    ]
  },
  {
    code: 'M',
    title: 'Atividades profissionais e técnicas',
    searchHint: 'Consultorias, escritórios, marketing, engenharia, arquitetura e serviços especializados.',
    divisions: [
      { code: '69', label: 'Atividades jurídicas e contábeis' },
      { code: '70', label: 'Consultoria em gestão empresarial' },
      { code: '71', label: 'Arquitetura e engenharia' },
      { code: '73', label: 'Publicidade e pesquisa de mercado' }
    ]
  },
  {
    code: 'N',
    title: 'Atividades administrativas',
    searchHint: 'Locação, limpeza, segurança, call centers, agências e apoio operacional.',
    divisions: [
      { code: '77', label: 'Aluguéis não imobiliários' },
      { code: '78', label: 'Seleção e agenciamento de mão de obra' },
      { code: '80', label: 'Segurança e investigação' },
      { code: '82', label: 'Serviços de escritório e apoio administrativo' }
    ]
  },
  {
    code: 'Q',
    title: 'Saúde humana e serviços sociais',
    searchHint: 'Clínicas, consultórios, laboratórios, odontologia, estética e serviços de cuidado.',
    divisions: [
      { code: '86', label: 'Saúde humana' },
      { code: '87', label: 'Atenção residencial à saúde' },
      { code: '88', label: 'Serviços sociais sem alojamento' }
    ]
  }
];

const CNAE_QUICK_SHORTCUTS: CnaeQuickShortcut[] = [
  {
    id: 'mro-aeronaves',
    label: 'MRO · Manutenção de aeronaves',
    hint: 'CNAE 33163 - hangar e pista (oficinas Part 145)',
    filterValue: '33163',
    searchTerms: ['33163', '3316301', '3316302', 'aeronave', 'aeronaves', 'mro', 'aviação', 'aviacao', 'hangar'],
    tone: 'cyan'
  },
  {
    id: 'transporte-aereo',
    label: 'Operadores aéreos',
    hint: 'CNAE 51 - companhias e táxi aéreo (não é oficina MRO)',
    filterValue: '51',
    searchTerms: ['transporte aéreo', 'transporte aereo', 'taxi aereo'],
    tone: 'amber'
  }
];

@Component({
  selector: 'app-cnae-picker',
  standalone: true,
  imports: [FormsModule, NgStyle, NgClass],
  templateUrl: './cnae-picker.component.html',
  styleUrl: './cnae-picker.component.scss',
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => CnaePickerComponent),
      multi: true
    }
  ]
})
export class CnaePickerComponent implements ControlValueAccessor, OnDestroy {
  private readonly host = inject(ElementRef<HTMLElement>);
  private readonly api = inject(ApiService);
  private readonly cdr = inject(ChangeDetectorRef);
  private searchTimer?: ReturnType<typeof setTimeout>;

  readonly quickShortcuts = CNAE_QUICK_SHORTCUTS;

  get visibleQuickShortcuts(): CnaeQuickShortcut[] {
    const query = this.searchQuery.trim().toLowerCase();
    if (!query) {
      return this.quickShortcuts;
    }

    return this.quickShortcuts.filter(
      (shortcut) =>
        shortcut.label.toLowerCase().includes(query) ||
        shortcut.hint.toLowerCase().includes(query) ||
        shortcut.filterValue.includes(query) ||
        shortcut.searchTerms.some((term) => term.includes(query) || query.includes(term))
    );
  }

  applyShortcut(shortcut: CnaeQuickShortcut, event: Event): void {
    event.preventDefault();
    event.stopPropagation();

    const activity: CnaeActivityOption = {
      code: shortcut.filterValue,
      label: `${shortcut.filterValue} - ${shortcut.label}`,
      filterValue: shortcut.filterValue,
      kind: 'DIVISION'
    };
    this.select(shortcut.filterValue, activity);
  }

  catalog: CnaeSectionCatalog[] = [];
  searchResults: CnaeActivityOption[] = [];
  filteredCatalog: CnaeSectionCatalog[] = [];
  loading = false;
  searching = false;
  loaded = false;
  errorMessage = '';
  warningMessage = '';
  panelStyle: Record<string, string> = {};

  expandedSections = new Set<string>();
  expandedDivisions = new Set<string>();
  loadingDivisions = new Set<string>();
  subclassesByDivision = new Map<string, CnaeActivityOption[]>();
  private readonly labelCache = new Map<string, CnaeActivityOption>();

  value = '';
  open = false;
  searchQuery = '';
  disabled = false;

  private onChange: (value: string) => void = () => undefined;
  private onTouched: () => void = () => undefined;

  get selectedLabel(): string {
    return this.resolveLabel(this.value);
  }

  get selectedSectionCode(): string | null {
    if (!this.value) {
      return null;
    }

    for (const section of this.catalog) {
      if (section.activities?.some((item) => item.filterValue === this.value)) {
        return section.sectionCode;
      }
      if (this.value.includes(',') && section.sectionFilterValue === this.value) {
        return section.sectionCode;
      }
    }

    const prefix = this.numericPrefix(this.value);
    if (prefix) {
      const section = this.findSectionByDivisionPrefix(prefix.slice(0, 2));
      return section?.sectionCode ?? null;
    }

    return null;
  }

  get hint(): string {
    if (this.errorMessage) {
      return this.errorMessage;
    }

    if (this.warningMessage) {
      return this.warningMessage;
    }

    if (this.loading) {
      return 'Carregando setores CNAE da base Receita Federal…';
    }

    if (!this.value) {
      return 'Expanda um setor na árvore ou busque por código/descrição da subclass.';
    }

    for (const section of this.catalog) {
      if (section.activities?.some((item) => item.filterValue === this.value)) {
        return section.searchHint;
      }
    }

    return 'Subclass CNAE selecionada da base RF.';
  }

  get hasSearchQuery(): boolean {
    return this.searchQuery.trim().length >= 2;
  }

  writeValue(value: string | null): void {
    this.value = value ?? '';
  }

  registerOnChange(fn: (value: string) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
  }

  togglePanel(): void {
    if (this.disabled) {
      return;
    }

    this.open = !this.open;
    if (this.open) {
      this.onTouched();
      this.updatePanelPosition();
      this.ensureCatalogLoaded();
    } else {
      this.resetSearch();
    }
  }

  ngOnDestroy(): void {
    if (this.searchTimer) {
      clearTimeout(this.searchTimer);
    }
  }

  closePanel(): void {
    this.open = false;
    this.resetSearch();
  }

  clearSearch(): void {
    this.searchQuery = '';
    this.searchResults = [];
    this.searching = false;
    this.filteredCatalog = this.catalog;
    if (this.searchTimer) {
      clearTimeout(this.searchTimer);
    }
  }

  onSearchInput(): void {
    this.refreshFilteredCatalog();
    this.syncExpansionFromSearch();

    if (this.searchTimer) {
      clearTimeout(this.searchTimer);
    }

    const query = this.searchQuery.trim();
    if (query.length < 2) {
      this.searchResults = [];
      this.searching = false;
      return;
    }

    this.searching = true;
    this.searchTimer = setTimeout(() => {
      this.api.searchCnaeActivities(query, 60).subscribe({
        next: (results) => {
          this.searchResults = results;
          results.forEach((item) => this.rememberActivity(item));
          this.searching = false;
          this.syncExpansionFromSearch();
          this.cdr.markForCheck();
        },
        error: () => {
          this.searchResults = [];
          this.searching = false;
          this.cdr.markForCheck();
        }
      });
    }, 280);
  }

  select(filterValue: string, activity?: CnaeActivityOption): void {
    if (activity) {
      this.rememberActivity(activity);
    }
    this.value = filterValue;
    this.onChange(filterValue);
    this.onTouched();
    this.closePanel();
  }

  toggleSection(sectionCode: string, event: Event): void {
    event.stopPropagation();
    if (this.expandedSections.has(sectionCode)) {
      this.expandedSections.delete(sectionCode);
      return;
    }
    this.expandedSections.add(sectionCode);
  }

  isSectionExpanded(sectionCode: string): boolean {
    return this.expandedSections.has(sectionCode);
  }

  toggleDivision(divisionCode: string, sectionCode: string, event: Event): void {
    event.stopPropagation();
    if (this.expandedDivisions.has(divisionCode)) {
      this.expandedDivisions.delete(divisionCode);
      return;
    }
    this.expandedSections.add(sectionCode);
    this.expandedDivisions.add(divisionCode);
    this.ensureSubclassesLoaded(divisionCode);
  }

  isDivisionExpanded(divisionCode: string): boolean {
    return this.expandedDivisions.has(divisionCode);
  }

  isDivisionLoading(divisionCode: string): boolean {
    return this.loadingDivisions.has(divisionCode);
  }

  subclassesFor(divisionCode: string): CnaeActivityOption[] {
    return this.subclassesByDivision.get(divisionCode) ?? [];
  }

  sectionActivity(section: CnaeSectionCatalog): CnaeActivityOption | undefined {
    return section.activities.find((activity) => activity.kind === 'SECTION');
  }

  divisionActivities(section: CnaeSectionCatalog): CnaeActivityOption[] {
    return section.activities.filter((activity) => activity.kind === 'DIVISION');
  }

  activityTitle(activity: CnaeActivityOption): string {
    const label = activity.label ?? '';
    const separator = label.includes(' - ') ? ' - ' : ' - ';
    const parts = label.split(separator);
    return parts.length > 1 ? parts.slice(1).join(separator) : label;
  }

  isSelected(filterValue: string): boolean {
    return this.value === filterValue;
  }

  sectionTone(sectionCode: string): string {
    const tones = ['violet', 'cyan', 'emerald', 'rose', 'amber'];
    const index = sectionCode.charCodeAt(0) % tones.length;
    return tones[index];
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (!this.open) {
      return;
    }

    if (!this.host.nativeElement.contains(event.target as Node)) {
      this.closePanel();
    }
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.closePanel();
  }

  @HostListener('window:scroll')
  @HostListener('window:resize')
  onViewportChange(): void {
    if (this.open) {
      this.updatePanelPosition();
    }
  }

  private updatePanelPosition(): void {
    const trigger = this.host.nativeElement.querySelector('.cnae-picker__trigger') as HTMLElement | null;
    if (!trigger) {
      return;
    }

    const rect = trigger.getBoundingClientRect();
    const width = Math.min(Math.max(rect.width, 340), 480);
    const maxLeft = Math.max(12, window.innerWidth - width - 12);

    this.panelStyle = {
      top: `${rect.bottom + 8}px`,
      left: `${Math.min(rect.left, maxLeft)}px`,
      width: `${width}px`
    };
  }

  private ensureCatalogLoaded(): void {
    if (this.loaded || this.loading) {
      this.refreshFilteredCatalog();
      this.expandToSelectedValue();
      return;
    }

    this.loading = true;
    this.errorMessage = '';
    this.warningMessage = '';
    this.api.getCnaeCatalog().subscribe({
      next: (catalog) => {
        this.catalog = catalog?.length ? catalog : this.buildFallbackCatalog();
        this.warningMessage = catalog?.length
          ? ''
          : 'Catálogo CNAE local carregado. A busca fina será ativada quando o backend responder.';
        this.loaded = true;
        this.loading = false;
        this.refreshFilteredCatalog();
        this.expandToSelectedValue();
        this.cdr.markForCheck();
      },
      error: () => {
        this.catalog = this.buildFallbackCatalog();
        this.loaded = true;
        this.loading = false;
        this.errorMessage = '';
        this.warningMessage = 'Catálogo CNAE local carregado. Reinicie o backend para buscar subclasses completas da RF.';
        this.refreshFilteredCatalog();
        this.expandToSelectedValue();
        this.cdr.markForCheck();
      }
    });
  }

  private buildFallbackCatalog(): CnaeSectionCatalog[] {
    return FALLBACK_CNAE_SECTIONS.map((section) => {
      const sectionFilterValue = section.divisions.map((division) => division.code).join(',');
      return {
        sectionCode: section.code,
        title: section.title,
        searchHint: section.searchHint,
        sectionFilterValue,
        activities: [
          {
            code: section.code,
            label: `Toda a seção ${section.code}`,
            filterValue: sectionFilterValue,
            kind: 'SECTION' as const
          },
          ...section.divisions.map((division) => ({
            code: division.code,
            label: `${division.code} - ${division.label}`,
            filterValue: division.code,
            kind: 'DIVISION' as const
          }))
        ]
      };
    });
  }

  private refreshFilteredCatalog(): void {
    const query = this.searchQuery.trim().toLowerCase();
    if (!query) {
      this.filteredCatalog = this.catalog;
      return;
    }

    this.filteredCatalog = this.catalog
      .map((section) => {
        const sectionMatches =
          section.sectionCode.toLowerCase().includes(query) ||
          section.title.toLowerCase().includes(query) ||
          section.searchHint.toLowerCase().includes(query);

        const divisions = this.divisionActivities(section).filter(
          (activity) =>
            sectionMatches ||
            (activity.label ?? '').toLowerCase().includes(query) ||
            (activity.code ?? '').toLowerCase().includes(query) ||
            (activity.filterValue ?? '').toLowerCase().includes(query)
        );

        if (!sectionMatches && divisions.length === 0) {
          return null;
        }

        return {
          ...section,
          activities: [
            ...(section.activities.filter((activity) => activity.kind === 'SECTION')),
            ...divisions
          ]
        };
      })
      .filter((section): section is CnaeSectionCatalog => section !== null);
  }

  private syncExpansionFromSearch(): void {
    const query = this.searchQuery.trim();
    if (!query) {
      return;
    }

    this.filteredCatalog.forEach((section) => {
      this.expandedSections.add(section.sectionCode);
      this.divisionActivities(section).forEach((division) => {
        if (
          division.code.startsWith(query) ||
          query.startsWith(division.code) ||
          (division.label ?? '').toLowerCase().includes(query.toLowerCase())
        ) {
          this.expandedDivisions.add(division.code);
          this.ensureSubclassesLoaded(division.code);
        }
      });
    });

    this.searchResults.forEach((subclass) => {
      const divisionCode = subclass.code.slice(0, 2);
      const section = this.findSectionByDivisionPrefix(divisionCode);
      if (section) {
        this.expandedSections.add(section.sectionCode);
        this.expandedDivisions.add(divisionCode);
        this.ensureSubclassesLoaded(divisionCode);
      }
    });
  }

  private expandToSelectedValue(): void {
    if (!this.value) {
      return;
    }

    if (this.value.includes(',')) {
      const section = this.catalog.find((item) => item.sectionFilterValue === this.value);
      if (section) {
        this.expandedSections.add(section.sectionCode);
      }
      return;
    }

    const prefix = this.numericPrefix(this.value);
    if (!prefix) {
      return;
    }

    const divisionCode = prefix.slice(0, 2);
    const section = this.findSectionByDivisionPrefix(divisionCode);
    if (!section) {
      return;
    }

    this.expandedSections.add(section.sectionCode);
    this.expandedDivisions.add(divisionCode);
    this.ensureSubclassesLoaded(divisionCode);
  }

  private ensureSubclassesLoaded(divisionCode: string): void {
    if (this.subclassesByDivision.has(divisionCode) || this.loadingDivisions.has(divisionCode)) {
      return;
    }

    this.loadingDivisions.add(divisionCode);
    this.api.listCnaeSubclasses(divisionCode, 300).subscribe({
      next: (subclasses) => {
        this.subclassesByDivision.set(divisionCode, subclasses);
        subclasses.forEach((item) => this.rememberActivity(item));
        this.loadingDivisions.delete(divisionCode);
        this.cdr.markForCheck();
      },
      error: () => {
        this.subclassesByDivision.set(divisionCode, []);
        this.loadingDivisions.delete(divisionCode);
        this.cdr.markForCheck();
      }
    });
  }

  private findSectionByDivisionPrefix(divisionCode: string): CnaeSectionCatalog | undefined {
    return this.catalog.find((section) =>
      this.divisionActivities(section).some((division) => division.code === divisionCode)
    );
  }

  private numericPrefix(value: string): string | null {
    const match = value.match(/^(\d{2,7})/);
    return match?.[1] ?? null;
  }

  private resolveLabel(filterValue: string): string {
    if (!filterValue) {
      return 'Todos os setores';
    }

    const cached = this.labelCache.get(filterValue);
    if (cached) {
      return this.activityTitle(cached);
    }

    for (const section of this.catalog) {
      const activity = section.activities?.find((item) => item.filterValue === filterValue);
      if (activity) {
        return activity.kind === 'SECTION' ? `Toda a seção ${section.sectionCode}` : this.activityTitle(activity);
      }
    }

    const remote = this.searchResults.find((item) => item.filterValue === filterValue);
    if (remote) {
      return this.activityTitle(remote);
    }

    for (const subclasses of this.subclassesByDivision.values()) {
      const match = subclasses.find((item) => item.filterValue === filterValue);
      if (match) {
        return this.activityTitle(match);
      }
    }

    return `CNAE ${filterValue}`;
  }

  private rememberActivity(activity: CnaeActivityOption): void {
    this.labelCache.set(activity.filterValue, activity);
  }

  private resetSearch(): void {
    this.searchQuery = '';
    this.searchResults = [];
    this.searching = false;
    this.filteredCatalog = this.catalog;
    if (this.searchTimer) {
      clearTimeout(this.searchTimer);
    }
  }
}
