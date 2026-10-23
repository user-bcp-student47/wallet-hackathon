import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ApiErrorBody, Decision, RunView, Scenario, STEPS, Step } from './models';
import { OnboardingApi } from './onboarding-api';

const RUN_KEY = 'wallet-onboarding-run-id';

export type Phase = 'loading' | 'form' | 'submitting' | 'result';

export function firstIncompleteStep(completed: readonly Step[]): Step {
  return STEPS.find((s) => !completed.includes(s.id))?.id ?? 'document';
}

export function describeError(e: unknown): { code: string; message: string } {
  const err = e as HttpErrorResponse;
  const body = (err?.error ?? {}) as Partial<ApiErrorBody>;
  const code = body.error ?? (err?.status === 0 ? 'NETWORK' : 'UNKNOWN');
  switch (code) {
    case 'KYC_UNAVAILABLE':
      return {
        code,
        message: 'El servicio de verificación no está disponible por ahora. Tus datos se conservaron: vuelve a intentar.',
      };
    case 'MOCK_LIMIT_REACHED':
    case 'MOCK_ERROR':
      return { code, message: 'No pudimos completar la verificación. Intenta nuevamente en unos minutos.' };
    case 'MOCK_UNREACHABLE':
    case 'NETWORK':
      return { code, message: 'No hay conexión con el servicio. Revisa tu conexión e intenta de nuevo.' };
    case 'INVALID_REQUEST':
      return { code, message: body.message ?? 'Revisa los datos ingresados e intenta de nuevo.' };
    case 'DECISION_CONFLICT':
      return { code, message: 'La verificación ya tiene una decisión final distinta; se conserva el estado actual.' };
    case 'RUN_NOT_FOUND':
      return { code, message: 'Tu sesión expiró. Empieza de nuevo.' };
    default:
      return { code, message: 'Ocurrió un error inesperado. Intenta nuevamente.' };
  }
}

@Injectable({ providedIn: 'root' })
export class FlowService {
  private readonly api = inject(OnboardingApi);

  readonly phase = signal<Phase>('loading');
  readonly step = signal<Step>('phone');
  readonly run = signal<RunView | null>(null);
  readonly demo = signal(false);
  readonly scenario = signal<Scenario>('APPROVED');
  readonly error = signal<string | null>(null);
  /** Aumenta con cada corrida nueva; la interfaz lo usa para limpiar los formularios. */
  readonly generation = signal(0);

  private runId: string | null = null;
  private documentStepDone = false;

  async loadConfig(): Promise<void> {
    try {
      this.demo.set((await firstValueFrom(this.api.config())).demoEnabled);
    } catch {
      this.demo.set(false);
    }
  }

  async init(): Promise<void> {
    this.phase.set('loading');
    this.error.set(null);
    await this.loadConfig();

    const stored = this.read();
    if (stored) {
      try {
        this.apply(await firstValueFrom(this.api.getRun(stored)));
        if (this.phase() !== 'loading') return;
      } catch (e) {
        if (describeError(e).code !== 'RUN_NOT_FOUND') {
          this.fail(e);
          return;
        }
      }
    }
    await this.startNewRun();
  }

  /** Cierra la corrida actual como abandonada y empieza otra. */
  async restart(): Promise<void> {
    const current = this.runId;
    if (current) {
      try {
        await firstValueFrom(this.api.close(current));
      } catch {
        // Cerrar es un efecto secundario; si falla se inicia la nueva corrida igualmente.
      }
    }
    await this.startNewRun();
  }

  async completeStep(step: Step): Promise<void> {
    if (!this.runId || this.phase() !== 'form') return;
    try {
      await firstValueFrom(this.api.completeStep(this.runId, step));
    } catch (e) {
      this.fail(e);
      return;
    }
    this.error.set(null);
    const next = STEPS[STEPS.findIndex((s) => s.id === step) + 1];
    if (next) this.step.set(next.id);
  }

  async submit(document: string): Promise<void> {
    if (!this.runId || this.phase() !== 'form') return;
    this.phase.set('submitting');
    this.error.set(null);
    try {
      if (!this.documentStepDone) {
        await firstValueFrom(this.api.completeStep(this.runId, 'document'));
        this.documentStepDone = true;
      }
      const scenario = this.demo() ? this.scenario() : undefined;
      this.run.set(await firstValueFrom(this.api.submitKyc(this.runId, document, scenario)));
      this.phase.set('result');
    } catch (e) {
      this.phase.set('form');
      const described = describeError(e);
      if (described.code === 'ALREADY_SUBMITTED' && this.runId) {
        this.apply(await firstValueFrom(this.api.getRun(this.runId)));
        return;
      }
      this.error.set(described.message);
    }
  }

  async decide(decision: Decision): Promise<void> {
    if (!this.runId) return;
    this.error.set(null);
    try {
      this.run.set(await firstValueFrom(this.api.decide(this.runId, decision)));
    } catch (e) {
      this.error.set(describeError(e).message);
      try {
        this.run.set(await firstValueFrom(this.api.getRun(this.runId)));
      } catch {
        // Se conserva el estado visible.
      }
    }
  }

  private async startNewRun(): Promise<void> {
    try {
      const started = await firstValueFrom(this.api.startRun());
      this.runId = started.runId;
      this.write(started.runId);
      this.documentStepDone = false;
      this.run.set(null);
      this.step.set('phone');
      this.error.set(null);
      this.generation.update((g) => g + 1);
      this.phase.set('form');
    } catch (e) {
      this.fail(e);
    }
  }

  private apply(run: RunView): void {
    this.runId = run.runId;
    this.documentStepDone = run.completedSteps.includes('document');
    if (run.verification) {
      this.run.set(run);
      this.phase.set('result');
    } else if (run.status === 'ABANDONED') {
      this.phase.set('loading');
    } else {
      this.run.set(null);
      this.step.set(firstIncompleteStep(run.completedSteps));
      this.phase.set('form');
    }
  }

  private fail(e: unknown): void {
    this.error.set(describeError(e).message);
    if (this.phase() === 'loading') this.phase.set('form');
  }

  private read(): string | null {
    try {
      return sessionStorage.getItem(RUN_KEY);
    } catch {
      return null;
    }
  }

  private write(runId: string): void {
    try {
      sessionStorage.setItem(RUN_KEY, runId);
    } catch {
      // Sin almacenamiento: la corrida sigue, pero no se retoma tras recargar.
    }
  }
}
