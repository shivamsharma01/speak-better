import { Injectable } from '@angular/core';
import type { TextToSpeechPort, TtsEndedHandler } from './text-to-speech.port';

/** Free browser TTS. Swap later without changing speaking-room UI. */
@Injectable({ providedIn: 'root' })
export class BrowserTts implements TextToSpeechPort {
  readonly id = 'browser-speech-synthesis';
  private _speaking = false;

  get speaking(): boolean {
    return this._speaking || window.speechSynthesis.speaking;
  }

  speak(text: string, options?: { lang?: string; onEnded?: TtsEndedHandler }): void {
    const trimmed = text?.trim();
    if (!trimmed) {
      options?.onEnded?.();
      return;
    }
    this.stop();
    const u = new SpeechSynthesisUtterance(trimmed);
    u.lang = options?.lang || 'en-IN';
    u.rate = 0.95;
    u.pitch = 1;
    u.onstart = () => {
      this._speaking = true;
    };
    u.onend = () => {
      this._speaking = false;
      options?.onEnded?.();
    };
    u.onerror = () => {
      this._speaking = false;
      options?.onEnded?.();
    };
    window.speechSynthesis.speak(u);
  }

  stop(): void {
    this._speaking = false;
    window.speechSynthesis.cancel();
  }
}
