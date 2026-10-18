import { Routes } from '@angular/router';
import { PracticePageComponent } from './pages/practice/practice-page.component';
import { ConsultationPageComponent } from './pages/consultation/consultation-page.component';
import { SpeakingPageComponent } from './pages/speaking/speaking-page.component';

export const routes: Routes = [
  { path: '', component: PracticePageComponent, title: 'Speak Better' },
  { path: 'speaking', component: SpeakingPageComponent, title: 'AI Speaking Partner' },
  { path: 'consultation', component: ConsultationPageComponent, title: 'Doctor–Patient Consultation' },
  { path: '**', redirectTo: '' },
];
