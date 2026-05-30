import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  Component,
  OnDestroy,
  computed,
  inject,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CoachingPanelComponent } from './components/coaching-panel/coaching-panel.component';
import { RecorderComponent } from './components/recorder/recorder.component';
import { SuggestionsComponent } from './components/suggestions/suggestions.component';
import { TranscriptComponent } from './components/transcript/transcript.component';
import type {
  ImprovementFeedback,
  ResponseLanguage,
  Suggestion,
  ToneId,
} from './models/speech';
import { ApiService } from './services/api.service';
import { SpeechService } from './services/speech.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RecorderComponent,
    TranscriptComponent,
    SuggestionsComponent,
    CoachingPanelComponent,
  ],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
})
export class AppComponent implements OnDestroy {
  private readonly api = inject(ApiService);
  private readonly speech = inject(SpeechService);

  readonly title = 'Speak Better';

  readonly language = signal<string>('auto');
  /** Coaching JSON language: English (default) or match transcript. */
  readonly responseLanguage = signal<ResponseLanguage>('en');
  readonly tone = signal<ToneId>('');
  readonly questionText = signal('');
  readonly processing = signal(false);
  readonly transcript = signal<string | null>(null);
  readonly suggestions = signal<Suggestion[] | null>(null);
  readonly modelAnswer = signal<string | null>(null);
  readonly improvements = signal<ImprovementFeedback | null>(null);
  readonly error = signal<string | null>(null);

  /** Use US English TTS when coaching is in English; else follow speech hint. */
  readonly ttsSpeakLang = computed(() =>
    this.responseLanguage() === 'en' ? 'en' : this.language(),
  );

  readonly hasResults = computed(() => {
    if (this.transcript()) {
      return true;
    }
    const s = this.suggestions();
    if (s && s.length > 0) {
      return true;
    }
    if (this.modelAnswer()?.trim()) {
      return true;
    }
    const i = this.improvements();
    return !!(i && (i.opening || i.wording || i.fillers || i.other));
  });

  ngOnDestroy(): void {
    this.speech.stop();
  }

  onAudioReady(blob: Blob): void {
    this.processAudio(blob, 'recording.webm');
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) {
      return;
    }
    this.processAudio(file, file.name);
  }

  triggerFilePicker(input: HTMLInputElement): void {
    input.click();
  }

  onRecordError(msg: string): void {
    this.error.set(msg);
  }

  clearResults(): void {
    this.transcript.set(null);
    this.suggestions.set(null);
    this.modelAnswer.set(null);
    this.improvements.set(null);
    this.error.set(null);
  }

  private processAudio(blob: Blob, filename: string): void {
    this.speech.stop();
    this.error.set(null);
    this.processing.set(true);
    const toneVal = this.tone().trim() || null;
    const q = this.questionText().trim() || null;
    this.api
      .transcribeAndRephrase(blob, this.language(), {
        filename,
        tone: toneVal,
        question: q,
        responseLanguage: this.responseLanguage(),
      })
      .subscribe({
        next: ({ transcript, rephrase }) => {
          this.transcript.set(transcript.text);
          this.suggestions.set(rephrase.suggestions);
          const ma = rephrase.modelAnswer?.trim();
          this.modelAnswer.set(ma?.length ? ma : null);
          this.improvements.set(
            rephrase.improvements ?? {
              opening: '',
              wording: '',
              fillers: '',
              other: '',
            },
          );
          this.processing.set(false);
        },
        error: (err: unknown) => {
          this.processing.set(false);
          this.error.set(this.formatError(err));
        },
      });
  }

  private formatError(err: unknown): string {
    if (err instanceof HttpErrorResponse) {
      const body = err.error as { error?: string } | string | null;
      if (body && typeof body === 'object' && 'error' in body && body.error) {
        return body.error;
      }
      if (typeof body === 'string' && body.length) {
        return body;
      }
      return err.message || `HTTP ${err.status}`;
    }
    if (err instanceof Error) {
      return err.message;
    }
    return 'Something went wrong';
  }
}
