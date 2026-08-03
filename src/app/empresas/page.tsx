import Link from 'next/link';

export default function EmpresasPage() {
  return (
    <main className="wrap">
      <Link href="/escolher-busca" className="muted" style={{ fontSize: 14 }}>← Voltar</Link>
      <h1 style={{ marginTop: '1rem' }}>Busca de empresas</h1>
      <p className="muted" style={{ maxWidth: '40rem', lineHeight: 1.6 }}>
        Em reconstrução. Aqui entrará a busca sobre o índice CNPJ (Receita Federal) via Meilisearch/Postgres.
      </p>
      <div className="card" style={{ marginTop: '1.5rem' }}>
        <p style={{ margin: 0 }}>Próximos passos: `npm run ingest:receita:download` → `npm run ingest:receita` → API `/api/search/empresas`.</p>
      </div>
    </main>
  );
}
