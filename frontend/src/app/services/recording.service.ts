import { Injectable, NgZone } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import type { RecordState } from '../models/speech';

@Injectable({ providedIn: 'root' })
export class RecordingService {
  private readonly stateSubject = new BehaviorSubject<RecordState>('idle');
  private readonly elapsedMsSubject = new BehaviorSubject(0);

  private mediaRecorder?: MediaRecorder;
  private chunks: BlobPart[] = [];
  private stream?: MediaStream;
  private startedAt = 0;
  private tickHandle?: ReturnType<typeof setInterval>;

  /** Live mic stream while recording (for waveform). Cleared before tracks stop. */
  private readonly activeStreamSubject = new BehaviorSubject<MediaStream | null>(
    null,
  );
  readonly activeStream$: Observable<MediaStream | null> =
    this.activeStreamSubject.asObservable();

  readonly state$: Observable<RecordState> = this.stateSubject.asObservable();
  readonly elapsedMs$: Observable<number> = this.elapsedMsSubject.asObservable();

  constructor(private readonly zone: NgZone) {}

  get state(): RecordState {
    return this.stateSubject.value;
  }

  async start(): Promise<void> {
    if (this.stateSubject.value === 'recording') {
      return;
    }
    this.stopTick();
    this.chunks = [];
    this.stream = await navigator.mediaDevices.getUserMedia({ audio: true });

    const options: MediaRecorderOptions = {};
    if (MediaRecorder.isTypeSupported('audio/webm;codecs=opus')) {
      options.mimeType = 'audio/webm;codecs=opus';
    } else if (MediaRecorder.isTypeSupported('audio/webm')) {
      options.mimeType = 'audio/webm';
    }

    this.mediaRecorder = new MediaRecorder(this.stream, options);
    this.mediaRecorder.ondataavailable = (ev: BlobEvent) => {
      if (ev.data && ev.data.size > 0) {
        this.chunks.push(ev.data);
      }
    };

    this.mediaRecorder.start(250);
    this.stateSubject.next('recording');
    this.startedAt = Date.now();
    // Defer so the canvas is in the DOM before we attach AnalyserNode.
    setTimeout(() => {
      if (this.stream && this.stateSubject.value === 'recording') {
        this.activeStreamSubject.next(this.stream);
      }
    }, 0);
    this.elapsedMsSubject.next(0);
    this.zone.runOutsideAngular(() => {
      this.tickHandle = setInterval(() => {
        this.zone.run(() =>
          this.elapsedMsSubject.next(Date.now() - this.startedAt),
        );
      }, 200);
    });
  }

  async stop(): Promise<Blob> {
    if (!this.mediaRecorder || this.mediaRecorder.state === 'inactive') {
      throw new Error('Not recording');
    }

    return new Promise<Blob>((resolve, reject) => {
      const mr = this.mediaRecorder!;
      mr.onerror = () => {
        this.cleanup();
        reject(new Error('Recording failed'));
      };
      mr.onstop = () => {
        try {
          const type = mr.mimeType || 'audio/webm';
          const blob = new Blob(this.chunks, { type });
          this.cleanup();
          resolve(blob);
        } catch (e) {
          this.cleanup();
          reject(e);
        }
      };
      mr.stop();
    });
  }

  toggle(): Promise<Blob | void> {
    if (this.stateSubject.value === 'recording') {
      return this.stop();
    }
    return this.start();
  }

  private cleanup(): void {
    this.stopTick();
    this.activeStreamSubject.next(null);
    if (this.stream) {
      for (const t of this.stream.getTracks()) {
        t.stop();
      }
      this.stream = undefined;
    }
    this.mediaRecorder = undefined;
    this.chunks = [];
    this.stateSubject.next('idle');
    this.elapsedMsSubject.next(0);
  }

  private stopTick(): void {
    if (this.tickHandle != null) {
      clearInterval(this.tickHandle);
      this.tickHandle = undefined;
    }
  }
}
