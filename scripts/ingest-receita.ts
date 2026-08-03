/**
 * Placeholder: importa CSVs da Receita para Postgres e indexa no Meilisearch.
 * Uso: npm run ingest:receita
 */
async function main() {
  console.log('[mira] Ingestão ainda não implementada neste scaffold.');
  console.log('[mira] Quando a base for reconstruída, preferir dump + backup em object storage (S3/R2).');
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
