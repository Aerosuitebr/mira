import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService, OutreachSettings } from '../../core/api.service';

@Component({
  selector: 'app-outreach-settings',
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './outreach-settings.component.html',
  styleUrl: './outreach-settings.component.scss'
})
export class OutreachSettingsComponent implements OnInit {
  private readonly api = inject(ApiService);

  senderName = '';
  previewUrl: string | null = null;
  brandImageBase64: string | null = null;
  brandImageMime: string | null = null;
  brandImageFileName: string | null = null;
  clearBrandImage = false;

  loading = true;
  saving = false;
  message = '';
  error = '';

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error = '';
    this.api.getOutreachSettings().subscribe({
      next: (settings) => {
        this.applySettings(settings);
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        this.error = err?.error?.message || 'Não foi possível carregar as configurações de envio.';
      }
    });
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) {
      return;
    }
    if (!file.type.startsWith('image/')) {
      this.error = 'Selecione uma imagem PNG, JPEG ou WebP.';
      return;
    }
    if (file.size > 900_000) {
      this.error = 'Imagem muito grande. Use até cerca de 900 KB.';
      return;
    }

    const reader = new FileReader();
    reader.onload = () => {
      const result = String(reader.result || '');
      const comma = result.indexOf(',');
      const raw = comma > 0 ? result.slice(comma + 1) : result;
      this.brandImageBase64 = raw;
      this.brandImageMime = file.type === 'image/jpg' ? 'image/jpeg' : file.type;
      this.brandImageFileName = file.name;
      this.previewUrl = result.startsWith('data:') ? result : `data:${this.brandImageMime};base64,${raw}`;
      this.clearBrandImage = false;
      this.error = '';
    };
    reader.onerror = () => {
      this.error = 'Falha ao ler a imagem.';
    };
    reader.readAsDataURL(file);
  }

  removeImage(): void {
    this.previewUrl = null;
    this.brandImageBase64 = null;
    this.brandImageMime = null;
    this.brandImageFileName = null;
    this.clearBrandImage = true;
  }

  save(): void {
    this.saving = true;
    this.message = '';
    this.error = '';
    this.api
      .updateOutreachSettings({
        senderName: this.senderName.trim(),
        brandImageBase64: this.clearBrandImage ? null : this.brandImageBase64,
        brandImageMime: this.clearBrandImage ? null : this.brandImageMime,
        brandImageFileName: this.clearBrandImage ? null : this.brandImageFileName,
        clearBrandImage: this.clearBrandImage
      })
      .subscribe({
        next: (settings) => {
          this.applySettings(settings);
          this.saving = false;
          this.message = 'Configurações de envio salvas. Os próximos disparos usam este remetente e imagem.';
        },
        error: (err) => {
          this.saving = false;
          this.error = err?.error?.message || 'Não foi possível salvar.';
        }
      });
  }

  private applySettings(settings: OutreachSettings): void {
    this.senderName = settings.senderName || '';
    this.clearBrandImage = false;
    if (settings.hasBrandImage && settings.brandImageBase64) {
      const mime = settings.brandImageMime || 'image/png';
      this.brandImageBase64 = settings.brandImageBase64;
      this.brandImageMime = mime;
      this.brandImageFileName = settings.brandImageFileName;
      this.previewUrl = settings.brandImageBase64.startsWith('data:')
        ? settings.brandImageBase64
        : `data:${mime};base64,${settings.brandImageBase64}`;
    } else {
      this.brandImageBase64 = null;
      this.brandImageMime = null;
      this.brandImageFileName = null;
      this.previewUrl = null;
    }
  }
}
