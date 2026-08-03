import { DatePipe } from '@angular/common';
import { Component, HostListener, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import {
  ApiService,
  AppointmentItem,
  ClientListItem,
  CreateAppointmentPayload,
  UpdateAppointmentPayload
} from '../../core/api.service';
import {
  AppointmentPrefill,
  consumeAppointmentPrefill
} from '../../core/appointment-prefill';
import { DateTimePickerComponent } from '../../shared/datetime-picker/datetime-picker.component';
import {
  brazilPhoneValidationMessage,
  formatBrazilPhoneDisplay,
  formatBrazilPhoneInput,
  normalizeBrazilPhone,
  phoneDigitsOnly,
  toWhatsAppPhone
} from '../../core/phone.util';

interface AppointmentGroup {
  label: string;
  items: AppointmentItem[];
}

const REMINDER_OPTIONS = [
  { value: 5, label: '5 minutos antes' },
  { value: 10, label: '10 minutos antes' },
  { value: 15, label: '15 minutos antes' },
  { value: 30, label: '30 minutos antes' },
  { value: 60, label: '1 hora antes' },
  { value: 120, label: '2 horas antes' },
  { value: 1440, label: '1 dia antes' }
];

@Component({
  selector: 'app-appointments',
  standalone: true,
  imports: [FormsModule, DatePipe, DateTimePickerComponent],
  templateUrl: './appointments.component.html',
  styleUrl: './appointments.component.scss'
})
export class AppointmentsComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly reminderOptions = REMINDER_OPTIONS;

  appointments: AppointmentItem[] = [];
  clients: ClientListItem[] = [];
  groups: AppointmentGroup[] = [];
  loading = true;
  saving = false;
  formError = '';
  showForm = false;
  editingId: string | null = null;
  viewMode: 'upcoming' | 'all' = 'upcoming';
  phoneError = '';
  prefillSource = '';
  loadingClientDetails = false;

  form = this.emptyForm();

  ngOnInit(): void {
    this.load();
    this.api.listClients().subscribe({
      next: (clients) => {
        this.clients = clients;
        this.applyIncomingPrefill();
      },
      error: () => this.applyIncomingPrefill()
    });
  }

  @HostListener('document:keydown', ['$event'])
  onDocumentKeydown(event: KeyboardEvent): void {
    if (!this.showForm) {
      return;
    }
    if (event.key === 'Escape') {
      event.preventDefault();
      this.closeForm();
      return;
    }
    if (event.key === 'Enter' && !event.shiftKey) {
      const target = event.target as HTMLElement | null;
      if (!target) {
        return;
      }
      const tag = target.tagName;
      if (tag === 'TEXTAREA' || tag === 'BUTTON' || tag === 'SELECT') {
        return;
      }
      if (target.isContentEditable) {
        return;
      }
      event.preventDefault();
      this.submitForm();
    }
  }

  load(): void {
    this.loading = true;
    this.api.listAppointments().subscribe({
      next: (items) => {
        this.appointments = items;
        this.rebuildGroups();
        this.loading = false;
      },
      error: () => {
        this.loading = false;
      }
    });
  }

  openCreate(): void {
    this.editingId = null;
    this.formError = '';
    this.phoneError = '';
    this.prefillSource = '';
    this.form = this.emptyForm();
    this.showForm = true;
  }

  openEdit(item: AppointmentItem): void {
    this.editingId = item.id;
    this.formError = '';
    this.phoneError = '';
    this.prefillSource = '';
    this.form = {
      clientId: item.clientId ?? '',
      clientName: item.clientName,
      clientEmail: item.clientEmail ?? '',
      clientPhone: formatBrazilPhoneDisplay(item.clientPhone),
      clientCompany: item.clientCompany ?? '',
      title: item.title,
      description: item.description ?? '',
      location: item.location ?? '',
      videoConference: item.videoConference,
      meetingUrl: item.meetingUrl ?? '',
      startsAt: item.startsAt,
      endsAt: item.endsAt ?? '',
      reminderMinutesBefore: item.reminderMinutesBefore
    };
    this.showForm = true;
  }

  closeForm(): void {
    this.showForm = false;
    this.formError = '';
    this.phoneError = '';
    this.prefillSource = '';
  }

  onPhoneChange(value: string): void {
    this.phoneError = '';
    this.form.clientPhone = formatBrazilPhoneInput(value);
  }

  onPhoneKeydown(event: KeyboardEvent): void {
    if (event.ctrlKey || event.metaKey || event.altKey) {
      return;
    }
    const allowed = ['Backspace', 'Delete', 'Tab', 'ArrowLeft', 'ArrowRight', 'Home', 'End', 'Enter'];
    if (allowed.includes(event.key)) {
      return;
    }
    if (!/^\d$/.test(event.key)) {
      event.preventDefault();
    }
  }

  onPhonePaste(event: ClipboardEvent): void {
    event.preventDefault();
    const pasted = event.clipboardData?.getData('text') ?? '';
    const combined = `${phoneDigitsOnly(this.form.clientPhone)}${phoneDigitsOnly(pasted)}`;
    this.onPhoneChange(combined);
  }

  onPhoneBlur(): void {
    this.phoneError = brazilPhoneValidationMessage(this.form.clientPhone) ?? '';
    if (!this.phoneError && this.form.clientPhone.trim()) {
      this.form.clientPhone = formatBrazilPhoneDisplay(this.form.clientPhone);
    }
  }

  formatPhone(value?: string): string {
    return formatBrazilPhoneDisplay(value);
  }

  onClientSelected(): void {
    if (!this.form.clientId) {
      return;
    }
    const client = this.clients.find((c) => c.id === this.form.clientId);
    if (!client) {
      return;
    }
    this.form.clientName = client.displayName;
    this.form.clientCompany = client.displayName;
    this.prefillSource = 'Carteira';
    this.loadingClientDetails = true;
    this.api.getClient(client.id).subscribe({
      next: (detail) => {
        this.form.clientName = detail.tradeName || detail.legalName || detail.displayName;
        this.form.clientCompany = detail.legalName || detail.displayName;
        this.form.clientEmail = detail.email ?? this.form.clientEmail;
        this.form.clientPhone = formatBrazilPhoneDisplay(detail.phone ?? this.form.clientPhone);
        if (!this.form.title.trim()) {
          this.form.title = `Reunião comercial · ${this.form.clientCompany}`;
        }
        this.loadingClientDetails = false;
      },
      error: () => {
        this.loadingClientDetails = false;
      }
    });
  }

  submitForm(): void {
    this.formError = '';
    const startsAt = this.form.startsAt;
    if (!startsAt) {
      this.formError = 'Informe data e hora do compromisso.';
      return;
    }
    if (!this.form.clientId && !this.form.clientName.trim()) {
      this.formError = 'Informe o nome do cliente ou selecione um da carteira.';
      return;
    }
    if (!this.form.title.trim()) {
      this.formError = 'Informe o título do compromisso.';
      return;
    }
    if (this.form.endsAt && new Date(this.form.endsAt).getTime() <= new Date(startsAt).getTime()) {
      this.formError = 'O término deve ser posterior ao início.';
      return;
    }

    const phoneMessage = brazilPhoneValidationMessage(this.form.clientPhone);
    if (phoneMessage) {
      this.phoneError = phoneMessage;
      this.formError = phoneMessage;
      return;
    }

    const normalizedPhone = normalizeBrazilPhone(this.form.clientPhone);

    const payload: CreateAppointmentPayload = {
      clientId: this.form.clientId || undefined,
      clientName: this.form.clientName.trim(),
      clientEmail: this.form.clientEmail.trim() || undefined,
      clientPhone: normalizedPhone || undefined,
      clientCompany: this.form.clientCompany.trim() || undefined,
      title: this.form.title.trim(),
      description: this.form.description.trim() || undefined,
      location: this.form.videoConference ? undefined : (this.form.location.trim() || undefined),
      videoConference: this.form.videoConference,
      meetingUrl: this.form.videoConference ? (this.form.meetingUrl.trim() || undefined) : undefined,
      startsAt,
      endsAt: this.form.endsAt || undefined,
      reminderMinutesBefore: Number(this.form.reminderMinutesBefore)
    };

    this.saving = true;
    const request = this.editingId
      ? this.api.updateAppointment(this.editingId, payload as UpdateAppointmentPayload)
      : this.api.createAppointment(payload);

    request.subscribe({
      next: () => {
        this.saving = false;
        this.closeForm();
        this.load();
      },
      error: (err) => {
        this.saving = false;
        this.formError = err?.error?.message || err?.error || 'Não foi possível salvar o compromisso.';
      }
    });
  }

  cancelAppointment(item: AppointmentItem): void {
    if (!confirm(`Cancelar o compromisso "${item.title}"?`)) {
      return;
    }
    this.api.cancelAppointment(item.id).subscribe({
      next: () => this.load()
    });
  }

  setView(mode: 'upcoming' | 'all'): void {
    this.viewMode = mode;
    this.rebuildGroups();
  }

  reminderLabel(minutes: number): string {
    return REMINDER_OPTIONS.find((o) => o.value === minutes)?.label ?? `${minutes} min antes`;
  }

  statusLabel(status: string): string {
    const map: Record<string, string> = {
      SCHEDULED: 'Agendado',
      COMPLETED: 'Concluído',
      CANCELLED: 'Cancelado'
    };
    return map[status] ?? status;
  }

  isPast(item: AppointmentItem): boolean {
    return new Date(item.startsAt).getTime() < Date.now();
  }

  isAutoJitsi(url?: string): boolean {
    return !!url && url.includes('meet.jit.si/');
  }

  meetingHint(url?: string): string {
    if (!url) {
      return 'Uma sala Jitsi Meet será gerada ao salvar. Google Calendar / Outlook ainda não estão conectados.';
    }
    return this.isAutoJitsi(url) ? 'Sala Jitsi Meet gerada automaticamente' : 'Link personalizado';
  }

  async copyMeetingLink(url: string, event: Event): Promise<void> {
    event.preventDefault();
    event.stopPropagation();
    try {
      await navigator.clipboard.writeText(url);
    } catch {
      // clipboard may fail on insecure contexts
    }
  }

  whatsAppShareUrl(item: AppointmentItem): string {
    const when = new Date(item.startsAt).toLocaleString('pt-BR');
    const lines = [
      `Olá! Segue o convite para nossa reunião "${item.title}".`,
      `Data: ${when}`,
      item.meetingUrl ? `Link da videoconferência: ${item.meetingUrl}` : ''
    ].filter(Boolean);
    const phone = toWhatsAppPhone(item.clientPhone);
    const text = encodeURIComponent(lines.join('\n'));
    return phone ? `https://wa.me/${phone}?text=${text}` : `https://wa.me/?text=${text}`;
  }

  private applyIncomingPrefill(): void {
    const fromStorage = consumeAppointmentPrefill();
    const qp = this.route.snapshot.queryParamMap;
    const fromQuery: AppointmentPrefill = {
      clientId: qp.get('clientId') || undefined,
      clientName: qp.get('name') || undefined,
      clientCompany: qp.get('company') || undefined,
      clientEmail: qp.get('email') || undefined,
      clientPhone: qp.get('phone') || undefined,
      title: qp.get('title') || undefined,
      description: qp.get('description') || undefined,
      openForm: qp.get('new') === '1' || qp.keys.length > 0
    };

    const hasQuery =
      !!fromQuery.clientId ||
      !!fromQuery.clientName ||
      !!fromQuery.clientCompany ||
      !!fromQuery.clientEmail ||
      !!fromQuery.clientPhone;

    const prefill = fromStorage ?? (hasQuery ? fromQuery : null);
    if (!prefill) {
      return;
    }

    this.applyPrefill(prefill);
    if (hasQuery) {
      void this.router.navigate([], {
        relativeTo: this.route,
        queryParams: {},
        replaceUrl: true
      });
    }
  }

  private applyPrefill(prefill: AppointmentPrefill): void {
    this.editingId = null;
    this.formError = '';
    this.phoneError = '';
    this.form = this.emptyForm();

    let matchedClient = prefill.clientId
      ? this.clients.find((c) => c.id === prefill.clientId)
      : undefined;

    if (!matchedClient && prefill.clientCompany) {
      const needle = prefill.clientCompany.trim().toLowerCase();
      matchedClient = this.clients.find(
        (c) =>
          c.displayName.toLowerCase() === needle ||
          c.document.replace(/\D/g, '') === needle.replace(/\D/g, '')
      );
    }

    if (matchedClient) {
      this.form.clientId = matchedClient.id;
      this.prefillSource = 'Carteira';
      this.onClientSelected();
      if (prefill.title) {
        this.form.title = prefill.title;
      }
      if (prefill.description) {
        this.form.description = prefill.description;
      }
    } else {
      this.prefillSource = 'Lead / prospecção';
      if (prefill.clientName) {
        this.form.clientName = prefill.clientName;
      }
      if (prefill.clientCompany) {
        this.form.clientCompany = prefill.clientCompany;
      }
      if (prefill.clientEmail) {
        this.form.clientEmail = prefill.clientEmail;
      }
      if (prefill.clientPhone) {
        this.form.clientPhone = formatBrazilPhoneDisplay(prefill.clientPhone);
      }
      if (prefill.title) {
        this.form.title = prefill.title;
      } else if (prefill.clientCompany || prefill.clientName) {
        this.form.title = `Reunião comercial · ${prefill.clientCompany || prefill.clientName}`;
      }
      if (prefill.description) {
        this.form.description = prefill.description;
      }
    }

    this.showForm = prefill.openForm !== false;
  }

  private rebuildGroups(): void {
    const now = Date.now();
    const filtered = this.viewMode === 'upcoming'
      ? this.appointments.filter((a) => a.status === 'SCHEDULED' && new Date(a.startsAt).getTime() >= now - 60_000)
      : this.appointments;

    const sorted = [...filtered].sort(
      (a, b) => new Date(a.startsAt).getTime() - new Date(b.startsAt).getTime()
    );

    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const tomorrow = new Date(today);
    tomorrow.setDate(tomorrow.getDate() + 1);

    const buckets = new Map<string, AppointmentItem[]>();
    for (const item of sorted) {
      const date = new Date(item.startsAt);
      date.setHours(0, 0, 0, 0);
      let label: string;
      if (date.getTime() === today.getTime()) {
        label = 'Hoje';
      } else if (date.getTime() === tomorrow.getTime()) {
        label = 'Amanhã';
      } else {
        label = date.toLocaleDateString('pt-BR', { weekday: 'long', day: '2-digit', month: 'long' });
      }
      if (!buckets.has(label)) {
        buckets.set(label, []);
      }
      buckets.get(label)!.push(item);
    }

    this.groups = Array.from(buckets.entries()).map(([label, items]) => ({ label, items }));
  }

  private emptyForm() {
    const defaultStart = new Date();
    defaultStart.setMinutes(defaultStart.getMinutes() + 60 - (defaultStart.getMinutes() % 15));
    defaultStart.setSeconds(0, 0);
    return {
      clientId: '',
      clientName: '',
      clientEmail: '',
      clientPhone: '',
      clientCompany: '',
      title: '',
      description: '',
      location: '',
      videoConference: false,
      meetingUrl: '',
      startsAt: defaultStart.toISOString(),
      endsAt: '',
      reminderMinutesBefore: 30
    };
  }
}
