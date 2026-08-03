import { Injectable, OnDestroy, inject } from '@angular/core';
import { ApiService, AlertItem } from './api.service';
import { AuthService } from './auth.service';

@Injectable({ providedIn: 'root' })
export class AppointmentNotificationService implements OnDestroy {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);

  private pollTimer: ReturnType<typeof setInterval> | null = null;
  private knownAlertIds = new Set<string>();
  private initialized = false;

  start(): void {
    if (this.initialized || !this.auth.session()) {
      return;
    }
    this.initialized = true;
    this.requestPermission();
    this.poll();
    this.pollTimer = setInterval(() => this.poll(), 60_000);
  }

  stop(): void {
    if (this.pollTimer) {
      clearInterval(this.pollTimer);
      this.pollTimer = null;
    }
    this.initialized = false;
    this.knownAlertIds.clear();
  }

  ngOnDestroy(): void {
    this.stop();
  }

  private requestPermission(): void {
    if (typeof Notification === 'undefined' || Notification.permission !== 'default') {
      return;
    }
    void Notification.requestPermission();
  }

  private poll(): void {
    this.api.alerts().subscribe({
      next: (alerts) => this.processAlerts(alerts)
    });
  }

  private processAlerts(alerts: AlertItem[]): void {
    const appointmentAlerts = alerts.filter((a) => a.alertType === 'APPOINTMENT_REMINDER');

    if (this.knownAlertIds.size === 0) {
      appointmentAlerts.forEach((a) => this.knownAlertIds.add(a.id));
      return;
    }

    for (const alert of appointmentAlerts) {
      if (this.knownAlertIds.has(alert.id) || alert.read) {
        continue;
      }
      this.knownAlertIds.add(alert.id);
      this.showBrowserNotification(alert);
    }
  }

  private showBrowserNotification(alert: AlertItem): void {
    if (typeof Notification === 'undefined' || Notification.permission !== 'granted') {
      return;
    }
    new Notification(alert.title, {
      body: alert.description,
      tag: alert.id,
      icon: '/favicon.ico'
    });
  }
}
