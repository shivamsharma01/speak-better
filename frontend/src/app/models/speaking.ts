export type SpeakingMode =
  | 'JUST_TALK'
  | 'COACH_ME'
  | 'HINDI_TO_ENGLISH'
  | 'HINGLISH_SENTENCE'
  | 'HINGLISH_CHAT';
export type TurnRole = 'STUDENT' | 'ASSISTANT' | 'SYSTEM';
export type UtteranceSource = 'SPEECH' | 'TYPED';

export interface SpeakingTurn {
  id: string;
  role: TurnRole;
  text: string;
  source: UtteranceSource;
  createdAt: string;
  finalised: boolean;
}

export interface CorrectionEvent {
  id: string;
  turnId: string;
  originalText: string;
  issueType?: string;
  error?: boolean;
  correction?: string;
  naturalAlternative?: string;
  usefulPhrase?: string;
  whenToUse?: string;
  explanation?: string;
  priority?: string;
  confidence?: number;
  status: 'pending' | 'ready' | 'skipped' | 'failed' | string;
  createdAt: string;
}

export interface VocabularyItem {
  id: string;
  phrase: string;
  insteadOf?: string;
  whenToUse?: string;
  turnId?: string;
  seenCount: number;
  createdAt?: string;
  lastSeenAt?: string;
}

export interface LearnerPattern {
  issueType: string;
  exampleOriginal: string;
  exampleCorrection: string;
  count: number;
  lastSeenAt?: string;
}

export interface DrillItem {
  id: string;
  index: number;
  speaker?: string | null;
  hinglish: string;
  modelEnglish: string;
  status: string;
}

export interface DrillEvaluation {
  id: string;
  drillItemId: string;
  drillIndex: number;
  studentEnglish: string;
  modelEnglish: string;
  hinglish: string;
  score: number;
  meaningPreserved: boolean;
  verdict: string;
  betterEnglish: string;
  usefulPhrase?: string;
  tip?: string;
  createdAt: string;
}

export interface SpeakingSession {
  id: string;
  mode: SpeakingMode;
  topic: string;
  level: string;
  status: string;
  createdAt: string;
  endedAt?: string;
  conversationProvider: string;
  coachingProvider: string;
  turns: SpeakingTurn[];
  corrections: CorrectionEvent[];
  patterns: LearnerPattern[];
  vocabulary?: VocabularyItem[];
  drillItems?: DrillItem[];
  drillEvaluations?: DrillEvaluation[];
  currentDrillIndex?: number;
  chatTitle?: string;
  reviewSummary?: string;
}

export interface TurnResponse {
  sessionId: string;
  studentTurn: SpeakingTurn;
  assistantTurn: SpeakingTurn;
  coachingPending: boolean;
}

export interface DrillAttemptResponse {
  sessionId: string;
  studentTurn: SpeakingTurn;
  evaluation: DrillEvaluation;
  nextItem: DrillItem | null;
  currentDrillIndex: number;
  drillComplete: boolean;
  evaluations: DrillEvaluation[];
}

export interface ProviderInfo {
  conversationProvider: string;
  coachingProvider: string;
  availableConversation: string[];
  availableCoaching: string[];
  sttNote: string;
  ttsNote: string;
}
