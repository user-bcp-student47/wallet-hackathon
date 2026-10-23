import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AppConfig,
  Decision,
  EventExport,
  RunView,
  Scenario,
  StartRunResponse,
  Step,
  SummaryView,
} from './models';

/** Cliente de la API del backend. El frontend nunca llama al mock KYC directamente. */
@Injectable({ providedIn: 'root' })
export class OnboardingApi {
  private readonly http = inject(HttpClient);

  config(): Observable<AppConfig> {
    return this.http.get<AppConfig>('/api/config');
  }

  startRun(): Observable<StartRunResponse> {
    return this.http.post<StartRunResponse>('/api/onboarding/runs', null);
  }

  getRun(runId: string): Observable<RunView> {
    return this.http.get<RunView>(`/api/onboarding/runs/${runId}`);
  }

  completeStep(runId: string, step: Step): Observable<void> {
    return this.http.post<void>(`/api/onboarding/runs/${runId}/steps/${step}`, null);
  }

  /** Solo envía el documento de prueba y, en modo demo, el escenario. */
  submitKyc(runId: string, document: string, scenario?: Scenario): Observable<RunView> {
    const body = scenario ? { document, scenario } : { document };
    return this.http.post<RunView>(`/api/onboarding/runs/${runId}/kyc`, body);
  }

  decide(runId: string, decision: Decision): Observable<RunView> {
    return this.http.post<RunView>(`/api/onboarding/runs/${runId}/kyc/decision`, { decision });
  }

  close(runId: string): Observable<void> {
    return this.http.post<void>(`/api/onboarding/runs/${runId}/close`, null);
  }

  summary(): Observable<SummaryView> {
    return this.http.get<SummaryView>('/api/onboarding/summary');
  }

  events(): Observable<EventExport[]> {
    return this.http.get<EventExport[]>('/api/onboarding/events');
  }
}
