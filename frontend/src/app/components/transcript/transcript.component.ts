import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';

@Component({
  selector: 'app-transcript',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './transcript.component.html',
  styleUrl: './transcript.component.scss',
})
export class TranscriptComponent {
  @Input() text: string | null = null;
}
