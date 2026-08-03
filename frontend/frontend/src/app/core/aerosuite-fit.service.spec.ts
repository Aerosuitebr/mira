import { Company } from './api.service';
import { AerosuiteFitService } from './aerosuite-fit.service';

describe('AerosuiteFitService', () => {
  const service = new AerosuiteFitService();

  function company(overrides: Partial<Company> = {}): Company {
    return {
      id: 'company-1',
      cnpj: '00000000000100',
      legalName: 'Oficina de Teste Ltda',
      tradeName: 'Oficina de Teste',
      cnaeMain: '3316301',
      cnaeDescription: 'Manutenção e reparação de aeronaves',
      city: 'Rio de Janeiro',
      state: 'RJ',
      neighborhood: 'Galeão',
      capitalSocial: 1_200_000,
      openedAt: '2015-01-01',
      estimatedRevenue: 'MEDIUM',
      website: 'https://example.com',
      email: 'contato@example.com',
      latitude: -22.8,
      longitude: -43.2,
      ...overrides
    };
  }

  it('classifica uma oficina aeronáutica como ICP MRO', () => {
    const result = service.analyze(company());

    expect(result.icpMatch).toBeTrue();
    expect(result.icpSegment).toBe('MRO_HANGAR');
    expect(result.fitScore).toBeGreaterThanOrEqual(78);
    expect(result.readiness).toBe('alta');
  });

  it('usa CNAE secundário MRO quando o CNAE principal não pertence ao segmento', () => {
    const result = service.analyze(company({
      cnaeMain: '6201501',
      cnaeSecondary: '3316302, 6202300',
      neighborhood: 'Centro',
      capitalSocial: 150_000,
      estimatedRevenue: 'SMALL'
    }));

    expect(result.icpMatch).toBeTrue();
    expect(result.icpSegment).toBe('MRO_PISTA');
    expect(result.plan.id).toBe('PLANO_I');
    expect(result.trialEligible).toBeTrue();
  });

  it('mantém empresas sem sinal aeronáutico fora do ICP', () => {
    const result = service.analyze(company({
      cnaeMain: '6201501',
      cnaeDescription: 'Desenvolvimento de programas de computador',
      neighborhood: 'Centro',
      tradeName: 'Software de Teste'
    }));

    expect(result.icpMatch).toBeFalse();
    expect(result.icpSegment).toBe('FORA_ICP');
    expect(result.fitScore).toBe(28);
  });
});
