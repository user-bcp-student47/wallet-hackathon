import { Routes } from '@angular/router';
import { OnboardingComponent } from './onboarding/onboarding.component';
import { SummaryComponent } from './summary/summary.component';

export const routes: Routes = [
  { path: '', component: OnboardingComponent },
  { path: 'summary', component: SummaryComponent },
  { path: '**', redirectTo: '' },
];
