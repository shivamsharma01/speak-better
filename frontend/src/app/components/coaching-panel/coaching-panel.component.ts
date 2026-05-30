import { CommonModule } from '@angular/common';
import { Component, Input, inject } from '@angular/core';
import type { ImprovementFeedback } from '../../models/speech';
import { SpeechService } from '../../services/speech.service';

@Component({
  selector: 'app-coaching-panel',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './coaching-panel.component.html',
  styleUrl: './coaching-panel.component.scss',
})
export class CoachingPanelComponent {
  private readonly speech = inject(SpeechService);

  @Input() modelAnswer: string | null = null;
  @Input() improvements: ImprovementFeedback | null = null;
  @Input() speakLang = 'auto';

  speak(text: string | null | undefined): void {
    if (!text?.trim()) {
      return;
    }
    this.speech.speak(text, this.speakLang);
  }

  hasImprovements(): boolean {
    const i = this.improvements;
    if (!i) {
      return false;
    }
    return !!(i.opening || i.wording || i.fillers || i.other);
  }
}
