export type TtsEndedHandler = () => void;

/**
 * Text-to-speech port. Browser synthesis today; Groq Orpheus / Kokoro later.
 */
export interface TextToSpeechPort {
  readonly id: string;
  speak(text: string, options?: { lang?: string; onEnded?: TtsEndedHandler }): void;
  stop(): void;
  readonly speaking: boolean;
}
