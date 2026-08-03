import { Injectable } from '@angular/core';
import { Company } from './api.service';

export type AerosuitePlanId = 'PLANO_I' | 'PLANO_II' | 'PLANO_III';

export interface AerosuitePlanOffer {
  id: AerosuitePlanId;
  label: string;
  shortLabel: string;
  monthlyPrice: number;
  setupPrice: number;
  month1Price: number;
  usersIncluded: string;
  sgqIncluded: boolean;
  recommended?: boolean;
}

export interface AerosuitePainPoint {
  label: string;
  module: string;
}

export interface AerosuiteFitAnalysis {
  icpMatch: boolean;
  icpSegment: string;
  plan: AerosuitePlanOffer;
  fitScore: number;
  readiness: 'alta' | 'media' | 'baixa';
  painPoints: AerosuitePainPoint[];
  modules: string[];
  reasons: string[];
  pitchLine: string;
  trialEligible: boolean;
}

const PLANS: Record<AerosuitePlanId, AerosuitePlanOffer> = {
  PLANO_I: {
    id: 'PLANO_I',
    label: 'Plano I · Tenant na Nuvem',
    shortLabel: 'Plano I',
    monthlyPrice: 2490,
    setupPrice: 990,
    month1Price: 3480,
    usersIncluded: 'até 15 usuários',
    sgqIncluded: false
  },
  PLANO_II: {
    id: 'PLANO_II',
    label: 'Plano II · Dedicada na Nuvem',
    shortLabel: 'Plano II',
    monthlyPrice: 4990,
    setupPrice: 1490,
    month1Price: 6480,
    usersIncluded: 'até 30 usuários',
    sgqIncluded: true,
    recommended: true
  },
  PLANO_III: {
    id: 'PLANO_III',
    label: 'Plano III · Dedicada Física',
    shortLabel: 'Plano III',
    monthlyPrice: 5480,
    setupPrice: 4900,
    month1Price: 10380,
    usersIncluded: 'usuários ilimitados',
    sgqIncluded: true
  }
};

const AIRPORT_HINTS = [
  'GALEAO',
  'AEROPORTO',
  'HANGAR',
  'CAMPO DE MARTE',
  'JACAREPAGUA',
  'SBGL',
  'SBMT',
  'DESEMBARGADOR',
  'INDUSTRIAL DO AEROPORTO'
];

const CORE_MRO_CNAE_PREFIXES = ['3316301', '3316302', '33163'];
const AVIATION_CNAE_PREFIXES = ['33163', '30415', '30423', '5111', '5120'];

@Injectable({ providedIn: 'root' })
export class AerosuiteFitService {
  analyze(company: Company): AerosuiteFitAnalysis {
    const cnae = this.resolveEffectiveCnae(company);
    const segment = this.resolveSegment(cnae, company.cnaeDescription ?? '');
    const icpMatch = segment !== 'FORA_ICP';
    const capital = Number(company.capitalSocial) || 0;
    const airportHub = this.isAirportHub(company);
    const hasContact = this.hasContactChannel(company);
    const planId = this.selectPlan(company, cnae, capital, airportHub);
    const plan = PLANS[planId];
    const painPoints = this.resolvePainPoints(cnae, segment, plan);
    const modules = this.resolveModules(painPoints, plan);
    const reasons = this.buildReasons(company, segment, plan, capital, airportHub, hasContact);
    const fitScore = this.computeFitScore(icpMatch, segment, capital, company, airportHub, hasContact, plan);
    const readiness = fitScore >= 78 ? 'alta' : fitScore >= 55 ? 'media' : 'baixa';

    return {
      icpMatch,
      icpSegment: segment,
      plan,
      fitScore,
      readiness,
      painPoints,
      modules,
      reasons,
      pitchLine: this.buildPitch(company, plan, painPoints, segment),
      trialEligible: plan.id === 'PLANO_I'
    };
  }

  planOffer(id: AerosuitePlanId): AerosuitePlanOffer {
    return PLANS[id];
  }

  private resolveEffectiveCnae(company: Company): string {
    const main = (company.cnaeMain ?? '').replace(/\D/g, '');
    if (CORE_MRO_CNAE_PREFIXES.some((prefix) => main.startsWith(prefix))) {
      return main;
    }
    const secondary = (company.cnaeSecondary ?? '')
      .split(',')
      .map((code) => code.replace(/\D/g, ''))
      .find((code) => CORE_MRO_CNAE_PREFIXES.some((prefix) => code.startsWith(prefix)));
    return secondary ?? main;
  }

  private resolveSegment(cnae: string, description: string): string {
    if (cnae.startsWith('3316301')) {
      return 'MRO_HANGAR';
    }
    if (cnae.startsWith('3316302')) {
      return 'MRO_PISTA';
    }
    if (cnae.startsWith('33163')) {
      return 'MRO_AERONAVES';
    }
    if (cnae.startsWith('30415')) {
      return 'FABRICANTE_AERONAVES';
    }
    if (cnae.startsWith('30423')) {
      return 'FABRICANTE_PECAS';
    }
    if (cnae.startsWith('5111') || cnae.startsWith('5120')) {
      return 'OPERADOR_AEREO';
    }
    const text = `${cnae} ${description}`.toUpperCase();
    if (text.includes('AERONAV') || text.includes('MANUTEN') && text.includes('AER')) {
      return 'MRO_AERONAVES';
    }
    if (AVIATION_CNAE_PREFIXES.some((prefix) => cnae.startsWith(prefix))) {
      return 'AVIACAO_RELACIONADA';
    }
    return 'FORA_ICP';
  }

  private selectPlan(
    company: Company,
    cnae: string,
    capital: number,
    airportHub: boolean
  ): AerosuitePlanId {
    const revenue = company.estimatedRevenue;
    const isLarge = revenue === 'LARGE' || capital >= 3_000_000;
    const isMedium = revenue === 'MEDIUM' || capital >= 800_000;
    const isCoreMro = CORE_MRO_CNAE_PREFIXES.some((prefix) => cnae.startsWith(prefix));

    if (isLarge && (airportHub || capital >= 5_000_000)) {
      return 'PLANO_III';
    }
    if (isMedium || (isCoreMro && capital >= 400_000) || airportHub) {
      return 'PLANO_II';
    }
    if (cnae.startsWith('3316302') && capital < 500_000) {
      return 'PLANO_I';
    }
    return isCoreMro ? 'PLANO_II' : 'PLANO_I';
  }

  private resolvePainPoints(cnae: string, segment: string, plan: AerosuitePlanOffer): AerosuitePainPoint[] {
    const points: AerosuitePainPoint[] = [
      { label: 'Proposta desconectada do hangar', module: 'Comercial' },
      { label: 'Peças sem rastreio FIFO', module: 'Estoque' },
      { label: 'OS sem trilha Part 145', module: 'MRO / Hangar' }
    ];

    if (segment === 'MRO_PISTA' || segment === 'MRO_HANGAR' || segment === 'MRO_AERONAVES') {
      points.push({ label: 'Cliente sem visibilidade da OS', module: 'Portal' });
    }
    if (plan.sgqIncluded || segment.includes('MRO')) {
      points.push({ label: 'SGQ e NC/CAPA fragmentados', module: 'Conformidade SGQ' });
    }
    if (segment === 'FABRICANTE_AERONAVES' || segment === 'FABRICANTE_PECAS') {
      points.unshift({ label: 'Certificados e back-to-birth', module: 'Estoque' });
    }
    return points.slice(0, 4);
  }

  private resolveModules(painPoints: AerosuitePainPoint[], plan: AerosuitePlanOffer): string[] {
    const modules = new Set<string>();
    painPoints.forEach((point) => modules.add(point.module));
    modules.add('Plataforma RBAC');
    if (plan.sgqIncluded) {
      modules.add('SGQ ISO');
    }
    return [...modules].slice(0, 5);
  }

  private buildReasons(
    company: Company,
    segment: string,
    plan: AerosuitePlanOffer,
    capital: number,
    airportHub: boolean,
    hasContact: boolean
  ): string[] {
    const reasons: string[] = [];
    if (segment.startsWith('MRO')) {
      reasons.push('CNAE alinhado a manutenção aeronáutica (Part 145).');
    }
    if (airportHub) {
      reasons.push('Localização próxima a hub aeroportuário — operação intensiva.');
    }
    if (capital >= 1_000_000) {
      reasons.push(`Capital social ${this.formatCapital(capital)} — absorve ${plan.shortLabel}.`);
    } else if (capital > 0) {
      reasons.push(`Porte enxuto — entrada pelo ${plan.shortLabel} com trial 7 dias.`);
    }
    if (hasContact) {
      reasons.push('Contato RF disponível para abordagem comercial.');
    }
    if (plan.recommended) {
      reasons.push('Melhor equilíbrio custo × SGQ incluso (promo lançamento).');
    }
    if (company.openedAt && new Date(company.openedAt).getFullYear() <= 2018) {
      reasons.push('Operação madura — maior chance de processos legados fragmentados.');
    }
    return reasons.slice(0, 4);
  }

  private computeFitScore(
    icpMatch: boolean,
    segment: string,
    capital: number,
    company: Company,
    airportHub: boolean,
    hasContact: boolean,
    plan: AerosuitePlanOffer
  ): number {
    if (!icpMatch) {
      return 28;
    }

    let score = segment.startsWith('MRO') ? 62 : 48;
    if (segment === 'MRO_HANGAR' || segment === 'MRO_AERONAVES') {
      score += 12;
    }
    if (capital >= 800_000) {
      score += 8;
    }
    if (company.estimatedRevenue === 'LARGE') {
      score += 10;
    } else if (company.estimatedRevenue === 'MEDIUM') {
      score += 6;
    }
    if (airportHub) {
      score += 8;
    }
    if (hasContact) {
      score += 6;
    }
    if (company.website) {
      score += 4;
    }
    if (plan.recommended) {
      score += 3;
    }
    return Math.min(score, 98);
  }

  private buildPitch(
    company: Company,
    plan: AerosuitePlanOffer,
    painPoints: AerosuitePainPoint[],
    segment: string
  ): string {
    const name = company.tradeName || company.legalName;
    const pain = painPoints[0]?.label.toLowerCase() ?? 'processos fragmentados';
    if (segment.startsWith('MRO')) {
      return `${name}: unificar proposta → OS → estoque rastreável com Aero Suite (${plan.shortLabel}, R$ ${plan.monthlyPrice.toLocaleString('pt-BR')}/mês). Dor provável: ${pain}.`;
    }
    return `${name}: avaliar Aero Suite para digitalizar operação aeronáutica — ${plan.shortLabel} a partir de R$ ${plan.monthlyPrice.toLocaleString('pt-BR')}/mês.`;
  }

  private isAirportHub(company: Company): boolean {
    const blob = `${company.neighborhood ?? ''} ${company.city ?? ''} ${company.legalName ?? ''} ${company.tradeName ?? ''}`
      .toUpperCase();
    return AIRPORT_HINTS.some((hint) => blob.includes(hint));
  }

  private hasContactChannel(company: Company): boolean {
    const extended = company as Company & { email?: string; phone?: string };
    return Boolean(extended.email?.trim() || extended.phone?.trim());
  }

  private formatCapital(value: number): string {
    if (value >= 1_000_000) {
      return `R$ ${(value / 1_000_000).toFixed(1).replace('.', ',')} mi`;
    }
    return `R$ ${Math.round(value).toLocaleString('pt-BR')}`;
  }
}
