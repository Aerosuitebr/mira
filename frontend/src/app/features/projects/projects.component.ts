import { DatePipe } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { CdkDragDrop, DragDropModule, moveItemInArray, transferArrayItem } from '@angular/cdk/drag-drop';
import { RouterLink } from '@angular/router';
import { ApiService, ProjectBoard } from '../../core/api.service';

interface BoardProject {
  id: string;
  name: string;
  status: string;
  progressPercent: number;
  clientName: string;
  dueAt?: string;
}

interface BoardColumn {
  status: string;
  label: string;
  projects: BoardProject[];
}

@Component({
  selector: 'app-projects',
  standalone: true,
  imports: [DragDropModule, RouterLink, DatePipe],
  templateUrl: './projects.component.html',
  styleUrl: './projects.component.scss'
})
export class ProjectsComponent implements OnInit {
  private readonly api = inject(ApiService);

  columns: BoardColumn[] = [];
  loading = true;
  moving = false;

  private readonly statusColors: Record<string, string> = {
    NOT_STARTED: '#94a3b8',
    IN_PROGRESS: '#3b82f6',
    REVIEW: '#f59e0b',
    COMPLETED: '#22c55e'
  };

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.api.getProjectBoard().subscribe({
      next: (board: ProjectBoard) => {
        this.columns = board.columns.map((col) => ({
          status: col.status,
          label: col.label,
          projects: [...col.projects]
        }));
        this.loading = false;
      },
      error: () => {
        this.loading = false;
      }
    });
  }

  columnColor(status: string): string {
    return this.statusColors[status] ?? '#6366f1';
  }

  get isBoardEmpty(): boolean {
    return this.columns.length > 0 && this.columns.every((col) => col.projects.length === 0);
  }

  isOverdue(project: BoardProject): boolean {
    if (!project.dueAt || project.status !== 'IN_PROGRESS') {
      return false;
    }
    return new Date(project.dueAt).getTime() < Date.now();
  }

  drop(event: CdkDragDrop<BoardProject[]>, targetStatus: string): void {
    if (this.moving || event.previousContainer === event.container && event.previousIndex === event.currentIndex) {
      return;
    }

    const project = event.previousContainer.data[event.previousIndex];
    if (!project) {
      return;
    }

    const snapshot = this.columns.map((col) => ({
      ...col,
      projects: [...col.projects]
    }));

    if (event.previousContainer === event.container) {
      moveItemInArray(event.container.data, event.previousIndex, event.currentIndex);
      return;
    }

    transferArrayItem(
      event.previousContainer.data,
      event.container.data,
      event.previousIndex,
      event.currentIndex
    );

    project.status = targetStatus;
    this.moving = true;
    this.api.updateProjectStatus(project.id, targetStatus).subscribe({
      next: () => {
        this.moving = false;
      },
      error: () => {
        this.columns = snapshot;
        this.moving = false;
      }
    });
  }
}
