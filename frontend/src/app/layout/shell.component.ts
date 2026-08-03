import { Component, HostListener, OnDestroy, OnInit, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AppointmentNotificationService } from '../core/appointment-notification.service';
import { AuthService } from '../core/auth.service';
import { ThemeService } from '../core/theme.service';

import { BrandMarkComponent } from '../shared/brand-mark/brand-mark.component';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, BrandMarkComponent],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss'
})
export class ShellComponent implements OnInit, OnDestroy {
  private static readonly selectionSessionVersion = 'real-data-v1';
  private readonly ringCircumference = 213.6;
  private readonly notifications = inject(AppointmentNotificationService);
  mobileMenuOpen = false;

  constructor(
    readonly auth: AuthService,
    readonly theme: ThemeService
  ) {}

  ngOnInit(): void {
    this.resetLegacySelectionSession();
    this.notifications.start();
  }

  ngOnDestroy(): void {
    this.notifications.stop();
  }

  creditOffset(remaining: number, monthly: number): number {
    const total = Math.max(monthly, 1);
    const ratio = Math.min(Math.max(remaining, 0) / total, 1);
    return this.ringCircumference * (1 - ratio);
  }

  creditUsagePercent(remaining: number, monthly: number): number {
    const total = Math.max(monthly, 1);
    return Math.round((Math.max(remaining, 0) / total) * 100);
  }

  toggleMobileMenu(): void {
    this.mobileMenuOpen = !this.mobileMenuOpen;
  }

  closeMobileMenu(): void {
    this.mobileMenuOpen = false;
  }

  @HostListener('document:keydown.escape')
  closeMobileMenuWithEscape(): void {
    this.closeMobileMenu();
  }

  private resetLegacySelectionSession(): void {
    const versionKey = 'selected-companies-version';
    if (sessionStorage.getItem(versionKey) !== ShellComponent.selectionSessionVersion) {
      sessionStorage.removeItem('selected-companies');
      sessionStorage.removeItem('selected-companies-cache');
      sessionStorage.setItem(versionKey, ShellComponent.selectionSessionVersion);
    }
  }
}


