import { CommonModule } from '@angular/common';
import { Component, Input, inject } from '@angular/core';
import { SpeechService } from '../../services/speech.service';

@Component({
  selector: 'app-transcript',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './transcript.component.html',
  styleUrl: './transcript.component.scss',
})
export class TranscriptComponent {
  private readonly speech = inject(SpeechService);

  @Input() textHindi: string | null = null;
  @Input() textEnglish: string | null = null;
  /** Fallback when bilingual fields are missing (older API). */
  @Input() text: string | null = null;

  hindiDisplay(): string | null {
    const hi = this.textHindi?.trim();
    if (hi) {
      return hi;
    }
    return this.text?.trim() || null;
  }

  englishDisplay(): string | null {
    const en = this.textEnglish?.trim();
    if (en) {
      return en;
    }
    return this.text?.trim() || null;
  }

  showHindi(): boolean {
    return !!this.hindiDisplay();
  }

  showEnglish(): boolean {
    return !!this.englishDisplay();
  }

  speak(text: string | null | undefined, lang: string): void {
    if (text?.trim()) {
      this.speech.speak(text, lang);
    }
  }
}
