import { Injectable } from '@angular/core';

/** Browser text-to-speech (no API key). */
@Injectable({ providedIn: 'root' })
export class SpeechService {
  speak(text: string, languageHint: string): void {
    const trimmed = text?.trim();
    if (!trimmed) {
      return;
    }
    window.speechSynthesis.cancel();
    const u = new SpeechSynthesisUtterance(trimmed);
    u.lang = this.toBcp47(languageHint);
    u.rate = 0.92;
    u.pitch = 1;
    window.speechSynthesis.speak(u);
  }

  stop(): void {
    window.speechSynthesis.cancel();
  }

  private toBcp47(hint: string): string {
    if (hint === 'hi') {
      return 'hi-IN';
    }
    if (hint === 'en') {
      return 'en-US';
    }
    return 'en-US';
  }
}
