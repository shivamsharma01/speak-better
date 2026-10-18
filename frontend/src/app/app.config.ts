import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideHttpClient, withFetch } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { routes } from './app.routes';
import { SPEECH_TO_TEXT, TEXT_TO_SPEECH } from './providers/provider.tokens';
import { WebSpeechStt } from './providers/stt/web-speech.stt';
import { BrowserTts } from './providers/tts/browser.tts';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideHttpClient(withFetch()),
    provideRouter(routes),
    // Swap these classes to change STT/TTS without touching the speaking page.
    { provide: SPEECH_TO_TEXT, useExisting: WebSpeechStt },
    { provide: TEXT_TO_SPEECH, useExisting: BrowserTts },
  ],
};
