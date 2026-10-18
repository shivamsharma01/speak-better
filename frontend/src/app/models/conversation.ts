export interface ConversationTurn {
  speaker: string;
  textHindi: string;
  textEnglish: string;
}

export interface ClinicalSummary {
  chiefComplaint: string;
  keyPoints: string[];
  assessment: string;
  plan: string;
  followUp: string;
  medicationsMentioned: string[];
}

export interface ConversationResponse {
  rawTranscript: string;
  turns: ConversationTurn[];
  summaryEnglish: ClinicalSummary;
  summaryHindi: ClinicalSummary;
}
