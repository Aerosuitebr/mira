import { Company } from './api.service';

export const DISCOVER_IMPORTED_STATES = [
  { value: 'SP', label: 'SP - São Paulo' },
  { value: 'RJ', label: 'RJ - Rio de Janeiro' },
  { value: 'MG', label: 'MG - Minas Gerais' },
  { value: 'ES', label: 'ES - Espírito Santo' },
  { value: 'GO', label: 'GO - Goiás' },
  { value: 'DF', label: 'DF - Distrito Federal' },
  { value: 'MT', label: 'MT - Mato Grosso' },
  { value: 'MS', label: 'MS - Mato Grosso do Sul' }
] as const;

/** Bump ao mudar semântica da busca (ex.: CNAE no Postgres) para invalidar cache antigo. */
export const DISCOVER_SESSION_VERSION = 3;

export interface DiscoverFiltersSnapshot {
  keyword: string;
  cnae: string;
  state: string;
  city: string;
  revenue: string;
  activeOnly: boolean;
  contactableOnly: boolean;
}

export interface DiscoverSearchSession {
  version?: number;
  filters: DiscoverFiltersSnapshot;
  companies: Company[];
  resultFilter?: string;
  totalElements: number;
  selectedIds: string[];
  hasSearched: boolean;
  searchError: string;
}

const STORAGE_KEY = 'discover-search-session';

export function loadDiscoverSession(): DiscoverSearchSession | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return null;
    }
    const parsed = JSON.parse(raw) as DiscoverSearchSession;
    if (!parsed?.filters || !Array.isArray(parsed.companies)) {
      return null;
    }
    if (parsed.version !== DISCOVER_SESSION_VERSION) {
      sessionStorage.removeItem(STORAGE_KEY);
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
}

export function saveDiscoverSession(session: DiscoverSearchSession): void {
  sessionStorage.setItem(
    STORAGE_KEY,
    JSON.stringify({ ...session, version: DISCOVER_SESSION_VERSION })
  );
}

export function clearDiscoverSession(): void {
  sessionStorage.removeItem(STORAGE_KEY);
}
