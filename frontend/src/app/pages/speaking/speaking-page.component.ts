import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subscription, interval, switchMap, takeWhile } from 'rxjs';
import type {
  CorrectionEvent,
  DrillEvaluation,
  DrillItem,
  SpeakingMode,
  SpeakingSession,
  SpeakingTurn,
} from '../../models/speaking';
import { SPEECH_TO_TEXT, TEXT_TO_SPEECH } from '../../providers/provider.tokens';
import { ApiService } from '../../services/api.service';
import { RecordingService } from '../../services/recording.service';
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
  private readonly speechApi = inject(ApiService);
  private readonly recording = inject(RecordingService);
  private readonly stt = inject(SPEECH_TO_TEXT);
  private readonly tts = inject(TEXT_TO_SPEECH);

  topic = 'A disagreement at work';
  mode: SpeakingMode = 'JUST_TALK';
  level = 'intermediate';
  chatInputMode: 'turn' | 'full' = 'turn';

  session: SpeakingSession | null = null;
  pastSessions: SpeakingSession[] = [];
  providerLabel = '';
  latestEvaluation: DrillEvaluation | null = null;

  listening = false;
  processing = false;
  interimText = '';
  typedText = '';
  error = '';
  reviewMode = false;
  holdingSpeech = false;

  /** Whisper draft for drills — user confirms/edits before scoring. */
  pendingTranscript = '';
  showConfirm = false;
  transcribing = false;

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

  get drillItems(): DrillItem[] {
    return this.session?.drillItems ?? [];
  }

  get drillEvaluations(): DrillEvaluation[] {
    return this.session?.drillEvaluations ?? [];
  }

  get currentDrillItem(): DrillItem | null {
    if (!this.session?.drillItems?.length) {
      return null;
    }
    const idx = this.session.currentDrillIndex ?? 0;
    return this.session.drillItems[idx] ?? null;
  }

  get sttSupported(): boolean {
    return this.isDrillMode ? true : this.stt.supported;
  }

  get isHindiToEnglish(): boolean {
    return (this.session?.mode ?? this.mode) === 'HINDI_TO_ENGLISH';
  }

  get isDrillMode(): boolean {
    const m = this.session?.mode ?? this.mode;
    return m === 'HINGLISH_SENTENCE' || m === 'HINGLISH_CHAT';
  }

  get isChatDrill(): boolean {
    return (this.session?.mode ?? this.mode) === 'HINGLISH_CHAT';
  }

  get speechLang(): string {
    return this.isHindiToEnglish ? 'hi-IN' : 'en-IN';
  }

  get startLabel(): string {
    if (this.mode === 'HINGLISH_SENTENCE' || this.mode === 'HINGLISH_CHAT') {
      return this.processing ? 'Generating drill…' : 'Start drill';
    }
    return this.processing ? 'Starting…' : 'Start conversation';
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

  evaluationFor(index: number): DrillEvaluation | undefined {
    return this.drillEvaluations.find((e) => e.drillIndex === index);
  }

  ngOnInit(): void {
    this.api.providers().subscribe({
      next: (p) => {
        this.providerLabel = `LLM: ${p.conversationProvider} · Drill STT: Groq Whisper · Live chat STT: ${this.stt.id}`;
      },
      error: () => {
        this.providerLabel = `Drill STT: Groq Whisper · Live chat STT: ${this.stt.id}`;
      },
    });
    this.refreshPast();
  }

  ngOnDestroy(): void {
    this.stopListening();
    this.tts.stop();
    this.pollSub?.unsubscribe();
  }

  onModeChange(): void {
    if (this.mode === 'HINGLISH_SENTENCE' && this.topic === 'A disagreement at work') {
      this.topic = 'Office life';
    }
    if (this.mode === 'HINGLISH_CHAT' && (this.topic === 'A disagreement at work' || this.topic === 'Office life')) {
      this.topic = 'Weekend plans with a friend';
    }
  }

  startSession(): void {
    this.error = '';
    this.reviewMode = false;
    this.latestEvaluation = null;
    this.showConfirm = false;
    this.pendingTranscript = '';
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
          this.listening = false;
          this.holdingSpeech = false;
          if (!session.drillItems?.length) {
            // Live chat: AI speaks, then browser STT listens.
            this.speakLatestAssistant();
          }
          // Hinglish drills: wait for an explicit Record click (mic needs a user gesture).
          this.startPolling();
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
        this.showConfirm = false;
        this.refreshPast();
      },
      error: (err) => {
        this.processing = false;
        this.error = err?.error?.error || err?.message || 'Failed to end session';
      },
    });
  }

  toggleListen(): void {
    if (this.isDrillMode) {
      void this.toggleDrillRecord();
      return;
    }
    if (this.listening) {
      this.stopListening();
    } else {
      this.startListening();
    }
  }

  /** Explicit record/stop for Hinglish drills (Whisper). */
  async toggleDrillRecord(): Promise<void> {
    if (this.processing || this.transcribing || this.showConfirm) {
      return;
    }
    if (this.listening) {
      await this.stopDrillRecordingAndTranscribe();
      return;
    }
    await this.startDrillRecording();
  }

  sendTyped(): void {
    const text = this.typedText.trim();
    if (!text || !this.session || this.processing) {
      return;
    }
    this.typedText = '';
    this.showConfirm = false;
    this.pendingTranscript = '';
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
        this.showConfirm = false;
        this.latestEvaluation = session.drillEvaluations?.length
          ? session.drillEvaluations[session.drillEvaluations.length - 1]
          : null;
        if (session.status === 'active') {
          this.startPolling();
          // Live chat can resume STT; drills wait for Record English click.
          if (!this.isDrillMode) {
            this.startListening();
          }
        }
      },
      error: (err) => {
        this.error = err?.error?.error || 'Could not load session';
      },
    });
  }

  /** Finish recording (drill Whisper) or flush browser STT (live chat). */
  finishSpeaking(): void {
    if (!this.listening || this.processing || this.transcribing) {
      return;
    }
    if (this.isDrillMode) {
      void this.stopDrillRecordingAndTranscribe();
      return;
    }
    this.stt.flush();
  }

  get drillRecordLabel(): string {
    if (this.transcribing) {
      return 'Transcribing…';
    }
    if (this.listening) {
      return "Stop & transcribe";
    }
    return 'Record English';
  }

  confirmPending(): void {
    const text = this.pendingTranscript.trim();
    if (!text || this.processing) {
      return;
    }
    this.showConfirm = false;
    this.submitStudentText(text, 'SPEECH');
  }

  discardPending(): void {
    this.showConfirm = false;
    this.pendingTranscript = '';
    // User clicked Re-record — safe to open mic from this gesture.
    if (this.isDrillMode) {
      void this.startDrillRecording();
    } else {
      this.startListening();
    }
  }

  private startListening(): void {
    if (!this.session || this.session.status !== 'active' || this.showConfirm) {
      return;
    }
    if (this.isDrillMode) {
      void this.startDrillRecording();
      return;
    }
    if (!this.stt.supported) {
      return;
    }
    this.listening = true;
    this.interimText = '';
    this.holdingSpeech = false;
    const silence =
      this.isHindiToEnglish || (this.isChatDrill && this.chatInputMode === 'full') ? 2200 : 1700;
    this.stt.start({
      lang: this.speechLang,
      commitSilenceMs: silence,
      onResult: (text, isFinal) => {
        if (this.tts.speaking) {
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

  private async startDrillRecording(): Promise<void> {
    try {
      this.error = '';
      this.stt.stop();
      await this.recording.start();
      this.listening = true;
      this.holdingSpeech = true;
      this.interimText = '';
    } catch (e) {
      this.listening = false;
      this.holdingSpeech = false;
      const msg = e instanceof Error ? e.message : 'Microphone permission needed';
      this.error =
        msg.includes('NotAllowedError') || msg.toLowerCase().includes('permission')
          ? 'Microphone blocked. Allow mic for localhost in the browser address bar, then tap Record English.'
          : msg;
    }
  }

  private async stopDrillRecordingAndTranscribe(): Promise<void> {
    if (this.recording.state !== 'recording') {
      this.listening = false;
      this.holdingSpeech = false;
      this.error = 'Mic was not recording. Tap Record English, speak, then Stop & transcribe.';
      return;
    }
    this.listening = false;
    this.holdingSpeech = false;
    this.transcribing = true;
    this.error = '';
    try {
      const blob = await this.recording.stop();
      if (!blob || blob.size < 400) {
        this.transcribing = false;
        this.error = 'Recording too short — tap Record English, speak a full line, then Stop & transcribe.';
        return;
      }
      this.speechApi.transcribeStt(blob, 'en').subscribe({
        next: (res) => {
          this.transcribing = false;
          const text = (res.text || '').trim();
          if (!text) {
            this.error = 'Could not hear speech clearly. Try Record again or type your English below.';
            return;
          }
          this.pendingTranscript = text;
          this.showConfirm = true;
        },
        error: (err) => {
          this.transcribing = false;
          this.error = err?.error?.error || err?.message || 'Whisper transcription failed';
        },
      });
    } catch (e) {
      this.transcribing = false;
      this.error = e instanceof Error ? e.message : 'Recording failed';
    }
  }

  private stopListening(): void {
    this.listening = false;
    this.interimText = '';
    this.holdingSpeech = false;
    this.stt.stop();
    this.recording.cancel();
  }

  private submitStudentText(text: string, source: 'SPEECH' | 'TYPED'): void {
    if (!this.session || this.processing) {
      return;
    }
    this.processing = true;
    this.error = '';
    const wasListening = this.listening;
    this.stopListening();

    if (this.isDrillMode) {
      this.submitDrill(text, source, wasListening);
      return;
    }

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

  private submitDrill(
    text: string,
    source: 'SPEECH' | 'TYPED',
    wasListening: boolean,
  ): void {
    if (!this.session) {
      return;
    }

    if (this.isChatDrill && this.chatInputMode === 'full') {
      this.api.submitDrillRetell(this.session.id, text, source).subscribe({
        next: (session) => {
          this.session = session;
          this.processing = false;
          this.reviewMode = true;
          this.pendingTranscript = '';
          this.latestEvaluation = session.drillEvaluations?.length
            ? session.drillEvaluations[session.drillEvaluations.length - 1]
            : null;
          this.pollSub?.unsubscribe();
          this.refreshPast();
          this.speakLatestAssistant();
        },
        error: (err) => {
          this.processing = false;
          this.error = err?.error?.error || err?.message || 'Retell evaluation failed';
          if (wasListening) {
            this.startListening();
          }
        },
      });
      return;
    }

    this.api.submitDrillAttempt(this.session.id, text, source).subscribe({
      next: (res) => {
        if (!this.session) {
          return;
        }
        this.latestEvaluation = res.evaluation;
        this.pendingTranscript = '';
        this.api.getSession(this.session.id).subscribe({
          next: (session) => {
            this.session = session;
            this.processing = false;
            if (res.drillComplete) {
              this.reviewMode = true;
              this.pollSub?.unsubscribe();
              this.refreshPast();
            }
            this.speakLatestAssistant();
          },
          error: () => {
            this.processing = false;
            if (wasListening && !res.drillComplete) {
              this.startListening();
            }
          },
        });
      },
      error: (err) => {
        this.processing = false;
        this.error = err?.error?.error || err?.message || 'Drill attempt failed';
        if (wasListening) {
          this.startListening();
        }
      },
    });
  }

  private speakLatestAssistant(): void {
    if (this.isDrillMode) {
      // Drills stay idle until the user taps Record English (avoids blocked auto-mic).
      return;
    }
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
    if (!this.session || this.isDrillMode) {
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
