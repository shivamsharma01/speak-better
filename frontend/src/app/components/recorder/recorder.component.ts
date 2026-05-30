import { CommonModule } from '@angular/common';
import {
  Component,
  ElementRef,
  EventEmitter,
  Input,
  OnDestroy,
  Output,
  ViewChild,
  inject,
} from '@angular/core';
import { Subscription } from 'rxjs';
import { RecordingService } from '../../services/recording.service';
import { SpeechService } from '../../services/speech.service';

@Component({
  selector: 'app-recorder',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './recorder.component.html',
  styleUrl: './recorder.component.scss',
})
export class RecorderComponent implements OnDestroy {
  readonly recording = inject(RecordingService);
  private readonly speech = inject(SpeechService);

  @ViewChild('waveCanvas') private waveCanvas?: ElementRef<HTMLCanvasElement>;

  @Input({ required: false }) locked = false;

  @Output() readonly audioReady = new EventEmitter<Blob>();

  @Output() readonly recordError = new EventEmitter<string>();

  private streamSub?: Subscription;
  private audioCtx?: AudioContext;
  private analyser?: AnalyserNode;
  private sourceNode?: MediaStreamAudioSourceNode;
  private rafId?: number;
  private dataArray?: Uint8Array;
  private activeStreamId = 0;

  constructor() {
    this.streamSub = this.recording.activeStream$.subscribe((stream) => {
      if (stream) {
        this.activeStreamId += 1;
        const id = this.activeStreamId;
        let attempts = 0;
        const tryMount = () => {
          if (id !== this.activeStreamId || attempts++ > 120) {
            return;
          }
          if (!this.waveCanvas?.nativeElement) {
            requestAnimationFrame(tryMount);
            return;
          }
          this.startVisualizer(stream);
        };
        queueMicrotask(tryMount);
      } else {
        this.activeStreamId += 1;
        this.stopVisualizer();
      }
    });
  }

  ngOnDestroy(): void {
    this.streamSub?.unsubscribe();
    this.stopVisualizer();
  }

  async onMainClick(): Promise<void> {
    if (this.locked) {
      return;
    }
    try {
      if (this.recording.state === 'recording') {
        const blob = await this.recording.stop();
        this.audioReady.emit(blob);
      } else {
        this.speech.stop();
        await this.recording.start();
      }
    } catch (e) {
      const msg =
        e instanceof Error ? e.message : 'Could not access microphone';
      this.recordError.emit(msg);
    }
  }

  formatElapsed(ms: number): string {
    const s = Math.floor(ms / 1000);
    const m = Math.floor(s / 60);
    const r = s % 60;
    return `${m}:${r.toString().padStart(2, '0')}`;
  }

  private startVisualizer(stream: MediaStream): void {
    this.stopVisualizer();
    const canvas = this.waveCanvas?.nativeElement;
    if (!canvas) {
      return;
    }

    const Ctx =
      window.AudioContext ||
      (
        window as unknown as {
          webkitAudioContext: typeof AudioContext;
        }
      ).webkitAudioContext;
    if (!Ctx) {
      return;
    }

    const ctx = new Ctx();
    this.audioCtx = ctx;
    void ctx.resume().catch(() => undefined);

    const source = ctx.createMediaStreamSource(stream);
    this.sourceNode = source;
    const analyser = ctx.createAnalyser();
    this.analyser = analyser;
    analyser.fftSize = 512;
    analyser.smoothingTimeConstant = 0.72;
    analyser.minDecibels = -85;
    analyser.maxDecibels = -25;
    source.connect(analyser);

    const bufferLength = analyser.frequencyBinCount;
    this.dataArray = new Uint8Array(bufferLength);

    const c2d = canvas.getContext('2d');
    if (!c2d) {
      void ctx.close().catch(() => undefined);
      this.audioCtx = undefined;
      this.analyser = undefined;
      this.sourceNode = undefined;
      return;
    }

    const draw = () => {
      if (!this.analyser || !this.dataArray || !this.audioCtx) {
        return;
      }
      if (this.audioCtx.state === 'closed') {
        return;
      }

      const { width, height } = this.resizeCanvas(canvas, c2d);
      this.analyser.getByteFrequencyData(this.dataArray);

      c2d.clearRect(0, 0, width, height);
      const bars = 48;
      const step = Math.floor(this.dataArray.length / bars);
      const barW = width / bars;
      const mid = height * 0.55;

      for (let i = 0; i < bars; i++) {
        let sum = 0;
        for (let j = 0; j < step; j++) {
          sum += this.dataArray[i * step + j] ?? 0;
        }
        const avg = sum / step;
        const h = Math.max(2, (avg / 255) * (height * 0.85));
        const x = i * barW;
        const hue = 195 + (i / bars) * 60;
        c2d.fillStyle = `hsla(${hue}, 85%, 62%, 0.85)`;
        c2d.fillRect(x + 1, mid - h / 2, Math.max(1, barW - 2), h);
      }

      c2d.strokeStyle = 'rgba(148, 163, 184, 0.25)';
      c2d.beginPath();
      c2d.moveTo(0, mid);
      c2d.lineTo(width, mid);
      c2d.stroke();

      this.rafId = requestAnimationFrame(draw);
    };

    this.rafId = requestAnimationFrame(draw);
  }

  private resizeCanvas(
    canvas: HTMLCanvasElement,
    c2d: CanvasRenderingContext2D,
  ): { width: number; height: number } {
    const dpr = Math.min(window.devicePixelRatio ?? 1, 2);
    const rect = canvas.getBoundingClientRect();
    const w = Math.max(200, Math.floor(rect.width));
    const h = Math.max(56, Math.floor(rect.height));
    if (canvas.width !== Math.floor(w * dpr) || canvas.height !== Math.floor(h * dpr)) {
      canvas.width = Math.floor(w * dpr);
      canvas.height = Math.floor(h * dpr);
      c2d.setTransform(dpr, 0, 0, dpr, 0, 0);
    }
    return { width: w, height: h };
  }

  private stopVisualizer(): void {
    if (this.rafId != null) {
      cancelAnimationFrame(this.rafId);
      this.rafId = undefined;
    }
    try {
      this.sourceNode?.disconnect();
    } catch {
      /* ignore */
    }
    this.sourceNode = undefined;
    this.analyser = undefined;
    this.dataArray = undefined;
    if (this.audioCtx && this.audioCtx.state !== 'closed') {
      void this.audioCtx.close();
    }
    this.audioCtx = undefined;

    const canvas = this.waveCanvas?.nativeElement;
    if (canvas) {
      const c2d = canvas.getContext('2d');
      if (c2d) {
        c2d.clearRect(0, 0, canvas.width, canvas.height);
      }
    }
  }
}
