import Link from 'next/link';

type Props = { searchParams: Promise<{ origem?: string }> };

export default async function EscolherBuscaPage({ searchParams }: Props) {
  const params = await searchParams;
  const origem = params.origem || 'direto';
  const q = origem ? `?origem=${encodeURIComponent(origem)}` : '';

  return (
    <main className="wrap">
      <p className="muted" style={{ fontSize: 12, letterSpacing: '0.16em', textTransform: 'uppercase', fontWeight: 800 }}>
        MIRA · Aerosuite
      </p>
      <h1 style={{ fontSize: 'clamp(2rem, 5vw, 3rem)', lineHeight: 1.1, margin: '0.75rem 0 0.5rem' }}>
        Como você quer buscar?
      </h1>
      <p className="muted" style={{ maxWidth: '36rem', lineHeight: 1.6 }}>
        Inteligência B2B para prospectar empresas ou encontrar profissionais perto do local do serviço.
        {origem !== 'direto' ? <> Origem: <strong>{origem}</strong>.</> : null}
      </p>

      <div className="grid two" style={{ marginTop: '2rem' }}>
        <Link className="card" href={`/empresas${q}`}>
          <h2 style={{ margin: '0 0 0.5rem' }}>Quero encontrar empresas</h2>
          <p className="muted" style={{ margin: 0, lineHeight: 1.55 }}>
            Busque negócios por atividade (CNAE), região e potencial comercial — base CNPJ da Receita Federal.
          </p>
        </Link>
        <Link className="card" href={`/profissionais${q}`}>
          <h2 style={{ margin: '0 0 0.5rem' }}>Preciso de um profissional</h2>
          <p className="muted" style={{ margin: 0, lineHeight: 1.55 }}>
            Veja profissionais próximos e a distância até o serviço.
          </p>
        </Link>
      </div>

      <p className="muted" style={{ marginTop: '2rem', fontSize: 13 }}>
        Hub Resolva Jato:{' '}
        <a href={process.env.NEXT_PUBLIC_HUB_URL || 'https://resolvajato.com.br'} style={{ color: 'var(--accent)' }}>
          resolvajato.com.br
        </a>
      </p>
    </main>
  );
}
