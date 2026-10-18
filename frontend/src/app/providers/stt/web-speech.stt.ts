import { Injectable, NgZone } from '@angular/core';
import type { SpeechToTextPort, SttErrorHandler, SttPartialHandler } from './speech-to-text.port';

type SpeechRecognitionLike = {
  continuous: boolean;
  interimResults: boolean;
  lang: string;
  onresult: ((ev: SpeechRecognitionEventLike) => void) | null;
  onerror: ((ev: { error?: string }) => void) | null;
  onend: (() => void) | null;
  start: () => void;
  stop: () => void;
  abort: () => void;
};

type SpeechRecognitionEventLike = {
  resultIndex: number;
  results: ArrayLike<{ isFinal: boolean; 0: { transcript: string } }>;
};

declare global {
  interface Window {
    SpeechRecognition?: new () => SpeechRecognitionLike;
    webkitSpeechRecognition?: new () => SpeechRecognitionLike;
  }
}

/**
 * Free browser STT (Chrome/Edge).
 * Buffers short "final" chunks and only commits after a silence gap so slow
 * pronunciations (e.g. "woke") are not cut into "wook" mid-word.
 */
@Injectable({ providedIn: 'root' })
export class WebSpeechStt implements SpeechToTextPort {
  readonly id = 'web-speech';
  readonly supported: boolean;

  private recognition: SpeechRecognitionLike | null = null;
  private intentionalStop = false;
  private committedBuffer = '';
  private interim = '';
  private silenceTimer: ReturnType<typeof setTimeout> | null = null;
  private commitSilenceMs = 1600;
  private onResult: SttPartialHandler | null = null;
  private onError: SttErrorHandler | null = null;

  constructor(private readonly zone: NgZone) {
    this.supported = !!(window.SpeechRecognition || window.webkitSpeechRecognition);
  }

  start(options: {
    lang: string;
    onResult: SttPartialHandler;
    onError: SttErrorHandler;
    commitSilenceMs?: number;
  }): void {
    if (!this.supported) {
      options.onError('Web Speech API is not supported in this browser. Use Chrome/Edge or type instead.');
      return;
    }
    this.stop();
    this.intentionalStop = false;
    this.committedBuffer = '';
    this.interim = '';
    this.onResult = options.onResult;
    this.onError = options.onError;
    this.commitSilenceMs = options.commitSilenceMs ?? 1600;

    const Ctor = window.SpeechRecognition || window.webkitSpeechRecognition!;
    const recognition = new Ctor();
    recognition.continuous = true;
    recognition.interimResults = true;
    recognition.lang = options.lang || 'en-IN';

    recognition.onresult = (event) => {
      this.zone.run(() => {
        let interim = '';
        let finalChunk = '';
        for (let i = event.resultIndex; i < event.results.length; i++) {
          const piece = event.results[i][0].transcript;
          if (event.results[i].isFinal) {
            finalChunk += piece;
          } else {
            interim += piece;
          }
        }

        if (finalChunk.trim()) {
          this.committedBuffer = this.joinUtterance(this.committedBuffer, finalChunk.trim());
        }
        this.interim = interim.trim();
        this.emitPartial();
        this.armSilenceCommit();
      });
    };

    recognition.onerror = (ev) => {
      this.zone.run(() => {
        const code = ev.error || 'unknown';
        if (code === 'aborted' || code === 'no-speech') {
          return;
        }
        this.onError?.(`Speech recognition: ${code}`);
      });
    };

    recognition.onend = () => {
      if (!this.intentionalStop && this.recognition === recognition) {
        try {
          recognition.start();
        } catch {
          /* already started */
        }
      }
    };

    this.recognition = recognition;
    recognition.start();
  }

  flush(): void {
    this.clearSilenceTimer();
    const text = this.joinUtterance(this.committedBuffer, this.interim).trim();
    this.committedBuffer = '';
    this.interim = '';
    if (text && this.onResult) {
      this.zone.run(() => this.onResult!(text, true));
    } else {
      this.emitPartial();
    }
  }

  stop(): void {
    this.intentionalStop = true;
    this.clearSilenceTimer();
    this.committedBuffer = '';
    this.interim = '';
    this.onResult = null;
    this.onError = null;
    if (this.recognition) {
      try {
        this.recognition.onend = null;
        this.recognition.stop();
      } catch {
        /* ignore */
      }
      this.recognition = null;
    }
  }

  private armSilenceCommit(): void {
    this.clearSilenceTimer();
    // Wait longer when the buffer is still a short fragment (likely mid-word / mid-phrase).
    const words = this.committedBuffer.trim().split(/\s+/).filter(Boolean).length;
    const wait = words > 0 && words < 4 ? this.commitSilenceMs + 700 : this.commitSilenceMs;

    this.silenceTimer = setTimeout(() => {
      this.zone.run(() => this.commitIfReady());
    }, wait);
  }

  private commitIfReady(): void {
    const text = this.committedBuffer.trim();
    if (!text) {
      return;
    }
    // Keep holding very short fragments a bit longer unless user flushes manually.
    const words = text.split(/\s+/).filter(Boolean).length;
    if (words < 2 && this.interim) {
      this.armSilenceCommit();
      return;
    }
    this.committedBuffer = '';
    this.interim = '';
    this.onResult?.(text, true);
  }

  private emitPartial(): void {
    const draft = this.joinUtterance(this.committedBuffer, this.interim).trim();
    if (draft) {
      this.onResult?.(draft, false);
    }
  }

  private clearSilenceTimer(): void {
    if (this.silenceTimer != null) {
      clearTimeout(this.silenceTimer);
      this.silenceTimer = null;
    }
  }

  private joinUtterance(a: string, b: string): string {
    const left = a.trim();
    const right = b.trim();
    if (!left) {
      return right;
    }
    if (!right) {
      return left;
    }
    return `${left} ${right}`.replace(/\s+/g, ' ');
  }
}
