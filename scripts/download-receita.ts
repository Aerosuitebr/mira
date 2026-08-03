/**
 * Placeholder: baixa os ZIPs mensais da Receita Federal (dados abertos CNPJ).
 * Fonte: https://arquivos.receitafederal.gov.br/dados/cnpj/dados_abertos_cnpj/
 *
 * Uso: npm run ingest:receita:download
 */
import { mkdir } from 'node:fs/promises';
import path from 'node:path';

const outDir = process.env.RECEITA_DATA_DIR || '.data/receita';

async function main() {
  await mkdir(outDir, { recursive: true });
  console.log(`[mira] Pasta pronta: ${path.resolve(outDir)}`);
  console.log('[mira] Próximo passo: listar a pasta mensal mais recente na RFB e baixar Empresas*/Estabelecimentos*/Socios*.zip');
  console.log('[mira] Doc: docs/RECEITA-FEDERAL.md');
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
