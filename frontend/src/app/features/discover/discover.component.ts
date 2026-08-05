import { AfterViewInit, Component, ElementRef, HostListener, NgZone, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Subscription, forkJoin, timeout } from 'rxjs';
import { L } from '../../core/leaflet';
import { ApiService, Company } from '../../core/api.service';
import {
  DISCOVER_IMPORTED_STATES,
  clearDiscoverSession,
  loadDiscoverSession,
  saveDiscoverSession
} from '../../core/discover-session';
import { CnaePickerComponent } from '../../shared/cnae-picker/cnae-picker.component';
import { stashAppointmentPrefill } from '../../core/appointment-prefill';

type MapStyleId = 'dark' | 'light' | 'satellite';
type MarkerSizeClass = 'large' | 'medium' | 'small' | 'unknown';

const MAP_STYLE_STORAGE_KEY = 'discover-map-style';

const MAP_STYLE_OPTIONS: { id: MapStyleId; label: string; hint: string }[] = [
  { id: 'dark', label: 'Escuro', hint: 'Contraste alto com vias e rótulos' },
  { id: 'light', label: 'Claro', hint: 'Melhor para rotas e malha urbana' },
  { id: 'satellite', label: 'Satélite', hint: 'Infraestrutura física e hangares' }
];

const DEFAULT_STATE = '';
/** Máximo de empresas mantidas em memória para filtro/paginação local. */
const RESULT_CACHE_MAX = 500;

const STATE_VIEWS: Record<string, { center: L.LatLngTuple; zoom: number; bounds: L.LatLngBoundsExpression }> = {
  BR: {
    center: [-15.5, -52.0],
    zoom: 5,
    bounds: [[-25.5, -58.5], [-12.0, -38.5]]
  },
  RJ: {
    center: [-22.9068, -43.1729],
    zoom: 8,
    bounds: [[-23.37, -44.95], [-20.76, -40.95]]
  },
  SP: {
    center: [-23.5505, -46.6333],
    zoom: 8,
    bounds: [[-25.35, -53.25], [-19.75, -44.05]]
  },
  MG: {
    center: [-19.9167, -43.9345],
    zoom: 7,
    bounds: [[-22.95, -51.08], [-14.23, -39.85]]
  }
};

const markerSizeClass = (revenue: string | null | undefined): MarkerSizeClass => {
  switch (revenue) {
    case 'LARGE':
      return 'large';
    case 'MEDIUM':
      return 'medium';
    case 'SMALL':
      return 'small';
    default:
      return 'unknown';
  }
};

const createCompanyMarkerIcon = (opts: {
  selected: boolean;
  approximate: boolean;
  hovered: boolean;
  sizeClass: MarkerSizeClass;
}): L.DivIcon => {
  const classes = ['pp-map-marker', `pp-map-marker--${opts.sizeClass}`];
  if (opts.selected) {
    classes.push('pp-map-marker--selected');
  }
  if (opts.approximate) {
    classes.push('pp-map-marker--approx');
  }
  if (opts.hovered) {
    classes.push('pp-map-marker--hovered');
  }
  const enlarged = opts.selected || opts.hovered;
  const size = enlarged ? 38 : 30;
  return L.divIcon({
    className: classes.join(' '),
    html: '<span aria-hidden="true"></span>',
    iconSize: [size, size],
    iconAnchor: [size / 2, size],
    popupAnchor: [0, -size]
  });
};

@Component({
  selector: 'app-discover',
  standalone: true,
  imports: [ReactiveFormsModule, CnaePickerComponent],
  templateUrl: './discover.component.html',
  styleUrl: './discover.component.scss'
})
export class DiscoverComponent implements OnInit, AfterViewInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly ngZone = inject(NgZone);

  private map?: L.Map;
  private baseLayerGroup = L.layerGroup();
  private markers!: ReturnType<typeof L.markerClusterGroup>;
  private markerByCompanyId = new Map<string, L.Marker>();
  private markerCoordsByCompanyId = new Map<string, L.LatLngTuple>();
  private pendingSessionRefetch = false;

  @ViewChild('mapContainer') mapContainer?: ElementRef<HTMLDivElement>;

  readonly stateOptions = DISCOVER_IMPORTED_STATES;
  readonly mapStyleOptions = MAP_STYLE_OPTIONS;
  activeMapStyle: MapStyleId = this.readStoredMapStyle();

  companies: Company[] = [];
  /** Resultado completo da busca em memória (todas as páginas, até RESULT_CACHE_MAX). */
  allCompanies: Company[] = [];
  resultCacheComplete = false;
  resultFilter = '';
  selected = new Set<string>();
  loading = false;
  searchError = '';
  hasSearched = false;
  totalElements = 0;
  pageIndex = 0;
  readonly pageSize = 40;
  filtersCollapsed = false;
  hoveredCompanyId: string | null = null;

  streetViewOpen = false;
  streetViewCompany: Company | null = null;
  streetViewEmbedUrl: SafeResourceUrl | null = null;
  streetViewExternalUrl = '';
  streetViewApproximate = false;
  refiningLocations = false;
  listPaintActive = false;
  private listPaintLastId: string | null = null;
  private searchSubscription?: Subscription;
  private prefetchSubscription?: Subscription;
  private refineSubscription?: Subscription;

  get filteredCompanies(): Company[] {
    const query = this.resultFilter.trim().toLowerCase();
    if (!query) {
      return this.allCompanies;
    }
    const digits = query.replace(/\D/g, '');
    return this.allCompanies.filter((company) => this.matchesResultFilter(company, query, digits));
  }

  get mappedCompaniesCount(): number {
    return this.mapCompanies.filter((company) => this.hasMapCoordinates(company)).length;
  }

  get selectedOnMapCount(): number {
    return this.mapCompanies.filter(
      (company) => this.selected.has(company.id) && this.hasMapCoordinates(company)
    ).length;
  }

  /** Empresas desenhadas no mapa: filtro ativo = todas as batidas; senão = página atual. */
  get mapCompanies(): Company[] {
    return this.hasResultFilter ? this.filteredCompanies : this.visibleCompanies;
  }

  get visibleCompanies(): Company[] {
    const start = this.pageIndex * this.pageSize;
    return this.filteredCompanies.slice(start, start + this.pageSize);
  }

  get hasResultFilter(): boolean {
    return this.resultFilter.trim().length > 0;
  }

  get selectedCountLabel(): string {
    return this.selected.size === 1 ? '1 empresa selecionada' : `${this.selected.size} empresas selecionadas`;
  }

  get allPageSelected(): boolean {
    const page = this.visibleCompanies;
    return page.length > 0 && page.every((company) => this.selected.has(company.id));
  }

  get somePageSelected(): boolean {
    const page = this.visibleCompanies;
    return page.some((company) => this.selected.has(company.id));
  }

  get selectPageLabel(): string {
    const count = this.visibleCompanies.length;
    if (this.allPageSelected) {
      return count === 1 ? 'Desmarcar esta página' : `Desmarcar as ${count} desta página`;
    }
    return count === 1 ? 'Selecionar esta página' : `Selecionar as ${count} desta página`;
  }

  get selectedOnMapLabel(): string | null {
    if (this.selectedOnMapCount === 0) {
      return null;
    }
    return this.selectedOnMapCount === 1
      ? '1 visível no mapa'
      : `${this.selectedOnMapCount} visíveis no mapa`;
  }

  get totalElementsLabel(): string {
    const state = this.filters.getRawValue().state.trim();
    if (!state && this.allCompanies.length > 0 && this.allCompanies.length >= 50) {
      return `${this.totalElements.toLocaleString('pt-BR')}+`;
    }
    return this.totalElements.toLocaleString('pt-BR');
  }

  get totalPages(): number {
    const count = this.filteredCompanies.length;
    return count === 0 ? 0 : Math.ceil(count / this.pageSize);
  }

  get pageRangeLabel(): string {
    const filtered = this.filteredCompanies.length;
    if (filtered === 0 || this.visibleCompanies.length === 0) {
      return '';
    }
    const from = this.pageIndex * this.pageSize + 1;
    const to = this.pageIndex * this.pageSize + this.visibleCompanies.length;
    if (this.hasResultFilter) {
      return `${from}-${to} de ${filtered.toLocaleString('pt-BR')} filtradas`;
    }
    const span = Math.min(this.totalElements, Math.max(this.allCompanies.length, to));
    return `${from}-${to} de ${span.toLocaleString('pt-BR')}`;
  }

  get canGoPrevPage(): boolean {
    return this.pageIndex > 0 && !this.loading;
  }

  get canGoNextPage(): boolean {
    if (this.loading || this.pageIndex + 1 >= this.totalPages) {
      return false;
    }
    // Só avança se a próxima página já estiver em memória (prefetch quase imediato).
    return this.filteredCompanies.length > (this.pageIndex + 1) * this.pageSize;
  }

  resultNumber(indexOnPage: number): number {
    return this.pageIndex * this.pageSize + indexOnPage + 1;
  }

  get isNationalSearch(): boolean {
    return this.filters.getRawValue().state.trim() === '';
  }

  get filtersSummary(): string {
    const value = this.filters.getRawValue();
    const parts: string[] = [];
    if (value.state) {
      parts.push(value.state);
    } else {
      parts.push('Todas as UFs');
    }
    if (value.cnae) {
      parts.push(`CNAE ${value.cnae}`);
    }
    if (value.city) {
      parts.push(value.city);
    }
    if (value.keyword) {
      parts.push(`"${value.keyword}"`);
    }
    if (value.revenue) {
      parts.push(value.revenue);
    }
    if (value.activeOnly) {
      parts.push('Ativas');
    }
    if (value.contactableOnly) {
      parts.push('Site contatável');
    }
    return parts.join(' · ');
  }

  filters = this.fb.nonNullable.group({
    keyword: [''],
    cnae: [''],
    state: [DEFAULT_STATE],
    city: [''],
    revenue: [''],
    activeOnly: [true],
    contactableOnly: [false]
  });

  ngOnInit(): void {
    if (typeof L.markerClusterGroup !== 'function') {
      console.error('leaflet.markercluster não carregou; usando layerGroup sem cluster');
      this.markers = L.layerGroup() as unknown as ReturnType<typeof L.markerClusterGroup>;
    } else {
      this.markers = L.markerClusterGroup({
        showCoverageOnHover: false,
        maxClusterRadius: 48,
        spiderfyOnMaxZoom: true,
        disableClusteringAtZoom: 16,
        iconCreateFunction: (cluster) => this.createClusterIcon(cluster)
      });
    }
    this.restoreSession();
  }

  ngAfterViewInit(): void {
    if (!this.mapContainer) {
      return;
    }

    const initial = STATE_VIEWS['BR'];
    this.map = L.map(this.mapContainer.nativeElement, {
      zoomControl: true,
      preferCanvas: false
    }).fitBounds(initial.bounds, { padding: [24, 24] });

    this.baseLayerGroup.addTo(this.map);
    this.applyMapStyle(this.activeMapStyle);
    this.markers.addTo(this.map);

    if (this.pendingSessionRefetch) {
      this.pendingSessionRefetch = false;
      this.search();
    }
  }

  setMapStyle(styleId: MapStyleId): void {
    if (this.activeMapStyle === styleId) {
      return;
    }
    this.activeMapStyle = styleId;
    localStorage.setItem(MAP_STYLE_STORAGE_KEY, styleId);
    this.applyMapStyle(styleId);
  }

  private readStoredMapStyle(): MapStyleId {
    const stored = localStorage.getItem(MAP_STYLE_STORAGE_KEY);
    if (stored === 'dark' || stored === 'light' || stored === 'satellite') {
      return stored;
    }
    return 'dark';
  }

  private applyMapStyle(styleId: MapStyleId): void {
    this.baseLayerGroup.clearLayers();
    for (const layer of this.createBaseLayers(styleId)) {
      this.baseLayerGroup.addLayer(layer);
    }
  }

  private createBaseLayers(styleId: MapStyleId): L.Layer[] {
    switch (styleId) {
      case 'light':
        return [
          L.tileLayer('https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png', {
            attribution: '&copy; OpenStreetMap &copy; CARTO',
            subdomains: 'abcd',
            maxZoom: 20
          })
        ];
      case 'satellite':
        return [
          L.tileLayer(
            'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
            {
              attribution: 'Tiles &copy; Esri',
              maxZoom: 19
            }
          ),
          L.tileLayer(
            'https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}',
            {
              attribution: 'Labels &copy; Esri',
              maxZoom: 19,
              opacity: 0.9
            }
          )
        ];
      case 'dark':
      default:
        // Esri Dark Gray: terra cinza, rótulos e vias legíveis (bem acima do Ultra Dark Carto).
        return [
          L.tileLayer(
            'https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile/{z}/{y}/{x}',
            {
              attribution: 'Tiles &copy; Esri',
              maxZoom: 16
            }
          ),
          L.tileLayer(
            'https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Reference/MapServer/tile/{z}/{y}/{x}',
            {
              attribution: 'Labels &copy; Esri',
              maxZoom: 16,
              opacity: 1
            }
          )
        ];
    }
  }

  ngOnDestroy(): void {
    this.endListPaint();
    this.abortActiveRequests();
    this.map?.remove();
    this.removeStreetViewKeyListener();
  }

  cancelSearch(): void {
    this.abortActiveRequests();
    this.loading = false;
    this.refiningLocations = false;
    this.searchError = 'Busca cancelada.';
    this.hasSearched = true;
  }

  private abortActiveRequests(): void {
    this.searchSubscription?.unsubscribe();
    this.prefetchSubscription?.unsubscribe();
    this.refineSubscription?.unsubscribe();
    this.searchSubscription = undefined;
    this.prefetchSubscription = undefined;
    this.refineSubscription = undefined;
  }

  private isCancelledError(error: unknown): boolean {
    return error instanceof HttpErrorResponse && error.status === 0 && error.error instanceof ProgressEvent;
  }

  closeStreetViewModal(): void {
    this.streetViewOpen = false;
    this.streetViewCompany = null;
    this.streetViewEmbedUrl = null;
    this.streetViewExternalUrl = '';
    this.streetViewApproximate = false;
    this.removeStreetViewKeyListener();
  }

  openStreetViewExternal(): void {
    if (this.streetViewExternalUrl) {
      window.open(this.streetViewExternalUrl, '_blank', 'noopener,noreferrer');
    }
  }

  search(): void {
    this.loadResults(0, true);
  }

  goToPrevPage(): void {
    if (!this.canGoPrevPage) {
      return;
    }
    this.pageIndex -= 1;
    this.refreshPagedView(true);
  }

  goToNextPage(): void {
    if (!this.canGoNextPage) {
      return;
    }
    this.pageIndex += 1;
    this.refreshPagedView(true);
  }

  private loadResults(_pageIndex: number, freshSearch: boolean): void {
    const filters = this.filters.getRawValue();
    const hasScope =
      filters.keyword.trim() !== '' ||
      filters.cnae.trim() !== '' ||
      filters.state.trim() !== '' ||
      filters.city.trim() !== '';
    if (!hasScope) {
      this.searchError = 'Informe pelo menos UF, CNAE, cidade ou palavra-chave para buscar.';
      this.hasSearched = true;
      return;
    }

    this.abortActiveRequests();
    this.loading = true;
    this.searchError = '';
    if (freshSearch) {
      this.resultFilter = '';
      this.selected.clear();
    }
    // Limpa lista imediatamente (evita “Cancelar busca” com resultado antigo).
    this.companies = [];
    this.allCompanies = [];
    this.resultCacheComplete = false;
    this.totalElements = 0;
    this.pageIndex = 0;
    this.renderMarkers([]);

    this.searchSubscription = this.api
      .searchCompanies({ ...filters, page: 0, size: this.pageSize })
      .pipe(timeout({ first: 15_000 }))
      .subscribe({
      next: (page) => {
        this.totalElements = page.totalElements;
        this.allCompanies = page.content;
        this.resultCacheComplete =
          page.content.length >= page.totalElements || page.totalElements <= this.pageSize;
        this.hasSearched = true;
        this.loading = false;
        this.filtersCollapsed = true;
        this.searchSubscription = undefined;
        this.syncCompaniesFromCache();
        this.persistSession();
        queueMicrotask(() => {
          this.renderMarkers(this.mapCompanies);
          this.fitMapToResults(this.mapCompanies, filters.state);
          this.map?.invalidateSize();
          this.refineMapCoordinates(this.visibleCompanies);
          document.querySelector('.list-scroll')?.scrollTo({ top: 0 });
        });
        this.prefetchAllResults(filters, page.totalElements, page.content);
      },
      error: (error: unknown) => {
        if (error instanceof HttpErrorResponse && this.isCancelledError(error)) {
          return;
        }
        this.companies = [];
        this.allCompanies = [];
        this.resultCacheComplete = false;
        this.totalElements = 0;
        this.hasSearched = true;
        if (error instanceof HttpErrorResponse) {
          this.searchError = this.resolveSearchError(error);
        } else if (error && typeof error === 'object' && 'name' in error && (error as { name?: string }).name === 'TimeoutError') {
          this.searchError = 'A busca demorou demais. Tente de novo com UF + CNAE.';
        } else {
          this.searchError = 'Não foi possível concluir a busca. Tente novamente.';
        }
        this.loading = false;
        this.searchSubscription = undefined;
        queueMicrotask(() => {
          this.renderMarkers([]);
          this.fitMapToResults([], this.filters.getRawValue().state);
        });
        this.persistSession();
      }
    });
  }

  /**
   * Após a 1ª página (rápida), busca o restante em um único request em background.
   * A UI já está liberada; o filtro passa a cobrir todas as páginas sem flicker.
   */
  private prefetchAllResults(
    filters: ReturnType<typeof this.filters.getRawValue>,
    totalElements: number,
    firstPage: Company[]
  ): void {
    const targetSize = Math.min(totalElements, RESULT_CACHE_MAX);
    if (targetSize <= firstPage.length) {
      this.resultCacheComplete = true;
      return;
    }

    this.prefetchSubscription = this.api
      .searchCompanies({ ...filters, page: 0, size: targetSize })
      .pipe(timeout({ first: 20_000 }))
      .subscribe({
        next: (page) => {
          this.prefetchSubscription = undefined;
          this.applyPrefetchedCompanies(page.content, page.totalElements, filters.state);
        },
        error: () => {
          this.prefetchSubscription = undefined;
          this.prefetchPagesInParallel(filters, totalElements, firstPage);
        }
      });
  }

  /** Fallback: páginas restantes em paralelo se o fetch único falhar. */
  private prefetchPagesInParallel(
    filters: ReturnType<typeof this.filters.getRawValue>,
    totalElements: number,
    firstPage: Company[]
  ): void {
    const capped = Math.min(totalElements, RESULT_CACHE_MAX);
    const pagesNeeded = Math.ceil(capped / this.pageSize);
    if (pagesNeeded <= 1) {
      this.resultCacheComplete = true;
      return;
    }

    const requests = [];
    for (let page = 1; page < pagesNeeded; page += 1) {
      requests.push(
        this.api
          .searchCompanies({ ...filters, page, size: this.pageSize })
          .pipe(timeout({ first: 15_000 }))
      );
    }

    this.prefetchSubscription = forkJoin(requests).subscribe({
      next: (pages) => {
        this.prefetchSubscription = undefined;
        const merged = [...firstPage, ...pages.flatMap((p) => p.content)].slice(0, RESULT_CACHE_MAX);
        this.applyPrefetchedCompanies(merged, totalElements, filters.state);
      },
      error: () => {
        this.prefetchSubscription = undefined;
        this.resultCacheComplete = false;
      }
    });
  }

  private applyPrefetchedCompanies(companies: Company[], totalElements: number, state: string): void {
    this.allCompanies = companies;
    this.totalElements = totalElements;
    this.resultCacheComplete = companies.length >= Math.min(totalElements, RESULT_CACHE_MAX);

    if (this.pageIndex >= this.totalPages && this.totalPages > 0) {
      this.pageIndex = this.totalPages - 1;
    }
    this.syncCompaniesFromCache();
    this.persistSession();

    // Sem filtro e na 1ª página: lista/mapa já mostram o mesmo conteúdo (sem re-render).
    if (this.hasResultFilter || this.pageIndex > 0) {
      queueMicrotask(() => {
        this.renderMarkers(this.mapCompanies);
        this.fitMapToResults(this.mapCompanies, state);
        if (this.pageIndex > 0) {
          this.refineMapCoordinates(this.visibleCompanies);
        }
      });
    }
  }

  private syncCompaniesFromCache(): void {
    this.companies = this.visibleCompanies;
  }

  private refreshPagedView(scroll: boolean): void {
    this.syncCompaniesFromCache();
    this.persistSession();
    queueMicrotask(() => {
      this.renderMarkers(this.mapCompanies);
      this.fitMapToResults(this.mapCompanies, this.filters.getRawValue().state);
      this.map?.invalidateSize();
      this.refineMapCoordinates(this.visibleCompanies);
      if (scroll) {
        document.querySelector('.list-scroll')?.scrollTo({ top: 0 });
      }
    });
  }

  clearFilters(): void {
    this.filters.reset({
      keyword: '',
      cnae: '',
      state: DEFAULT_STATE,
      city: '',
      revenue: '',
      activeOnly: true,
      contactableOnly: false
    });
    this.companies = [];
    this.allCompanies = [];
    this.resultCacheComplete = false;
    this.resultFilter = '';
    this.totalElements = 0;
    this.pageIndex = 0;
    this.selected.clear();
    this.hoveredCompanyId = null;
    this.filtersCollapsed = false;
    this.searchError = '';
    this.hasSearched = false;
    this.loading = false;
    this.renderMarkers([]);
    this.fitStateView('');
    clearDiscoverSession();
  }

  toggleFiltersCollapsed(): void {
    this.filtersCollapsed = !this.filtersCollapsed;
    queueMicrotask(() => this.map?.invalidateSize());
  }

  setHoveredCompany(companyId: string): void {
    if (this.hoveredCompanyId === companyId) {
      return;
    }
    const previous = this.hoveredCompanyId;
    this.hoveredCompanyId = companyId;
    if (previous) {
      this.syncMarkerAppearance(previous);
    }
    this.syncMarkerAppearance(companyId);
  }

  clearHoveredCompany(companyId: string): void {
    if (this.hoveredCompanyId !== companyId) {
      return;
    }
    this.hoveredCompanyId = null;
    this.syncMarkerAppearance(companyId);
  }

  private scrollCompanyCardIntoView(companyId: string): void {
    const card = document.querySelector(`[data-company-id="${companyId}"]`);
    card?.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
  }

  toggleSelect(id: string): void {
    if (this.selected.has(id)) {
      this.selected.delete(id);
    } else {
      this.selected.add(id);
    }
    this.syncMarkerAppearance(id);
    this.refreshClusterIcons(id);
    if (this.hasSearched) {
      this.persistSession();
    }
  }

  toggleSelectPage(): void {
    const page = this.visibleCompanies;
    if (page.length === 0) {
      return;
    }
    const selectAll = !this.allPageSelected;
    for (const company of page) {
      if (selectAll) {
        this.selected.add(company.id);
      } else {
        this.selected.delete(company.id);
      }
      this.syncMarkerAppearance(company.id);
    }
    this.refreshClusterIcons();
    if (this.hasSearched) {
      this.persistSession();
    }
  }

  /** Clique esquerdo + arraste: entra numa empresa = alterna seleção; voltar = desmarca. */
  onCompanyPointerDown(event: PointerEvent, companyId: string): void {
    if (event.button !== 0) {
      return;
    }
    const target = event.target as HTMLElement | null;
    if (target?.closest('button, a, .card-action, .card-actions')) {
      return;
    }
    event.preventDefault();
    this.listPaintActive = true;
    this.listPaintLastId = companyId;
    this.toggleSelect(companyId);
  }

  onCompanyPointerEnter(companyId: string): void {
    if (!this.listPaintActive || this.listPaintLastId === companyId) {
      return;
    }
    this.listPaintLastId = companyId;
    this.toggleSelect(companyId);
  }

  endListPaint(): void {
    if (!this.listPaintActive) {
      return;
    }
    this.listPaintActive = false;
    this.listPaintLastId = null;
  }

  @HostListener('window:pointerup')
  onWindowPointerUp(): void {
    this.endListPaint();
  }

  @HostListener('window:pointercancel')
  onWindowPointerCancel(): void {
    this.endListPaint();
  }

  storeSelection(): void {
    if (this.selected.size === 0) {
      return;
    }
    const selectedCompanies = this.allCompanies.filter((company) => this.selected.has(company.id));
    sessionStorage.setItem('selected-companies', JSON.stringify([...this.selected]));
    sessionStorage.setItem('selected-companies-cache', JSON.stringify(selectedCompanies));
    this.persistSession();
    void this.router.navigate(['/enrich']);
  }

  sendToEnrich(companyId: string, event: Event): void {
    event.stopPropagation();
    const company = this.allCompanies.find((item) => item.id === companyId);
    sessionStorage.setItem('selected-companies', JSON.stringify([companyId]));
    sessionStorage.setItem('selected-companies-cache', JSON.stringify(company ? [company] : []));
    this.persistSession();
    void this.router.navigate(['/enrich']);
  }

  sendToOutreach(companyId: string, event: Event): void {
    event.stopPropagation();
    sessionStorage.setItem('selected-companies', JSON.stringify([companyId]));
    this.persistSession();
    void this.router.navigate(['/outreach']);
  }

  sendToAgenda(company: Company): void {
    this.map?.closePopup();
    stashAppointmentPrefill({
      clientName: company.tradeName || company.legalName,
      clientCompany: company.legalName,
      clientEmail: company.email,
      clientPhone: company.phone,
      title: `Reunião comercial · ${company.tradeName || company.legalName}`,
      description: company.cnaeDescription
        ? `Segmento: ${company.cnaeDescription}`
        : undefined,
      openForm: true
    });
    void this.router.navigate(['/agenda']);
  }

  updateResultFilter(event: Event): void {
    this.resultFilter = (event.target as HTMLInputElement).value;
    this.applyResultFilterView();
    if (this.hasSearched) {
      this.persistSession();
    }
  }

  clearResultFilter(): void {
    this.resultFilter = '';
    this.applyResultFilterView();
    if (this.hasSearched) {
      this.persistSession();
    }
  }

  revenueBadge(revenue: string | null | undefined): string {
    switch (revenue) {
      case 'LARGE':
        return 'pp-badge pp-badge--violet';
      case 'MEDIUM':
        return 'pp-badge pp-badge--cyan';
      case 'SMALL':
        return 'pp-badge pp-badge--emerald';
      default:
        return 'pp-badge';
    }
  }

  /** CNAE principal: código + descrição oficial RF (pode repetir no mesmo segmento). */
  activityLabel(company: Company): string {
    const code = (company.cnaeMain || '').trim();
    const desc = (company.cnaeDescription || '').trim();
    if (code && desc) {
      return `${code} · ${desc}`;
    }
    if (desc) {
      return desc;
    }
    return code ? `CNAE ${code}` : 'Atividade não informada';
  }

  /** Diferencia empresas do mesmo CNAE principal via secundários. */
  secondaryActivityLabel(company: Company): string | null {
    const codes = (company.cnaeSecondary || '')
      .split(',')
      .map((value) => value.trim())
      .filter((value) => value.length > 0);
    if (codes.length === 0) {
      return null;
    }
    if (codes.length <= 3) {
      return `Secundários: ${codes.join(', ')}`;
    }
    return `Secundários: ${codes.slice(0, 3).join(', ')} +${codes.length - 3}`;
  }

  locationLabel(company: Company): string {
    const cityState = [company.city, company.state].filter(Boolean).join('/');
    const neighborhood = (company.neighborhood || '').trim();
    if (neighborhood && cityState) {
      return `${neighborhood} · ${cityState}`;
    }
    return cityState || neighborhood || 'Local não informado';
  }

  private applyResultFilterView(): void {
    this.pageIndex = 0;
    this.syncCompaniesFromCache();
    this.renderMarkers(this.mapCompanies);
    this.fitMapToResults(this.mapCompanies, this.filters.getRawValue().state);
  }

  private normalizeSearchText(value: string): string {
    return value
      .normalize('NFD')
      .replace(/\p{M}/gu, '')
      .toLowerCase();
  }

  private compactSearchText(value: string): string {
    return this.normalizeSearchText(value).replace(/[^a-z0-9]/g, '');
  }

  private matchesResultFilter(company: Company, query: string, digits: string): boolean {
    const normalizedQuery = this.normalizeSearchText(query);
    if (!normalizedQuery) {
      return true;
    }

    const compactQuery = this.compactSearchText(query);
    const queryChars = query.replace(/\s/g, '');
    const digitRatio = digits.length / Math.max(queryChars.length, 1);
    if (digits.length >= 3 && digitRatio >= 0.6) {
      const cnpj = company.cnpj?.replace(/\D/g, '') ?? '';
      return cnpj.includes(digits);
    }

    const fields = [
      company.tradeName,
      company.legalName,
      company.city,
      company.state,
      company.cnaeDescription,
      company.cnaeMain,
      company.cnpj
    ].filter((value): value is string => !!value && value.trim().length > 0);

    return fields.some((field) => {
      const normalized = this.normalizeSearchText(field);
      if (normalized.includes(normalizedQuery)) {
        return true;
      }
      // "gec" deve achar "GE CELMA" (ignora espaço/pontuação)
      if (compactQuery.length >= 2 && this.compactSearchText(field).includes(compactQuery)) {
        return true;
      }
      return normalized
        .split(/[^a-z0-9]+/)
        .some((word) => word.startsWith(normalizedQuery));
    });
  }

  private renderMarkers(companies: Company[]): void {
    if (!this.markers) {
      return;
    }
    this.markers.clearLayers();
    this.markerByCompanyId.clear();
    this.markerCoordsByCompanyId.clear();

    companies
      .filter((company) => this.hasMapCoordinates(company))
      .forEach((company) => {
        const coords: L.LatLngTuple = [company.latitude!, company.longitude!];
        const isSelected = this.selected.has(company.id);
        const approximate = this.isApproximateLocation(company);
        const hovered = this.hoveredCompanyId === company.id;
        const sizeClass = markerSizeClass(company.estimatedRevenue);
        const marker = L.marker(coords, {
          icon: createCompanyMarkerIcon({ selected: isSelected, approximate, hovered, sizeClass }),
          zIndexOffset: isSelected || hovered ? 1000 : 0,
          companyId: company.id
        } as L.MarkerOptions)
          .bindTooltip(this.buildMarkerTooltip(company), {
            direction: 'top',
            offset: L.point(0, -32),
            opacity: 1,
            className: 'pp-map-tooltip',
            sticky: true
          })
          .bindPopup(this.buildMarkerPopup(company), {
            className: 'pp-map-popup',
            maxWidth: 280,
            offset: L.point(0, -8)
          })
          .on('mouseover', () =>
            this.ngZone.run(() => {
              this.setHoveredCompany(company.id);
              this.scrollCompanyCardIntoView(company.id);
            })
          )
          .on('mouseout', () => this.ngZone.run(() => this.clearHoveredCompany(company.id)));

        this.markers.addLayer(marker);
        this.markerByCompanyId.set(company.id, marker);
        this.markerCoordsByCompanyId.set(company.id, coords);
      });
  }

  private buildMarkerPopup(company: Company): HTMLElement {
    const root = document.createElement('div');
    root.className = 'pp-map-popup__body';

    const name = company.tradeName || company.legalName;
    const address = [company.street, company.neighborhood, `${company.city}/${company.state}`]
      .filter(Boolean)
      .join(' · ');
    const revenue = company.estimatedRevenue || 'Porte n/d';

    root.innerHTML = `
      <strong class="pp-map-popup__title">${this.escapeHtml(name)}</strong>
      <span class="pp-map-popup__meta">${this.escapeHtml(address || 'Endereço não informado')}</span>
      <span class="pp-map-popup__badge">${this.escapeHtml(revenue)}</span>
      <div class="pp-map-popup__actions">
        <button type="button" data-action="select">Selecionar</button>
        <button type="button" data-action="enrich">Qualificar</button>
        <button type="button" data-action="schedule">Agendar</button>
        <button type="button" data-action="street">Street View</button>
      </div>
    `;

    root.querySelector('[data-action="select"]')?.addEventListener('click', (event) => {
      event.preventDefault();
      this.ngZone.run(() => {
        if (!this.selected.has(company.id)) {
          this.toggleSelect(company.id);
        }
        this.map?.closePopup();
      });
    });
    root.querySelector('[data-action="enrich"]')?.addEventListener('click', (event) => {
      event.preventDefault();
      this.ngZone.run(() => this.sendToEnrich(company.id, event));
    });
    root.querySelector('[data-action="schedule"]')?.addEventListener('click', (event) => {
      event.preventDefault();
      this.ngZone.run(() => this.sendToAgenda(company));
    });
    root.querySelector('[data-action="street"]')?.addEventListener('click', (event) => {
      event.preventDefault();
      this.ngZone.run(() => {
        this.map?.closePopup();
        this.openStreetViewModal(company, company.latitude!, company.longitude!);
      });
    });

    return root;
  }

  private refineMapCoordinates(companies: Company[]): void {
    // Só refina quem ainda não tem pin útil. CEP/CITY já bastam para o mapa.
    const ids = companies
      .filter((company) => !this.hasMapCoordinates(company) || company.locationPrecision === 'UNRESOLVED')
      .map((company) => company.id)
      .slice(0, 10);
    if (ids.length === 0) {
      return;
    }

    this.refiningLocations = true;
    this.refineSubscription = this.api.refineCompanyCoordinates(ids).subscribe({
      next: (refined) => {
        const byId = new Map(refined.map((company) => [company.id, company]));
        this.companies = this.companies.map((company) => byId.get(company.id) ?? company);
        this.renderMarkers(this.visibleCompanies);
        this.refiningLocations = false;
        this.refineSubscription = undefined;
        this.persistSession();
      },
      error: (error: HttpErrorResponse) => {
        if (!this.isCancelledError(error)) {
          this.refiningLocations = false;
        }
        this.refineSubscription = undefined;
      }
    });
  }

  private hasMapCoordinates(company: Company): boolean {
    return company.latitude != null && company.longitude != null;
  }

  private isApproximateLocation(company: Company): boolean {
    return company.locationPrecision !== 'EXACT';
  }

  private createClusterIcon(cluster: {
    getAllChildMarkers: () => L.Marker[];
    getChildCount: () => number;
  }): L.DivIcon {
    const childMarkers = cluster.getAllChildMarkers();
    const selectedCount = childMarkers.filter((marker: L.Marker) => {
      const companyId = (marker.options as L.MarkerOptions & { companyId?: string }).companyId;
      return companyId != null && this.selected.has(companyId);
    }).length;
    const count = cluster.getChildCount();
    const sizeClass = count >= 10 ? 'lg' : count >= 4 ? 'md' : 'sm';
    const hasSelected = selectedCount > 0;
    const label = hasSelected ? `${selectedCount}/${count}` : `${count}`;
    const size = count >= 10 ? 48 : count >= 4 ? 42 : 36;

    return L.divIcon({
      html: `
        <div class="pp-map-cluster__inner">
          <span class="pp-map-cluster__count">${label}</span>
          <div class="pp-map-cluster__balloon" role="tooltip">
            ${this.buildClusterTooltip(count, selectedCount)}
          </div>
        </div>
      `,
      className: [
        'leaflet-div-icon',
        'pp-map-cluster',
        `pp-map-cluster--${sizeClass}`,
        hasSelected ? 'pp-map-cluster--selected' : ''
      ]
        .filter(Boolean)
        .join(' '),
      iconSize: L.point(size, size),
      iconAnchor: L.point(size / 2, size / 2)
    });
  }

  private buildClusterTooltip(count: number, selectedCount: number): string {
    const empresaLabel = count === 1 ? 'empresa' : 'empresas';
    const selectionLine =
      selectedCount > 0
        ? `<span>${selectedCount} de ${count} selecionada${selectedCount === 1 ? '' : 's'}</span>`
        : '';

    return `
      <div class="pp-map-tooltip__content">
        <strong>${count} ${empresaLabel} neste ponto</strong>
        <span>O número no pin indica quantas empresas estão agrupadas aqui.</span>
        ${selectionLine}
        <em>Clique para expandir e ver cada empresa no mapa</em>
      </div>
    `;
  }

  private refreshClusterIcons(companyId?: string): void {
    if (companyId) {
      const marker = this.markerByCompanyId.get(companyId);
      if (marker) {
        this.markers.refreshClusters(marker);
        return;
      }
    }
    this.markers.refreshClusters();
  }

  private syncMarkerAppearance(companyId: string, options: { fitSelection?: boolean } = {}): void {
    const marker = this.markerByCompanyId.get(companyId);
    if (!marker) {
      return;
    }

    const isSelected = this.selected.has(companyId);
    const hovered = this.hoveredCompanyId === companyId;
    const company = this.allCompanies.find((item) => item.id === companyId);
    marker.setIcon(
      createCompanyMarkerIcon({
        selected: isSelected,
        approximate: company ? this.isApproximateLocation(company) : false,
        hovered,
        sizeClass: markerSizeClass(company?.estimatedRevenue)
      })
    );
    marker.setZIndexOffset(isSelected || hovered ? 1000 : 0);

    if (options.fitSelection && isSelected) {
      this.fitMapToSelection();
    }
  }

  /** Mantido para fluxos que precisem enquadrar a seleção (ex.: atalho futuro). */
  private syncMarkerSelection(companyId: string): void {
    this.syncMarkerAppearance(companyId);
  }

  private fitMapToSelection(): void {
    if (!this.map || this.selected.size === 0) {
      return;
    }

    const points = [...this.selected]
      .map((id) => this.markerCoordsByCompanyId.get(id))
      .filter((coords): coords is L.LatLngTuple => coords != null);

    if (points.length === 0) {
      return;
    }

    if (points.length === 1) {
      this.map.flyTo(points[0], Math.max(this.map.getZoom(), 12), { duration: 0.45 });
      return;
    }

    this.map.fitBounds(L.latLngBounds(points), { padding: [56, 56], maxZoom: 13 });
  }

  private openStreetViewModal(company: Company, lat: number, lng: number): void {
    const addressQuery = this.buildMapsAddressQuery(company);
    const approximate = this.isApproximateLocation(company);
    const embedUrl = approximate
      ? `https://maps.google.com/maps?q=${encodeURIComponent(addressQuery)}&z=17&output=embed`
      : `https://maps.google.com/maps?layer=c&cbll=${lat},${lng}&cbp=12,0,0,0,0&output=svembed`;
    this.streetViewCompany = company;
    this.streetViewEmbedUrl = this.sanitizer.bypassSecurityTrustResourceUrl(embedUrl);
    this.streetViewExternalUrl = `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(addressQuery)}`;
    this.streetViewApproximate = approximate;
    this.streetViewOpen = true;
    this.bindStreetViewKeyListener();
  }

  private buildMapsAddressQuery(company: Company): string {
    const parts = [
      company.street,
      company.neighborhood,
      `${company.city}/${company.state}`,
      company.zipCode ? `CEP ${company.zipCode}` : ''
    ].filter(Boolean);
    return parts.join(', ');
  }

  private buildMarkerTooltip(company: Company): string {
    const name = this.escapeHtml(company.tradeName || company.legalName);
    const street = company.street ? this.escapeHtml(company.street) : '';
    const location = this.escapeHtml(`${company.city}/${company.state}`);
    const cnae = company.cnaeDescription
      ? this.escapeHtml(company.cnaeDescription)
      : company.cnaeMain
        ? this.escapeHtml(`CNAE ${company.cnaeMain}`)
        : '';
    const revenue = company.estimatedRevenue ? this.escapeHtml(company.estimatedRevenue) : '';
    const precisionLabel = this.locationPrecisionLabel(company);
    const approx = this.isApproximateLocation(company)
      ? `<em>${precisionLabel}</em>`
      : '<em>Endereço verificado · clique no pin para ações</em>';

    return `
      <div class="pp-map-tooltip__content">
        <strong>${name}</strong>
        ${street ? `<span>${street}</span>` : ''}
        <span>${location}</span>
        ${cnae ? `<span>${cnae}</span>` : ''}
        ${revenue ? `<span class="pp-map-tooltip__badge">${revenue}</span>` : ''}
        ${approx}
      </div>
    `;
  }

  private locationPrecisionLabel(company: Company): string {
    switch (company.locationPrecision) {
      case 'EXACT':
        return 'Endereço verificado';
      case 'CEP':
        return 'Localização aproximada (área do CEP)';
      case 'CITY':
        return 'Localização aproximada (centro da cidade)';
      default:
        return 'Localização aproximada';
    }
  }

  private escapeHtml(value: string): string {
    return value
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  private streetViewKeyHandler = (event: KeyboardEvent): void => {
    if (event.key === 'Escape') {
      this.closeStreetViewModal();
    }
  };

  private bindStreetViewKeyListener(): void {
    window.addEventListener('keydown', this.streetViewKeyHandler);
  }

  private removeStreetViewKeyListener(): void {
    window.removeEventListener('keydown', this.streetViewKeyHandler);
  }

  private fitMapToResults(companies: Company[], state: string): void {
    if (!this.map) {
      return;
    }

    const points = companies
      .filter((company) => company.latitude != null && company.longitude != null)
      .map((company) => [company.latitude!, company.longitude!] as L.LatLngTuple);

    if (points.length === 1) {
      this.map.setView(points[0], 11);
      return;
    }

    if (points.length > 1) {
      this.map.fitBounds(L.latLngBounds(points), { padding: [48, 48], maxZoom: 11 });
      return;
    }

    this.fitStateView(state);
  }

  private resolveSearchError(error: HttpErrorResponse): string {
    if (error.status === 0) {
      return 'Não foi possível conectar ao backend. Verifique se a API está rodando em http://localhost:8082 e tente novamente.';
    }
    if (error.status === 401 || error.status === 403) {
      return 'Sua sessão expirou ou é inválida. Faça login novamente para continuar.';
    }
    if (error.status >= 500) {
      return 'A busca está temporariamente indisponível. Verifique se o backend está ativo e tente novamente.';
    }
    return 'Não foi possível concluir a busca com estes filtros. Tente novamente.';
  }

  private fitStateView(state: string): void {
    if (!this.map) {
      return;
    }

    const key = (state || DEFAULT_STATE).trim().toUpperCase() || 'BR';
    const view = STATE_VIEWS[key] ?? STATE_VIEWS['BR'];
    this.map.fitBounds(view.bounds, { padding: [24, 24] });
  }

  private restoreSession(): void {
    const session = loadDiscoverSession();
    if (!session) {
      return;
    }

    this.filters.patchValue(session.filters);
    this.resultFilter = session.resultFilter ?? '';
    this.selected = new Set(session.selectedIds);
    this.searchError = '';

    // Não reutiliza a lista em cache: a busca pode ter mudado (ex.: CNAE no Postgres).
    // Restaura só filtros/seleção e refetch na API.
    if (session.hasSearched) {
      this.filtersCollapsed = true;
      this.pendingSessionRefetch = true;
    }
  }

  private persistSession(): void {
    saveDiscoverSession({
      filters: this.filters.getRawValue(),
      companies: this.allCompanies.length <= 200 ? this.allCompanies : this.companies,
      resultFilter: this.resultFilter,
      totalElements: this.totalElements,
      selectedIds: [...this.selected],
      hasSearched: this.hasSearched,
      searchError: this.searchError
    });
  }
}
