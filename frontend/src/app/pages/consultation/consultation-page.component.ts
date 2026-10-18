import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { RecorderComponent } from '../../components/recorder/recorder.component';
import type { ConversationResponse } from '../../models/conversation';
import { ApiService } from '../../services/api.service';
import { SpeechService } from '../../services/speech.service';

@Component({
  selector: 'app-consultation-page',
  standalone: true,
  imports: [CommonModule, FormsModule, RecorderComponent],
  templateUrl: './consultation-page.component.html',
  styleUrl: './consultation-page.component.scss',
})
export class ConsultationPageComponent implements OnDestroy {
  private readonly api = inject(ApiService);
  private readonly speech = inject(SpeechService);
  private processingSub?: Subscription;

  readonly language = signal('auto');
  readonly processing = signal(false);
  readonly error = signal<string | null>(null);
  readonly result = signal<ConversationResponse | null>(null);
  readonly transcriptView = signal<'hindi' | 'english' | 'both'>('both');

  ngOnDestroy(): void {
    this.cancelProcessing();
    this.speech.stop();
  }

  onAudioReady(blob: Blob): void {
    this.processAudio(blob, 'recording.webm');
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (file) {
      this.processAudio(file, file.name);
    }
  }

  triggerFilePicker(input: HTMLInputElement): void {
    input.click();
  }

  onRecordError(msg: string): void {
    this.error.set(msg);
  }

  cancelProcessing(): void {
    this.processingSub?.unsubscribe();
    this.processingSub = undefined;
    this.processing.set(false);
  }

  clearResults(): void {
    this.result.set(null);
    this.error.set(null);
  }

  speakerLabel(speaker: string): string {
    return speaker === 'doctor' ? 'Doctor' : 'Patient';
  }

  speak(text: string, lang: 'hi' | 'en'): void {
    this.speech.speak(text, lang);
  }

  private processAudio(blob: Blob, filename: string): void {
    this.cancelProcessing();
    this.speech.stop();
    this.error.set(null);
    this.result.set(null);
    this.processing.set(true);

    this.processingSub = this.api
      .processConversation(blob, this.language(), filename)
      .subscribe({
        next: (res) => {
          this.result.set(res);
          this.processing.set(false);
          this.processingSub = undefined;
        },
        error: (err: unknown) => {
          this.processing.set(false);
          this.processingSub = undefined;
          if (this.isAbortError(err)) {
            return;
          }
          this.error.set(this.formatError(err));
        },
      });
  }

  private isAbortError(err: unknown): boolean {
    if (err instanceof DOMException && err.name === 'AbortError') {
      return true;
    }
    if (err instanceof HttpErrorResponse && err.status === 0) {
      return true;
    }
    return false;
  }

  private formatError(err: unknown): string {
    if (err instanceof HttpErrorResponse) {
      const body = err.error as { error?: string } | string | null;
      if (body && typeof body === 'object' && 'error' in body && body.error) {
        return body.error;
      }
      return err.message || `HTTP ${err.status}`;
    }
    if (err instanceof Error) {
      return err.message;
    }
    return 'Something went wrong';
  }
}
