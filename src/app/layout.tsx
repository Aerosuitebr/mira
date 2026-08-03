import type { Metadata } from 'next';
import './globals.css';

export const metadata: Metadata = {
  title: 'MIRA — Busca B2B | Aerosuite',
  description: 'Encontre empresas para prospectar ou profissionais próximos do local do serviço.',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="pt-BR">
      <body>{children}</body>
    </html>
  );
}
