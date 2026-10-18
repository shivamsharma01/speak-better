import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import type {
  ProviderInfo,
  SpeakingMode,
  SpeakingSession,
  TurnResponse,
  UtteranceSource,
} from '../models/speaking';

@Injectable({ providedIn: 'root' })
export class SpeakingApiService {
  private readonly http = inject(HttpClient);

  providers(): Observable<ProviderInfo> {
    return this.http.get<ProviderInfo>('/api/speaking/providers');
  }

  createSession(body: {
    topic: string;
    mode: SpeakingMode;
    level?: string;
  }): Observable<SpeakingSession> {
    return this.http.post<SpeakingSession>('/api/speaking/sessions', body);
  }

  listSessions(limit = 20): Observable<SpeakingSession[]> {
    return this.http.get<SpeakingSession[]>(`/api/speaking/sessions?limit=${limit}`);
  }

  getSession(id: string): Observable<SpeakingSession> {
    return this.http.get<SpeakingSession>(`/api/speaking/sessions/${id}`);
  }

  submitTurn(
    sessionId: string,
    text: string,
    source: UtteranceSource,
  ): Observable<TurnResponse> {
    return this.http.post<TurnResponse>(`/api/speaking/sessions/${sessionId}/turns`, {
      text,
      source,
    });
  }

  endSession(sessionId: string): Observable<SpeakingSession> {
    return this.http.post<SpeakingSession>(`/api/speaking/sessions/${sessionId}/end`, {});
  }
}
