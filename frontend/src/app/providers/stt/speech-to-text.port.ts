export type SttPartialHandler = (text: string, isFinal: boolean) => void;
export type SttErrorHandler = (message: string) => void;

/**
 * Speech-to-text port. Swap Web Speech for Deepgram/Groq streaming later
 * without changing the speaking-room page.
 */
export interface SpeechToTextPort {
  readonly id: string;
  readonly supported: boolean;
  start(options: {
    lang: string;
    onResult: SttPartialHandler;
    onError: SttErrorHandler;
    /** Wait this long after last speech chunk before committing a turn (ms). */
    commitSilenceMs?: number;
  }): void;
  /** Force-commit any buffered speech (e.g. user taps "I'm done"). */
  flush(): void;
  stop(): void;
}
