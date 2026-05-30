import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { map, Observable, switchMap } from 'rxjs';
import type {
  RephraseResponse,
  ResponseLanguage,
  TranscriptionResponse,
} from '../models/speech';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  transcribe(
    audio: Blob,
    language: string,
    filename = 'recording.webm',
  ): Observable<TranscriptionResponse> {
    const form = new FormData();
    form.append('audio', audio, filename);
    if (language && language !== 'auto') {
      form.append('language', language);
    }
    return this.http.post<TranscriptionResponse>('/api/transcribe', form);
  }

  rephrase(
    text: string,
    language: string,
    options?: {
      tone?: string | null;
      question?: string | null;
      responseLanguage?: ResponseLanguage;
    },
  ): Observable<RephraseResponse> {
    const body: {
      text: string;
      language?: string;
      tone?: string;
      question?: string;
      responseLanguage?: ResponseLanguage;
    } = { text };
    if (language && language !== 'auto') {
      body.language = language;
    }
    if (options?.tone) {
      body.tone = options.tone;
    }
    if (options?.question?.trim()) {
      body.question = options.question.trim();
    }
    body.responseLanguage = options?.responseLanguage ?? 'en';
    return this.http.post<RephraseResponse>('/api/rephrase', body);
  }

  transcribeAndRephrase(
    audio: Blob,
    language: string,
    options?: {
      tone?: string | null;
      question?: string | null;
      filename?: string;
      responseLanguage?: ResponseLanguage;
    },
  ): Observable<{ transcript: TranscriptionResponse; rephrase: RephraseResponse }> {
    const filename = options?.filename ?? 'recording.webm';
    return this.transcribe(audio, language, filename).pipe(
      switchMap((transcript) =>
        this.rephrase(transcript.text, language, {
          tone: options?.tone,
          question: options?.question,
          responseLanguage: options?.responseLanguage ?? 'en',
        }).pipe(map((rephrase) => ({ transcript, rephrase }))),
      ),
    );
  }
}
