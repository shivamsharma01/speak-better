# Planned enhancements

Implement these in order (after the core record → transcribe → rephrase flow).

1. **One-click copy for each suggestion** — Copy buttons on each rephrased line (shipped in v1 in the suggestions list).

2. **Live waveform / visualizer while recording** — **Done:** Web Audio `AnalyserNode` + frequency bars on `<canvas>` in `RecorderComponent`, fed by `RecordingService.activeStream$` (deferred until after view).

3. **Tone selector** — **Done:** UI + `tone` on `POST /api/rephrase`.

4. **Grammar / filler-word analysis** — New `POST /api/analyze` that returns structured highlights (e.g. `um`, `uh`, repeated words); render underline or badges in the transcript view.

5. **Translation** — If input is Hindi, also show English gloss or parallel English suggestions; extend `RephraseRequest` with `targetLanguage` and adjust the LLM prompt.

6. **Text-to-speech playback** — **Done (browser):** `SpeechSynthesisUtterance` via `SpeechService`; optional later swap to a hosted TTS API for higher quality.

7. **Local history** — Angular service persisting `{ transcript, suggestions, timestamp }[]` to `localStorage`; simple list UI to reload a past session.

**Also shipped:** optional **question** + **coaching** (`modelAnswer`, `improvements`), **upload** of audio/video files, **Play** (browser TTS), **tone** selector, and **response language** for coaching (`en` default vs `same` as transcript).
