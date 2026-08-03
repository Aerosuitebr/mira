import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { CdkDragDrop, DragDropModule, moveItemInArray, transferArrayItem } from '@angular/cdk/drag-drop';
import { ApiService, KanbanBoard, KanbanCard, KanbanStage } from '../../core/api.service';

type ValueFilter = 'ALL' | '10K' | '100K';
type DateFilter = 'ALL' | '7D' | '30D' | 'NEWEST' | 'OLDEST';

@Component({
  selector: 'app-crm',
  standalone: true,
  imports: [DragDropModule, DecimalPipe, CurrencyPipe, FormsModule, RouterLink],
  templateUrl: './crm.component.html',
  styleUrl: './crm.component.scss'
})
export class CrmComponent implements OnInit {
  private readonly api = inject(ApiService);

  board?: KanbanBoard;
  displayStages: KanbanStage[] = [];
  loading = true;
  moving = false;
  moveError = '';

  searchQuery = '';
  ownerFilter = 'ALL';
  valueFilter: ValueFilter = 'ALL';
  dateFilter: DateFilter = 'ALL';

  ngOnInit(): void {
    this.loadBoard();
  }

  loadBoard(): void {
    this.loading = true;
    this.moveError = '';
    this.api.board().subscribe({
      next: (board) => {
        this.board = board;
        this.rebuildView();
        this.loading = false;
      },
      error: () => {
        this.loading = false;
      }
    });
  }

  get hasActiveFilters(): boolean {
    return (
      !!this.searchQuery.trim() ||
      this.ownerFilter !== 'ALL' ||
      this.valueFilter !== 'ALL' ||
      this.dateFilter !== 'ALL'
    );
  }

  get isBoardEmpty(): boolean {
    return this.board?.stages.every((stage) => stage.cards.length === 0) ?? false;
  }

  get ownerOptions(): string[] {
    if (!this.board) {
      return [];
    }
    const names = new Set<string>();
    for (const stage of this.board.stages) {
      for (const card of stage.cards) {
        if (card.ownerName) {
          names.add(card.ownerName);
        }
      }
    }
    return [...names].sort((a, b) => a.localeCompare(b, 'pt-BR'));
  }

  get showOwnerFilter(): boolean {
    return this.ownerOptions.length > 1;
  }

  viewStages(): KanbanStage[] {
    return this.displayStages;
  }

  onFiltersChanged(): void {
    this.rebuildView();
  }

  rebuildView(): void {
    if (!this.board) {
      this.displayStages = [];
      return;
    }

    if (!this.hasActiveFilters) {
      this.displayStages = this.board.stages;
      return;
    }

    this.displayStages = this.board.stages.map((stage) => ({
      ...stage,
      cards: this.filterCards(stage.cards)
    }));
  }

  stageLeadCount(stage: KanbanStage): number {
    return stage.cards.length;
  }

  stageTotalValue(stage: KanbanStage): number {
    return stage.cards.reduce((sum, card) => sum + (card.valueAmount ?? 0), 0);
  }

  filteredLeadCount(): number {
    return this.viewStages().reduce((sum, stage) => sum + stage.cards.length, 0);
  }

  clearFilters(): void {
    this.searchQuery = '';
    this.ownerFilter = 'ALL';
    this.valueFilter = 'ALL';
    this.dateFilter = 'ALL';
    this.rebuildView();
  }

  drop(event: CdkDragDrop<KanbanCard[]>, targetStage: KanbanStage): void {
    if (!this.board || this.moving || this.hasActiveFilters) {
      return;
    }

    const sourceStage = this.board.stages.find((stage) => stage.id === event.previousContainer.id);
    const destStage = this.board.stages.find((stage) => stage.id === targetStage.id);
    if (!sourceStage || !destStage) {
      return;
    }

    const snapshot = this.cloneBoard(this.board);

    if (event.previousContainer === event.container) {
      moveItemInArray(destStage.cards, event.previousIndex, event.currentIndex);
    } else {
      transferArrayItem(
        sourceStage.cards,
        destStage.cards,
        event.previousIndex,
        event.currentIndex
      );
    }

    const card = destStage.cards[event.currentIndex];
    if (!card) {
      return;
    }

    this.moving = true;
    this.api.moveCard(card.id, destStage.id, event.currentIndex).subscribe({
      next: (updated) => {
        card.position = updated.position;
        this.moving = false;
      },
      error: () => {
        this.board = snapshot;
        this.moveError = 'Não foi possível mover o card. Tente novamente.';
        this.moving = false;
      }
    });
  }

  private filterCards(cards: KanbanCard[]): KanbanCard[] {
    const query = this.searchQuery.trim().toLowerCase();
    let result = cards.filter((card) => {
      if (query) {
        const haystack = `${card.companyName} ${card.title}`.toLowerCase();
        if (!haystack.includes(query)) {
          return false;
        }
      }

      if (this.ownerFilter !== 'ALL' && card.ownerName !== this.ownerFilter) {
        return false;
      }

      const value = card.valueAmount ?? 0;
      if (this.valueFilter === '10K' && value < 10_000) {
        return false;
      }
      if (this.valueFilter === '100K' && value < 100_000) {
        return false;
      }

      if (this.dateFilter === '7D' || this.dateFilter === '30D') {
        if (!card.createdAt) {
          return false;
        }
        const created = new Date(card.createdAt).getTime();
        const days = this.dateFilter === '7D' ? 7 : 30;
        const cutoff = Date.now() - days * 24 * 60 * 60 * 1000;
        if (created < cutoff) {
          return false;
        }
      }

      return true;
    });

    if (this.dateFilter === 'NEWEST' || this.dateFilter === 'OLDEST') {
      result = [...result].sort((a, b) => {
        const aTime = a.createdAt ? new Date(a.createdAt).getTime() : 0;
        const bTime = b.createdAt ? new Date(b.createdAt).getTime() : 0;
        return this.dateFilter === 'NEWEST' ? bTime - aTime : aTime - bTime;
      });
    }

    return result;
  }

  private cloneBoard(board: KanbanBoard): KanbanBoard {
    return {
      ...board,
      stages: board.stages.map((stage) => ({
        ...stage,
        cards: [...stage.cards]
      }))
    };
  }
}
