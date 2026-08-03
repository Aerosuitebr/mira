import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

export type BrandMarkSize = 'sidebar' | 'hero' | 'panel' | 'compact';

@Component({
  selector: 'app-brand-mark',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './brand-mark.component.html',
  styleUrl: './brand-mark.component.scss',
})
export class BrandMarkComponent {
  readonly size = input<BrandMarkSize>('sidebar');
  readonly tagline = input('');
  readonly link = input<string | null>('/dashboard');
}
