import { InjectionToken } from '@angular/core';
import type { SpeechToTextPort } from './stt/speech-to-text.port';
import type { TextToSpeechPort } from './tts/text-to-speech.port';

export const SPEECH_TO_TEXT = new InjectionToken<SpeechToTextPort>('SPEECH_TO_TEXT');
export const TEXT_TO_SPEECH = new InjectionToken<TextToSpeechPort>('TEXT_TO_SPEECH');
