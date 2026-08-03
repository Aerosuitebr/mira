export interface AppointmentPrefill {
  clientId?: string;
  clientName?: string;
  clientCompany?: string;
  clientEmail?: string;
  clientPhone?: string;
  title?: string;
  description?: string;
  openForm?: boolean;
}

const STORAGE_KEY = 'mira-appointment-prefill';

export function stashAppointmentPrefill(data: AppointmentPrefill): void {
  sessionStorage.setItem(
    STORAGE_KEY,
    JSON.stringify({
      ...data,
      openForm: true
    })
  );
}

export function consumeAppointmentPrefill(): AppointmentPrefill | null {
  const raw = sessionStorage.getItem(STORAGE_KEY);
  if (!raw) {
    return null;
  }
  sessionStorage.removeItem(STORAGE_KEY);
  try {
    return JSON.parse(raw) as AppointmentPrefill;
  } catch {
    return null;
  }
}
