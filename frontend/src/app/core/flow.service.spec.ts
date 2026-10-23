import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { FlowService, describeError, firstIncompleteStep } from './flow.service';
import { RunView } from './models';

const RUN_KEY = 'wallet-onboarding-run-id';

function runView(overrides: Partial<RunView> = {}): RunView {
  return {
    runId: 'r1',
    startedAt: '2026-01-01T10:00:00Z',
    status: 'IN_PROGRESS',
    completedSteps: [],
    verification: null,
    metrics: {} as RunView['metrics'],
    ...overrides,
  };
}

describe('FlowService', () => {
  let service: FlowService;
  let http: HttpTestingController;

  const next = (url: string, method = 'GET') =>
    vi.waitFor(() => http.expectOne((r) => r.url === url && r.method === method));

  async function initFresh(demo = false): Promise<void> {
    const done = service.init();
    (await next('/api/config')).flush({ demoEnabled: demo });
    (await next('/api/onboarding/runs', 'POST')).flush({ runId: 'r1', startedAt: '2026-01-01T10:00:00Z' });
    await done;
  }

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(FlowService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });

  it('starts a new run on first load and remembers its id', async () => {
    await initFresh();

    expect(service.phase()).toBe('form');
    expect(service.step()).toBe('phone');
    expect(sessionStorage.getItem(RUN_KEY)).toBe('r1');
  });

  it('resumes at the first incomplete step after a reload', async () => {
    sessionStorage.setItem(RUN_KEY, 'r1');
    const done = service.init();
    (await next('/api/config')).flush({ demoEnabled: false });
    (await next('/api/onboarding/runs/r1')).flush(runView({ completedSteps: ['phone', 'otp'] }));
    await done;

    expect(service.phase()).toBe('form');
    expect(service.step()).toBe('personal');
  });

  it('shows the result when the stored run already has a verification', async () => {
    sessionStorage.setItem(RUN_KEY, 'r1');
    const done = service.init();
    (await next('/api/config')).flush({ demoEnabled: false });
    (await next('/api/onboarding/runs/r1')).flush(
      runView({
        status: 'APPROVED',
        completedSteps: ['phone', 'otp', 'personal', 'document'],
        verification: { id: 'k1', walletId: 'w1', outcome: 'APPROVED', walletActive: true },
      }),
    );
    await done;

    expect(service.phase()).toBe('result');
    expect(service.run()?.verification?.walletId).toBe('w1');
  });

  it('starts a new run when the stored run no longer exists', async () => {
    sessionStorage.setItem(RUN_KEY, 'old');
    const done = service.init();
    (await next('/api/config')).flush({ demoEnabled: false });
    (await next('/api/onboarding/runs/old')).flush({ error: 'RUN_NOT_FOUND' }, { status: 404, statusText: 'Not Found' });
    (await next('/api/onboarding/runs', 'POST')).flush({ runId: 'r2', startedAt: '2026-01-01T10:00:00Z' });
    await done;

    expect(sessionStorage.getItem(RUN_KEY)).toBe('r2');
    expect(service.step()).toBe('phone');
  });

  it('starts a new run when the stored run was abandoned', async () => {
    sessionStorage.setItem(RUN_KEY, 'r1');
    const done = service.init();
    (await next('/api/config')).flush({ demoEnabled: false });
    (await next('/api/onboarding/runs/r1')).flush(runView({ status: 'ABANDONED' }));
    (await next('/api/onboarding/runs', 'POST')).flush({ runId: 'r3', startedAt: '2026-01-01T10:00:00Z' });
    await done;

    expect(sessionStorage.getItem(RUN_KEY)).toBe('r3');
  });

  it('never sends phone, name or birth date to the backend', async () => {
    await initFresh();
    const sent: string[] = [];

    for (const step of ['phone', 'otp', 'personal'] as const) {
      const p = service.completeStep(step);
      const req = await next(`/api/onboarding/runs/r1/steps/${step}`, 'POST');
      sent.push(JSON.stringify(req.request.body));
      req.flush(null, { status: 204, statusText: 'No Content' });
      await p;
    }
    expect(service.step()).toBe('document');

    const submitting = service.submit('TEST-001');
    const docStep = await next('/api/onboarding/runs/r1/steps/document', 'POST');
    sent.push(JSON.stringify(docStep.request.body));
    docStep.flush(null, { status: 204, statusText: 'No Content' });
    const kyc = await next('/api/onboarding/runs/r1/kyc', 'POST');
    expect(kyc.request.body).toEqual({ document: 'TEST-001' });
    kyc.flush(runView({ status: 'APPROVED' }));
    await submitting;

    expect(sent.every((body) => body === 'null')).toBe(true);
  });

  it('sends the chosen scenario only in demo mode', async () => {
    await initFresh(true);
    service.scenario.set('MANUAL_REVIEW');

    const submitting = service.submit('TEST-001');
    (await next('/api/onboarding/runs/r1/steps/document', 'POST')).flush(null, { status: 204, statusText: 'No Content' });
    const kyc = await next('/api/onboarding/runs/r1/kyc', 'POST');
    expect(kyc.request.body).toEqual({ document: 'TEST-001', scenario: 'MANUAL_REVIEW' });
    kyc.flush(runView({ status: 'MANUAL_REVIEW' }));
    await submitting;
  });

  it('ignores a second submit while one is in flight', async () => {
    await initFresh();

    const first = service.submit('TEST-001');
    const second = service.submit('TEST-001');
    await second;
    (await next('/api/onboarding/runs/r1/steps/document', 'POST')).flush(null, { status: 204, statusText: 'No Content' });
    (await next('/api/onboarding/runs/r1/kyc', 'POST')).flush(runView({ status: 'APPROVED' }));
    await first;

    expect(service.phase()).toBe('result');
  });

  it('keeps the form and allows a retry after KYC_UNAVAILABLE', async () => {
    await initFresh();

    const first = service.submit('TEST-001');
    (await next('/api/onboarding/runs/r1/steps/document', 'POST')).flush(null, { status: 204, statusText: 'No Content' });
    (await next('/api/onboarding/runs/r1/kyc', 'POST')).flush({ error: 'KYC_UNAVAILABLE' }, { status: 503, statusText: 'Unavailable' });
    await first;

    expect(service.phase()).toBe('form');
    expect(service.error()).toContain('no está disponible');

    const retry = service.submit('TEST-001');
    // El paso de documento ya quedó registrado: el reintento solo envía el KYC.
    (await next('/api/onboarding/runs/r1/kyc', 'POST')).flush(runView({ status: 'APPROVED' }));
    await retry;

    expect(service.phase()).toBe('result');
    expect(service.error()).toBeNull();
  });

  it('restart closes the current run and starts another', async () => {
    await initFresh();

    const restarting = service.restart();
    (await next('/api/onboarding/runs/r1/close', 'POST')).flush(null, { status: 204, statusText: 'No Content' });
    (await next('/api/onboarding/runs', 'POST')).flush({ runId: 'r9', startedAt: '2026-01-01T10:00:00Z' });
    await restarting;

    expect(sessionStorage.getItem(RUN_KEY)).toBe('r9');
    expect(service.step()).toBe('phone');
  });

  it('decide updates the run and surfaces a conflict without losing state', async () => {
    await initFresh(true);
    const pending = runView({
      status: 'MANUAL_REVIEW',
      verification: { id: 'k1', walletId: 'w1', outcome: 'MANUAL_REVIEW', walletActive: false },
    });
    const submitting = service.submit('TEST-001');
    (await next('/api/onboarding/runs/r1/steps/document', 'POST')).flush(null, { status: 204, statusText: 'No Content' });
    (await next('/api/onboarding/runs/r1/kyc', 'POST')).flush(pending);
    await submitting;

    const conflict = service.decide('REJECTED');
    (await next('/api/onboarding/runs/r1/kyc/decision', 'POST')).flush({ error: 'DECISION_CONFLICT' }, { status: 409, statusText: 'Conflict' });
    (await next('/api/onboarding/runs/r1')).flush({
      ...pending,
      status: 'APPROVED',
      verification: { ...pending.verification!, outcome: 'APPROVED', walletActive: true },
    });
    await conflict;

    expect(service.error()).toContain('decisión final');
    expect(service.run()?.verification?.walletActive).toBe(true);
  });
});

describe('firstIncompleteStep', () => {
  it('returns the first step not completed', () => {
    expect(firstIncompleteStep([])).toBe('phone');
    expect(firstIncompleteStep(['phone'])).toBe('otp');
    expect(firstIncompleteStep(['phone', 'otp', 'personal'])).toBe('document');
    expect(firstIncompleteStep(['phone', 'otp', 'personal', 'document'])).toBe('document');
  });
});

describe('describeError', () => {
  it('maps backend codes to actionable messages', () => {
    expect(describeError({ error: { error: 'KYC_UNAVAILABLE' } }).message).toContain('vuelve a intentar');
    expect(describeError({ error: { error: 'MOCK_ERROR' } }).message).toContain('Intenta nuevamente');
    expect(describeError({ status: 0 }).code).toBe('NETWORK');
    expect(describeError({ error: { error: 'INVALID_REQUEST', message: 'Documento inválido' } }).message).toBe('Documento inválido');
  });
});
