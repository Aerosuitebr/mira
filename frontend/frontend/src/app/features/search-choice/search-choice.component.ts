import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { BrandMarkComponent } from '../../shared/brand-mark/brand-mark.component';

@Component({
  selector: 'app-search-choice', standalone: true, imports: [RouterLink, BrandMarkComponent],
  templateUrl: './search-choice.component.html', styleUrl: './search-choice.component.scss'
})
export class SearchChoiceComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  ngOnInit(): void {
    if (this.route.snapshot.queryParamMap.get('origem') === 'resolva-jato') {
      sessionStorage.setItem('mira-origin', 'resolva-jato');
    }
  }
}
