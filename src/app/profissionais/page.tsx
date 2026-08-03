import Link from 'next/link';

export default function ProfissionaisPage() {
  return (
    <main className="wrap">
      <Link href="/escolher-busca" className="muted" style={{ fontSize: 14 }}>← Voltar</Link>
      <h1 style={{ marginTop: '1rem' }}>Busca de profissionais</h1>
      <p className="muted" style={{ maxWidth: '40rem', lineHeight: 1.6 }}>
        Em reconstrução. Aqui entrará a busca por proximidade geográfica.
      </p>
    </main>
  );
}
