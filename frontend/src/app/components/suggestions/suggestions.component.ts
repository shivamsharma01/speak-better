import { CommonModule } from '@angular/common';
import { Component, Input, inject } from '@angular/core';
import type { Suggestion } from '../../models/speech';
import { SpeechService } from '../../services/speech.service';

@Component({
  selector: 'app-suggestions',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './suggestions.component.html',
  styleUrl: './suggestions.component.scss',
})
export class SuggestionsComponent {
  private readonly speech = inject(SpeechService);

  @Input() suggestions: Suggestion[] | null = null;
  @Input() speakLang = 'auto';

  copiedIndex: number | null = null;

  speak(text: string): void {
    this.speech.speak(text, this.speakLang);
  }

  async copy(text: string, index: number): Promise<void> {
    try {
      await navigator.clipboard.writeText(text);
      this.copiedIndex = index;
      setTimeout(() => {
        if (this.copiedIndex === index) {
          this.copiedIndex = null;
        }
      }, 1600);
    } catch {
      // ignore
    }
  }
}
