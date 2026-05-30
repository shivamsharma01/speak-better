export type RecordState = 'idle' | 'recording';

export type ToneId = '' | 'formal' | 'casual' | 'persuasive' | 'concise';

/** Coaching output: English (default) or same language as spoken transcript. */
export type ResponseLanguage = 'en' | 'same';

export interface TranscriptionResponse {
  text: string;
  language: string;
}

export interface Suggestion {
  text: string;
  why: string;
}

export interface ImprovementFeedback {
  opening: string;
  wording: string;
  fillers: string;
  other: string;
}

export interface RephraseResponse {
  suggestions: Suggestion[];
  modelAnswer: string;
  improvements: ImprovementFeedback;
}
