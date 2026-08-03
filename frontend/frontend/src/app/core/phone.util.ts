const VALID_DDD = new Set([
  11, 12, 13, 14, 15, 16, 17, 18, 19,
  21, 22, 24, 27, 28,
  31, 32, 33, 34, 35, 37, 38,
  41, 42, 43, 44, 45, 46, 47, 48, 49,
  51, 53, 54, 55,
  61, 62, 63, 64, 65, 66, 67, 68, 69,
  71, 73, 74, 75, 77, 79,
  81, 82, 83, 84, 85, 86, 87, 88, 89,
  91, 92, 93, 94, 95, 96, 97, 98, 99
]);

export function phoneDigitsOnly(value: string | null | undefined): string {
  return (value ?? '').replace(/\D/g, '');
}

/** Extrai DDD + número nacional (10 ou 11 dígitos). */
export function nationalPhoneDigits(value: string | null | undefined): string {
  let digits = phoneDigitsOnly(value);
  if (digits.startsWith('55') && digits.length > 11) {
    digits = digits.slice(2);
  }
  return digits.slice(0, 11);
}

export function formatBrazilPhoneInput(value: string | null | undefined): string {
  const digits = nationalPhoneDigits(value);
  if (!digits) {
    return '';
  }

  if (digits.length <= 2) {
    return `(${digits}`;
  }

  const ddd = digits.slice(0, 2);
  const rest = digits.slice(2);

  if (digits.length <= 6) {
    return `(${ddd}) ${rest}`;
  }

  if (digits.length <= 10) {
    return `(${ddd}) ${rest.slice(0, 4)}-${rest.slice(4)}`;
  }

  return `(${ddd}) ${rest.slice(0, 5)}-${rest.slice(5, 9)}`;
}

export function formatBrazilPhoneDisplay(value: string | null | undefined): string {
  const digits = nationalPhoneDigits(value);
  if (!digits) {
    return '';
  }
  return formatBrazilPhoneInput(digits);
}

export function isValidBrazilPhone(value: string | null | undefined): boolean {
  const digits = nationalPhoneDigits(value);
  if (digits.length !== 10 && digits.length !== 11) {
    return false;
  }

  const ddd = Number(digits.slice(0, 2));
  if (!VALID_DDD.has(ddd)) {
    return false;
  }

  if (digits.length === 11) {
    return digits.charAt(2) === '9';
  }

  const first = digits.charAt(2);
  return first >= '2' && first <= '5';
}

export function normalizeBrazilPhone(value: string | null | undefined): string | null {
  const trimmed = (value ?? '').trim();
  if (!trimmed) {
    return null;
  }
  if (!isValidBrazilPhone(trimmed)) {
    return null;
  }
  return formatBrazilPhoneDisplay(trimmed);
}

/** Número no formato internacional para WhatsApp (55 + DDD + número). */
export function toWhatsAppPhone(value: string | null | undefined): string {
  const digits = nationalPhoneDigits(value);
  if (!isValidBrazilPhone(digits)) {
    return '';
  }
  return `55${digits}`;
}

export function brazilPhoneValidationMessage(value: string | null | undefined): string | null {
  const trimmed = (value ?? '').trim();
  if (!trimmed) {
    return null;
  }
  const digits = nationalPhoneDigits(trimmed);
  if (digits.length < 10) {
    return 'Informe o DDD e o número completo.';
  }
  if (!isValidBrazilPhone(trimmed)) {
    return 'Telefone inválido. Use DDD + celular (9 dígitos) ou fixo (8 dígitos).';
  }
  return null;
}
