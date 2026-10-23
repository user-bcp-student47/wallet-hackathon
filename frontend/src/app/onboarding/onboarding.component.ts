import { Component, OnInit, effect, inject, untracked } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { FlowService } from '../core/flow.service';
import { SCENARIOS, STEPS, Step } from '../core/models';
import {
  OTP_TEST_CODE,
  birthDateError,
  documentError,
  fullNameError,
  otpError,
  phoneError,
  toValidator,
} from '../core/validators';
import { ResultComponent } from './result.component';

const control = (check: (value: string) => string | null) =>
  new FormControl('', { nonNullable: true, validators: [toValidator(check)] });

@Component({
  selector: 'app-onboarding',
  imports: [ReactiveFormsModule, ResultComponent],
  template: `
    @if (flow.phase() === 'loading') {
      <p class="muted" role="status">Cargando…</p>
    } @else if (flow.phase() === 'result') {
      <app-result />
    } @else {
      <nav aria-label="Pasos del onboarding">
        <ol class="stepper">
          @for (s of steps; track s.id; let i = $index) {
            <li
              [class.done]="isDone(s.id)"
              [class.current]="s.id === flow.step()"
              [attr.aria-current]="s.id === flow.step() ? 'step' : null"
            >
              <span class="dot">{{ isDone(s.id) ? '✓' : i + 1 }}</span>
              <span class="step-label">{{ s.label }}</span>
            </li>
          }
        </ol>
      </nav>
      <p class="progress">Paso {{ stepIndex() + 1 }} de {{ steps.length }}: {{ steps[stepIndex()].label }}</p>

      @if (flow.error(); as error) {
        <div class="alert" role="alert">{{ error }}</div>
      }

      <form (submit)="$event.preventDefault(); onSubmit()" novalidate>
        @switch (flow.step()) {
          @case ('phone') {
            <label for="phone">Número de celular</label>
            <input id="phone" type="tel" inputmode="numeric" autocomplete="off" maxlength="9" placeholder="912345678" [formControl]="phone" [attr.aria-invalid]="showError(phone)" aria-describedby="phone-help phone-error" />
            <p id="phone-help" class="help">Usa un número de prueba. No se envía ningún SMS real.</p>
            <p id="phone-error" class="field-error" role="alert">{{ errorOf(phone) }}</p>
          }
          @case ('otp') {
            <label for="otp">Código OTP</label>
            <input id="otp" type="text" inputmode="numeric" autocomplete="one-time-code" maxlength="6" placeholder="123456" [formControl]="otp" [attr.aria-invalid]="showError(otp)" aria-describedby="otp-help otp-error" />
            <p id="otp-help" class="help">Verificación simulada: no se envió ningún SMS. Usa el código de prueba <strong>{{ otpCode }}</strong>.</p>
            <p id="otp-error" class="field-error" role="alert">{{ errorOf(otp) }}</p>
          }
          @case ('personal') {
            <label for="fullName">Nombre completo</label>
            <input id="fullName" type="text" autocomplete="off" placeholder="Ana Pérez" [formControl]="fullName" [attr.aria-invalid]="showError(fullName)" aria-describedby="fullName-error" />
            <p id="fullName-error" class="field-error" role="alert">{{ errorOf(fullName) }}</p>

            <label for="birthDate">Fecha de nacimiento</label>
            <input id="birthDate" type="date" autocomplete="off" [formControl]="birthDate" [attr.aria-invalid]="showError(birthDate)" aria-describedby="birthDate-help birthDate-error" />
            <p id="birthDate-help" class="help">Debes tener 18 años o más.</p>
            <p id="birthDate-error" class="field-error" role="alert">{{ errorOf(birthDate) }}</p>
          }
          @case ('document') {
            <label for="document">Documento de prueba</label>
            <input id="document" type="text" autocomplete="off" placeholder="TEST-001" [formControl]="document" [attr.aria-invalid]="showError(document)" aria-describedby="document-help document-error" />
            <p id="document-help" class="help">Solo documentos ficticios que empiecen por TEST-. No ingreses documentos reales.</p>
            <p id="document-error" class="field-error" role="alert">{{ errorOf(document) }}</p>

            @if (flow.demo()) {
              <div class="demo-box">
                <label for="scenario">Escenario KYC (modo demo)</label>
                <select id="scenario" [value]="flow.scenario()" (change)="onScenario($event)">
                  @for (s of scenarios; track s.id) {
                    <option [value]="s.id" [selected]="s.id === flow.scenario()">{{ s.label }}</option>
                  }
                </select>
              </div>
            }
          }
        }

        <button type="submit" class="btn" [disabled]="flow.phase() === 'submitting'">{{ buttonLabel() }}</button>
      </form>
    }
  `,
})
export class OnboardingComponent implements OnInit {
  protected readonly flow = inject(FlowService);
  protected readonly steps = STEPS;
  protected readonly scenarios = SCENARIOS;
  protected readonly otpCode = OTP_TEST_CODE;

  protected readonly phone = control(phoneError);
  protected readonly otp = control(otpError);
  protected readonly fullName = control(fullNameError);
  protected readonly birthDate = control((v) => birthDateError(v));
  protected readonly document = control(documentError);

  ngOnInit(): void {
    void this.flow.init();
  }

  constructor() {
    effect(() => {
      this.flow.generation();
      untracked(() => [this.phone, this.otp, this.fullName, this.birthDate, this.document].forEach((c) => c.reset('')));
    });
  }

  protected stepIndex(): number {
    return STEPS.findIndex((s) => s.id === this.flow.step());
  }

  protected isDone(step: Step): boolean {
    return STEPS.findIndex((s) => s.id === step) < this.stepIndex();
  }

  protected buttonLabel(): string {
    if (this.flow.phase() === 'submitting') return 'Enviando…';
    return this.flow.step() === 'document' ? 'Enviar' : 'Continuar';
  }

  protected showError(c: FormControl<string>): 'true' | null {
    return c.touched && c.invalid ? 'true' : null;
  }

  protected errorOf(c: FormControl<string>): string {
    return c.touched && c.invalid ? (c.errors?.['message'] as string) : '';
  }

  protected onScenario(event: Event): void {
    this.flow.scenario.set((event.target as HTMLSelectElement).value as never);
  }

  protected async onSubmit(): Promise<void> {
    const controls = this.controlsFor(this.flow.step());
    controls.forEach((c) => c.markAsTouched());
    if (controls.some((c) => c.invalid)) return;

    if (this.flow.step() === 'document') {
      await this.flow.submit(this.document.value.trim());
    } else {
      await this.flow.completeStep(this.flow.step());
    }
  }

  private controlsFor(step: Step): FormControl<string>[] {
    switch (step) {
      case 'phone':
        return [this.phone];
      case 'otp':
        return [this.otp];
      case 'personal':
        return [this.fullName, this.birthDate];
      default:
        return [this.document];
    }
  }
}
