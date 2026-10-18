import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subscription, interval, switchMap, takeWhile } from 'rxjs';
import type {
  CorrectionEvent,
  SpeakingMode,
  SpeakingSession,
  SpeakingTurn,
} from '../../models/speaking';
import { SPEECH_TO_TEXT, TEXT_TO_SPEECH } from '../../providers/provider.tokens';
import { SpeakingApiService } from '../../services/speaking-api.service';

@Component({
  selector: 'app-speaking-page',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './speaking-page.component.html',
  styleUrl: './speaking-page.component.scss',
})
export class SpeakingPageComponent implements OnInit, OnDestroy {
  private readonly api = inject(SpeakingApiService);
  private readonly stt = inject(SPEECH_TO_TEXT);
  private readonly tts = inject(TEXT_TO_SPEECH);

  topic = 'A disagreement at work';
  mode: SpeakingMode = 'JUST_TALK';
  level = 'intermediate';

  session: SpeakingSession | null = null;
  pastSessions: SpeakingSession[] = [];
  providerLabel = '';

  listening = false;
  processing = false;
  interimText = '';
  typedText = '';
  error = '';
  reviewMode = false;
  /** True while STT is buffering speech and waiting for a silence pause. */
  holdingSpeech = false;

  private pollSub: Subscription | null = null;
  private lastSpokenAssistantId = '';

  get turns(): SpeakingTurn[] {
    return this.session?.turns ?? [];
  }

  get coachingNotes(): CorrectionEvent[] {
    return (this.session?.corrections ?? []).filter(
      (c) => c.status === 'ready' || c.status === 'pending',
    );
  }

  get phraseBank() {
    return this.session?.vocabulary ?? [];
  }

  get sttSupported(): boolean {
    return this.stt.supported;
  }

  get isHindiToEnglish(): boolean {
    return (this.session?.mode ?? this.mode) === 'HINDI_TO_ENGLISH';
  }

  get speechLang(): string {
    return this.isHindiToEnglish ? 'hi-IN' : 'en-IN';
  }

  isWordUpgrade(c: CorrectionEvent): boolean {
    const t = (c.issueType || '').toLowerCase();
    return t === 'word_upgrade' || t === 'word_choice' || t === 'vague_wording';
  }

  issueLabel(c: CorrectionEvent): string {
    if (this.isWordUpgrade(c)) {
      return 'richer wording';
    }
    return c.issueType || 'tip';
  }

  ngOnInit(): void {
    this.api.providers().subscribe({
      next: (p) => {
        this.providerLabel = `Conversation/coaching: ${p.conversationProvider} · STT: ${this.stt.id} · TTS: ${this.tts.id}`;
      },
      error: () => {
        this.providerLabel = `STT: ${this.stt.id} · TTS: ${this.tts.id}`;
      },
    });
    this.refreshPast();
  }

  ngOnDestroy(): void {
    this.stopListening();
    this.tts.stop();
    this.pollSub?.unsubscribe();
  }

  startSession(): void {
    this.error = '';
    this.reviewMode = false;
    this.processing = true;
    this.stopListening();
    this.tts.stop();
    this.api
      .createSession({ topic: this.topic, mode: this.mode, level: this.level })
      .subscribe({
        next: (session) => {
          this.session = session;
          this.processing = false;
          this.lastSpokenAssistantId = '';
          this.speakLatestAssistant();
          this.startPolling();
          this.startListening();
          this.refreshPast();
        },
        error: (err) => {
          this.processing = false;
          this.error = err?.error?.error || err?.message || 'Failed to start session';
        },
      });
  }

  endSession(): void {
    if (!this.session) {
      return;
    }
    this.stopListening();
    this.tts.stop();
    this.pollSub?.unsubscribe();
    this.processing = true;
    this.api.endSession(this.session.id).subscribe({
      next: (session) => {
        this.session = session;
        this.processing = false;
        this.reviewMode = true;
        this.refreshPast();
      },
      error: (err) => {
        this.processing = false;
        this.error = err?.error?.error || err?.message || 'Failed to end session';
      },
    });
  }

  toggleListen(): void {
    if (this.listening) {
      this.stopListening();
    } else {
      this.startListening();
    }
  }

  sendTyped(): void {
    const text = this.typedText.trim();
    if (!text || !this.session || this.processing) {
      return;
    }
    this.typedText = '';
    this.submitStudentText(text, 'TYPED');
  }

  openPast(id: string): void {
    this.stopListening();
    this.tts.stop();
    this.pollSub?.unsubscribe();
    this.api.getSession(id).subscribe({
      next: (session) => {
        this.session = session;
        this.reviewMode = session.status !== 'active';
        if (session.status === 'active') {
          this.startPolling();
          this.startListening();
        }
      },
      error: (err) => {
        this.error = err?.error?.error || 'Could not load session';
      },
    });
  }

  /** Commit buffered speech immediately (after you finish a long thought). */
  finishSpeaking(): void {
    if (!this.listening || this.processing) {
      return;
    }
    this.stt.flush();
  }

  private startListening(): void {
    if (!this.session || this.session.status !== 'active' || !this.stt.supported) {
      return;
    }
    this.listening = true;
    this.interimText = '';
    this.holdingSpeech = false;
    this.stt.start({
      lang: this.speechLang,
      commitSilenceMs: this.isHindiToEnglish ? 2000 : 1700,
      onResult: (text, isFinal) => {
        if (this.tts.speaking) {
          // Barge-in: student spoke while AI was talking
          this.tts.stop();
        }
        if (!isFinal) {
          this.interimText = text;
          this.holdingSpeech = !!text.trim();
          return;
        }
        this.interimText = '';
        this.holdingSpeech = false;
        this.submitStudentText(text, 'SPEECH');
      },
      onError: (message) => {
        this.error = message;
      },
    });
  }

  private stopListening(): void {
    this.listening = false;
    this.interimText = '';
    this.holdingSpeech = false;
    this.stt.stop();
  }

  private submitStudentText(text: string, source: 'SPEECH' | 'TYPED'): void {
    if (!this.session || this.processing) {
      return;
    }
    this.processing = true;
    this.error = '';
    // Pause mic briefly to avoid capturing AI TTS / echo as the next student turn
    const wasListening = this.listening;
    this.stopListening();

    this.api.submitTurn(this.session.id, text, source).subscribe({
      next: (res) => {
        if (!this.session) {
          return;
        }
        this.session = {
          ...this.session,
          turns: [...this.session.turns, res.studentTurn, res.assistantTurn],
        };
        this.processing = false;
        this.speakLatestAssistant();
        if (wasListening && this.session.status === 'active') {
          // Resume after TTS ends (handled in speak), or immediately if TTS empty
        }
      },
      error: (err) => {
        this.processing = false;
        this.error = err?.error?.error || err?.message || 'Turn failed';
        if (wasListening) {
          this.startListening();
        }
      },
    });
  }

  private speakLatestAssistant(): void {
    const last = [...this.turns].reverse().find((t) => t.role === 'ASSISTANT');
    if (!last || last.id === this.lastSpokenAssistantId) {
      if (this.session?.status === 'active' && !this.listening) {
        this.startListening();
      }
      return;
    }
    this.lastSpokenAssistantId = last.id;
    this.tts.speak(last.text, {
      lang: 'en-IN',
      onEnded: () => {
        if (this.session?.status === 'active') {
          this.startListening();
        }
      },
    });
  }

  private startPolling(): void {
    this.pollSub?.unsubscribe();
    if (!this.session) {
      return;
    }
    const id = this.session.id;
    this.pollSub = interval(2000)
      .pipe(
        takeWhile(() => !!this.session && this.session.id === id && this.session.status === 'active'),
        switchMap(() => this.api.getSession(id)),
      )
      .subscribe({
        next: (session) => {
          this.session = session;
        },
      });
  }

  private refreshPast(): void {
    this.api.listSessions(12).subscribe({
      next: (list) => {
        this.pastSessions = list;
      },
    });
  }
}
