/** Máscara financeira pt-BR: digitos viram centavos (4500 → 45,00). */

export function digitsOnly(value: string): string {
  return (value || '').replace(/\D/g, '');
}

export function moneyFromDigits(digits: string): number {
  const clean = digitsOnly(digits);
  if (!clean) {
    return 0;
  }
  return Number(clean) / 100;
}

export function formatBrMoney(value: number | null | undefined): string {
  const amount = Number(value ?? 0);
  if (!Number.isFinite(amount)) {
    return '0,00';
  }
  return amount.toLocaleString('pt-BR', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  });
}
