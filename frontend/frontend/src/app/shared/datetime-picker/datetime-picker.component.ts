import { Component, Input, forwardRef } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

interface CalendarDay {
  date: Date;
  inMonth: boolean;
  isToday: boolean;
  isSelected: boolean;
  isPast: boolean;
}

interface TimeSlot {
  value: string;
  label: string;
}

@Component({
  selector: 'app-datetime-picker',
  standalone: true,
  templateUrl: './datetime-picker.component.html',
  styleUrl: './datetime-picker.component.scss',
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => DateTimePickerComponent),
      multi: true
    }
  ]
})
export class DateTimePickerComponent implements ControlValueAccessor {
  @Input() label = 'Data e hora';
  @Input() optional = false;
  @Input() minIso: string | null = null;

  open = false;
  disabled = false;

  selectedDate: Date | null = null;
  selectedTime = '';

  viewYear = new Date().getFullYear();
  viewMonth = new Date().getMonth();

  readonly weekDays = ['Dom', 'Seg', 'Ter', 'Qua', 'Qui', 'Sex', 'Sáb'];
  readonly monthNames = [
    'Janeiro', 'Fevereiro', 'Março', 'Abril', 'Maio', 'Junho',
    'Julho', 'Agosto', 'Setembro', 'Outubro', 'Novembro', 'Dezembro'
  ];

  readonly timeSlots: TimeSlot[] = this.buildTimeSlots();

  private onChange: (value: string | null) => void = () => undefined;
  private onTouched: () => void = () => undefined;

  get summary(): string {
    if (!this.selectedDate || !this.selectedTime) {
      return this.optional ? 'Toque para escolher (opcional)' : 'Toque para escolher data e hora';
    }
    const [h, m] = this.selectedTime.split(':').map(Number);
    const d = new Date(this.selectedDate);
    d.setHours(h, m, 0, 0);
    return d.toLocaleString('pt-BR', {
      weekday: 'short',
      day: '2-digit',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  }

  get monthLabel(): string {
    return `${this.monthNames[this.viewMonth]} ${this.viewYear}`;
  }

  get calendarWeeks(): CalendarDay[][] {
    const weeks: CalendarDay[][] = [];
    const first = new Date(this.viewYear, this.viewMonth, 1);
    const startOffset = first.getDay();
    const gridStart = new Date(this.viewYear, this.viewMonth, 1 - startOffset);

    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const minDay = this.minDateStart();

    for (let w = 0; w < 6; w++) {
      const week: CalendarDay[] = [];
      for (let d = 0; d < 7; d++) {
        const idx = w * 7 + d;
        const date = new Date(gridStart);
        date.setDate(gridStart.getDate() + idx);
        date.setHours(0, 0, 0, 0);

        const isSelected = !!this.selectedDate
          && date.getTime() === this.stripTime(this.selectedDate).getTime();

        week.push({
          date,
          inMonth: date.getMonth() === this.viewMonth,
          isToday: date.getTime() === today.getTime(),
          isSelected,
          isPast: minDay != null && date.getTime() < minDay.getTime()
        });
      }
      weeks.push(week);
    }
    return weeks;
  }

  writeValue(value: string | null): void {
    if (!value) {
      this.selectedDate = null;
      this.selectedTime = '';
      return;
    }
    const parsed = new Date(value);
    if (Number.isNaN(parsed.getTime())) {
      return;
    }
    this.selectedDate = this.stripTime(parsed);
    this.selectedTime = this.formatTime(parsed);
    this.viewYear = parsed.getFullYear();
    this.viewMonth = parsed.getMonth();
  }

  registerOnChange(fn: (value: string | null) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
  }

  togglePanel(): void {
    if (this.disabled) {
      return;
    }
    this.open = !this.open;
    this.onTouched();
    if (this.open && !this.selectedDate) {
      const base = this.minDateStart() ?? new Date();
      this.viewYear = base.getFullYear();
      this.viewMonth = base.getMonth();
    }
  }

  prevMonth(): void {
    if (this.viewMonth === 0) {
      this.viewMonth = 11;
      this.viewYear -= 1;
    } else {
      this.viewMonth -= 1;
    }
  }

  nextMonth(): void {
    if (this.viewMonth === 11) {
      this.viewMonth = 0;
      this.viewYear += 1;
    } else {
      this.viewMonth += 1;
    }
  }

  selectDay(day: CalendarDay): void {
    if (day.isPast || !day.inMonth) {
      return;
    }
    this.selectedDate = new Date(day.date);
    if (!this.selectedTime) {
      this.selectedTime = this.nearestSlot(new Date());
    }
    this.emitValue();
  }

  selectTime(slot: string): void {
    this.selectedTime = slot;
    if (!this.selectedDate) {
      this.selectedDate = this.minDateStart() ?? this.stripTime(new Date());
    }
    this.emitValue();
  }

  applyPreset(preset: 'plus1h' | 'tomorrow9' | 'plus2h'): void {
    const now = new Date();
    let target = new Date(now);

    if (preset === 'plus1h') {
      target.setMinutes(now.getMinutes() + 60, 0, 0);
    } else if (preset === 'plus2h') {
      target.setMinutes(now.getMinutes() + 120, 0, 0);
    } else {
      target.setDate(now.getDate() + 1);
      target.setHours(9, 0, 0, 0);
    }

    const min = this.minDateTime();
    if (min && target.getTime() < min.getTime()) {
      target = min;
    }

    this.selectedDate = this.stripTime(target);
    this.selectedTime = this.formatTime(target);
    this.viewYear = target.getFullYear();
    this.viewMonth = target.getMonth();
    this.open = true;
    this.emitValue();
  }

  clearValue(event: Event): void {
    event.stopPropagation();
    if (!this.optional) {
      return;
    }
    this.selectedDate = null;
    this.selectedTime = '';
    this.onChange(null);
    this.onTouched();
  }

  private emitValue(): void {
    if (!this.selectedDate || !this.selectedTime) {
      this.onChange(null);
      return;
    }
    const [h, m] = this.selectedTime.split(':').map(Number);
    const combined = new Date(this.selectedDate);
    combined.setHours(h, m, 0, 0);

    const min = this.minDateTime();
    if (min && combined.getTime() < min.getTime()) {
      return;
    }

    this.onChange(combined.toISOString());
  }

  private minDateStart(): Date | null {
    if (!this.minIso) {
      return null;
    }
    const min = new Date(this.minIso);
    return Number.isNaN(min.getTime()) ? null : this.stripTime(min);
  }

  private minDateTime(): Date | null {
    if (!this.minIso) {
      return null;
    }
    const min = new Date(this.minIso);
    return Number.isNaN(min.getTime()) ? null : min;
  }

  private stripTime(date: Date): Date {
    const d = new Date(date);
    d.setHours(0, 0, 0, 0);
    return d;
  }

  private formatTime(date: Date): string {
    const pad = (n: number) => `${n}`.padStart(2, '0');
    const mins = date.getMinutes();
    const rounded = Math.round(mins / 15) * 15;
    const d = new Date(date);
    d.setMinutes(rounded % 60, 0, 0);
    if (rounded >= 60) {
      d.setHours(d.getHours() + 1);
    }
    return `${pad(d.getHours())}:${pad(d.getMinutes())}`;
  }

  private nearestSlot(date: Date): string {
    return this.formatTime(date);
  }

  private buildTimeSlots(): TimeSlot[] {
    const slots: TimeSlot[] = [];
    for (let h = 7; h <= 21; h++) {
      for (let m = 0; m < 60; m += 15) {
        const value = `${`${h}`.padStart(2, '0')}:${`${m}`.padStart(2, '0')}`;
        slots.push({ value, label: value });
      }
    }
    return slots;
  }
}
