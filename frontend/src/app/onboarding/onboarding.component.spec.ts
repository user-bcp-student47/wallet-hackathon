import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RunView } from '../core/models';
import { OnboardingComponent } from './onboarding.component';

const NO_CONTENT = { status: 204, statusText: 'No Content' };

function approvedRun(): RunView {
  return {
    runId: 'r1',
    startedAt: '2026-01-01T10:00:00Z',
    status: 'APPROVED',
    completedSteps: ['phone', 'otp', 'personal', 'document'],
    verification: { id: 'k1', walletId: 'w1', outcome: 'APPROVED', walletActive: true },
    metrics: {} as RunView['metrics'],
  };
}

describe('OnboardingComponent', () => {
  let fixture: ComponentFixture<OnboardingComponent>;
  let http: HttpTestingController;
  let el: HTMLElement;

  const next = (url: string, method = 'GET') =>
    vi.waitFor(() => http.expectOne((r) => r.url === url && r.method === method));

  const flush = () => {
    fixture.detectChanges();
    return fixture.whenStable().then(() => fixture.detectChanges());
  };

  function type(id: string, value: string): void {
    const input = el.querySelector<HTMLInputElement>(`#${id}`)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function submitForm(): void {
    el.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }

  const currentStep = () => el.querySelector('li[aria-current="step"] .step-label')?.textContent?.trim();
  const errorText = (id: string) => el.querySelector(`#${id}-error`)?.textContent?.trim() ?? '';

  async function mount(demo = false): Promise<void> {
    fixture = TestBed.createComponent(OnboardingComponent);
    el = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
    (await next('/api/config')).flush({ demoEnabled: demo });
    (await next('/api/onboarding/runs', 'POST')).flush({ runId: 'r1', startedAt: '2026-01-01T10:00:00Z' });
    await flush();
  }

  async function pass(step: string, id: string, value: string): Promise<void> {
    type(id, value);
    submitForm();
    (await next(`/api/onboarding/runs/r1/steps/${step}`, 'POST')).flush(null, NO_CONTENT);
    await flush();
  }

  async function reachDocumentStep(): Promise<void> {
    await pass('phone', 'phone', '912345678');
    await pass('otp', 'otp', '123456');
    type('fullName', 'Ana Pérez');
    type('birthDate', '1999-05-05');
    submitForm();
    (await next('/api/onboarding/runs/r1/steps/personal', 'POST')).flush(null, NO_CONTENT);
    await flush();
  }

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });

  describe('sequence of steps', () => {
    it('shows the 4 required steps and identifies the current one', async () => {
      await mount();

      const labels = Array.from(el.querySelectorAll('.stepper .step-label')).map((n) => n.textContent?.trim());
      expect(labels).toEqual(['Celular', 'OTP', 'Datos', 'Documento']);
      expect(currentStep()).toBe('Celular');
      expect(el.querySelector('.progress')?.textContent).toContain('Paso 1 de 4');
    });

    it('moves forward and marks completed steps', async () => {
      await mount();
      await pass('phone', 'phone', '912345678');

      expect(currentStep()).toBe('OTP');
      expect(el.querySelectorAll('.stepper li.done').length).toBe(1);
      expect(el.querySelector('.progress')?.textContent).toContain('Paso 2 de 4');
    });
  });

  describe('blocks advancing on invalid data', () => {
    it('does not advance with an empty required field and shows what to do', async () => {
      await mount();
      submitForm();
      await flush();

      http.expectNone((r) => r.url.includes('/steps/'));
      expect(currentStep()).toBe('Celular');
      expect(errorText('phone')).toContain('obligatorio');
    });

    it('does not advance with an invalid phone and explains the format', async () => {
      await mount();
      type('phone', '812345678');
      submitForm();
      await flush();

      http.expectNone((r) => r.url.includes('/steps/'));
      expect(errorText('phone')).toContain('9 dígitos');
    });

    it('clears the error and advances once the value is corrected', async () => {
      await mount();
      type('phone', '123');
      submitForm();
      await flush();
      expect(errorText('phone')).not.toBe('');

      await pass('phone', 'phone', '912345678');

      expect(currentStep()).toBe('OTP');
      expect(el.querySelector('#phone-error')).toBeNull();
    });

    it('rejects a wrong simulated OTP and accepts the test code', async () => {
      await mount();
      await pass('phone', 'phone', '912345678');

      type('otp', '000000');
      submitForm();
      await flush();
      http.expectNone((r) => r.url.includes('/steps/'));
      expect(errorText('otp')).toContain('incorrecto');
      expect(el.querySelector('#otp-help')?.textContent).toContain('123456');

      await pass('otp', 'otp', '123456');
      expect(currentStep()).toBe('Datos');
    });

    it('blocks a minor on the personal data step', async () => {
      await mount();
      await pass('phone', 'phone', '912345678');
      await pass('otp', 'otp', '123456');

      type('fullName', 'Ana Pérez');
      type('birthDate', '2020-01-01');
      submitForm();
      await flush();

      http.expectNone((r) => r.url.includes('/steps/'));
      expect(errorText('birthDate')).toContain('mayor de edad');
      expect(currentStep()).toBe('Datos');
    });

    it('blocks a document without the TEST- prefix', async () => {
      await mount();
      await reachDocumentStep();

      type('document', '12345678');
      submitForm();
      await flush();

      http.expectNone((r) => r.url.includes('/kyc'));
      expect(errorText('document')).toContain('TEST-');
    });
  });

  describe('submission', () => {
    it('sends a single request when the button is pressed twice', async () => {
      await mount();
      await reachDocumentStep();

      type('document', 'TEST-001');
      submitForm();
      submitForm();
      (await next('/api/onboarding/runs/r1/steps/document', 'POST')).flush(null, NO_CONTENT);
      (await next('/api/onboarding/runs/r1/kyc', 'POST')).flush(approvedRun());
      await flush();

      http.expectNone('/api/onboarding/runs/r1/kyc');
      expect(el.querySelector('app-result')).not.toBeNull();
    });

    it('disables the button while sending', async () => {
      await mount();
      await reachDocumentStep();

      type('document', 'TEST-001');
      submitForm();
      await flush();

      const button = el.querySelector<HTMLButtonElement>('button[type="submit"]')!;
      expect(button.disabled).toBe(true);
      expect(button.textContent).toContain('Enviando');

      (await next('/api/onboarding/runs/r1/steps/document', 'POST')).flush(null, NO_CONTENT);
      (await next('/api/onboarding/runs/r1/kyc', 'POST')).flush(approvedRun());
      await flush();
    });

    it('keeps the entered data and lets the user retry after KYC_UNAVAILABLE', async () => {
      await mount();
      await reachDocumentStep();

      type('document', 'TEST-001');
      submitForm();
      (await next('/api/onboarding/runs/r1/steps/document', 'POST')).flush(null, NO_CONTENT);
      (await next('/api/onboarding/runs/r1/kyc', 'POST')).flush({ error: 'KYC_UNAVAILABLE' }, { status: 503, statusText: 'Unavailable' });
      await flush();

      expect(el.querySelector('.alert')?.textContent).toContain('no está disponible');
      expect(el.querySelector<HTMLInputElement>('#document')!.value).toBe('TEST-001');
      expect(el.querySelector<HTMLButtonElement>('button[type="submit"]')!.disabled).toBe(false);

      submitForm();
      (await next('/api/onboarding/runs/r1/kyc', 'POST')).flush(approvedRun());
      await flush();

      expect(el.querySelector('app-result')).not.toBeNull();
    });
  });

  describe('demo mode', () => {
    it('shows the scenario selector only in demo mode', async () => {
      await mount(true);
      await reachDocumentStep();
      expect(el.querySelector('#scenario')).not.toBeNull();
    });

    it('hides the scenario selector outside demo mode', async () => {
      await mount(false);
      await reachDocumentStep();
      expect(el.querySelector('#scenario')).toBeNull();
    });
  });
});
