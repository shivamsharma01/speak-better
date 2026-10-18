# Planned enhancements

Implement these in order (after the core record → transcribe → rephrase flow).

1. **One-click copy for each suggestion** — Copy buttons on each rephrased line (shipped in v1 in the suggestions list).

2. **Live waveform / visualizer while recording** — **Done:** Web Audio `AnalyserNode` + frequency bars on `<canvas>` in `RecorderComponent`, fed by `RecordingService.activeStream$` (deferred until after view).

3. **Tone selector** — **Done:** UI + `tone` on `POST /api/rephrase`.

4. **Grammar / filler-word analysis** — New `POST /api/analyze` that returns structured highlights (e.g. `um`, `uh`, repeated words); render underline or badges in the transcript view.

5. **Translation** — **Partially done:** `/api/transcribe` returns `textHindi` + `textEnglish` for the full utterance. Optional: parallel English suggestions when coaching is in Hindi-only mode.

6. **Text-to-speech playback** — **Done (browser):** `SpeechSynthesisUtterance` via `SpeechService`; optional later swap to a hosted TTS API for higher quality.

7. **Local history** — Angular service persisting `{ transcript, suggestions, timestamp }[]` to `localStorage`; simple list UI to reload a past session.

8. **AI Speaking Partner (POC)** — **Done (v1):** `/speaking` with adapter-based STT/TTS (browser), Groq conversation + async coaching, file-backed session logs, past-session review. Next: streaming cloud STT / native Live voice provider behind the same ports; vocab retrieval practice.

**Also shipped:** optional **question** + **coaching** (`modelAnswer`, `improvements`), **upload** of audio/video files, **Play** (browser TTS), **tone** selector, and **response language** for coaching (`en` default vs `same` as transcript).
