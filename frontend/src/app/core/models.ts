export type Step = 'phone' | 'otp' | 'personal' | 'document';

export const STEPS: { id: Step; label: string }[] = [
  { id: 'phone', label: 'Celular' },
  { id: 'otp', label: 'OTP' },
  { id: 'personal', label: 'Datos' },
  { id: 'document', label: 'Documento' },
];

export type Scenario = 'APPROVED' | 'MANUAL_REVIEW' | 'REJECTED' | 'UNAVAILABLE';

export const SCENARIOS: { id: Scenario; label: string }[] = [
  { id: 'APPROVED', label: 'APPROVED: registro aprobado' },
  { id: 'MANUAL_REVIEW', label: 'MANUAL_REVIEW: revisión manual' },
  { id: 'REJECTED', label: 'REJECTED: registro rechazado' },
  { id: 'UNAVAILABLE', label: 'UNAVAILABLE: KYC no disponible (503)' },
];

export type Decision = 'ACTIVE' | 'REJECTED';

export interface AppConfig {
  demoEnabled: boolean;
}

export interface Metric {
  millis: number | null;
  state: 'MEASURED' | 'PENDING' | 'NOT_AVAILABLE';
}

export interface RunSummary {
  runId: string;
  startedAt: string;
  status: string;
  flowDuration: Metric;
  kycReview: Metric;
  activation: Metric;
}

export interface VerificationView {
  id: string;
  walletId: string;
  outcome: 'APPROVED' | 'MANUAL_REVIEW' | 'REJECTED';
  walletActive: boolean;
}

export interface RunView {
  runId: string;
  startedAt: string;
  status: string;
  completedSteps: Step[];
  verification: VerificationView | null;
  metrics: RunSummary;
}

export interface StartRunResponse {
  runId: string;
  startedAt: string;
}

export interface SummaryView {
  referenceMillis: number;
  medianFlowMillis: number | null;
  measuredRuns: number;
  runs: RunSummary[];
}

export interface EventExport {
  runId: string;
  type: string;
  at: string;
  meta: string | null;
}

export interface ApiErrorBody {
  error: string;
  message: string;
}
