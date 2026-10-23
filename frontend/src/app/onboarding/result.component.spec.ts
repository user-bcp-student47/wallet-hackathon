import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FlowService } from '../core/flow.service';
import { RunView, VerificationView } from '../core/models';
import { ResultComponent } from './result.component';

function run(outcome: VerificationView['outcome'], walletActive = outcome === 'APPROVED'): RunView {
  return {
    runId: 'r1',
    startedAt: '2026-01-01T10:00:00Z',
    status: outcome,
    completedSteps: ['phone', 'otp', 'personal', 'document'],
    verification: { id: 'k1', walletId: 'wallet-123', outcome, walletActive },
    metrics: {} as RunView['metrics'],
  };
}

describe('ResultComponent', () => {
  let fixture: ComponentFixture<ResultComponent>;
  let flow: FlowService;
  let http: HttpTestingController;
  let el: HTMLElement;

  async function render(view: RunView, demo = false): Promise<void> {
    flow.run.set(view);
    flow.demo.set(demo);
    flow.phase.set('result');
    fixture = TestBed.createComponent(ResultComponent);
    el = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
    await fixture.whenStable();
  }

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    flow = TestBed.inject(FlowService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('shows an approved registration with an active wallet and no financial actions', async () => {
    await render(run('APPROVED'));

    expect(el.querySelector('h1')?.textContent).toContain('Registro aprobado');
    expect(el.textContent).toContain('APPROVED');
    expect(el.textContent).toContain('wallet está activa');
    expect(el.textContent).toContain('wallet-123');
    const buttons = Array.from(el.querySelectorAll('button')).map((b) => b.textContent?.trim());
    expect(buttons).toEqual(['Empezar de nuevo']);
  });

  it('shows manual review as pending with an inactive wallet', async () => {
    await render(run('MANUAL_REVIEW', false));

    expect(el.querySelector('h1')?.textContent).toContain('Revisión manual');
    expect(el.textContent).toContain('MANUAL_REVIEW');
    expect(el.textContent).toContain('aún no está activa');
  });

  it('shows a rejected registration without presenting the wallet as active', async () => {
    await render(run('REJECTED', false));

    expect(el.querySelector('h1')?.textContent).toContain('Registro rechazado');
    expect(el.textContent).toContain('REJECTED');
    expect(el.textContent).toContain('no está activa');
    expect(el.textContent).not.toContain('wallet está activa');
  });

  it('offers manual resolution actions only in demo mode', async () => {
    await render(run('MANUAL_REVIEW', false), true);
    const demoButtons = Array.from(el.querySelectorAll('button')).map((b) => b.textContent?.trim());
    expect(demoButtons).toContain('Resolver: ACTIVE');
    expect(demoButtons).toContain('Resolver: REJECTED');

    fixture.destroy();
    await render(run('MANUAL_REVIEW', false), false);
    const buttons = Array.from(el.querySelectorAll('button')).map((b) => b.textContent?.trim());
    expect(buttons).toEqual(['Empezar de nuevo']);
  });

  it('resolves a pending review with ACTIVE and shows the wallet as active', async () => {
    await render(run('MANUAL_REVIEW', false), true);
    (flow as unknown as { runId: string }).runId = 'r1';

    Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.includes('ACTIVE'))!.click();
    const req = await vi.waitFor(() => http.expectOne('/api/onboarding/runs/r1/kyc/decision'));
    expect(req.request.body).toEqual({ decision: 'ACTIVE' });
    req.flush(run('APPROVED'));
    await fixture.whenStable();
    fixture.detectChanges();

    expect(el.querySelector('h1')?.textContent).toContain('Registro aprobado');
  });

  it('resolves a pending review with REJECTED', async () => {
    await render(run('MANUAL_REVIEW', false), true);
    (flow as unknown as { runId: string }).runId = 'r1';

    Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.includes('REJECTED'))!.click();
    const req = await vi.waitFor(() => http.expectOne('/api/onboarding/runs/r1/kyc/decision'));
    expect(req.request.body).toEqual({ decision: 'REJECTED' });
    req.flush(run('REJECTED', false));
    await fixture.whenStable();
    fixture.detectChanges();

    expect(el.querySelector('h1')?.textContent).toContain('Registro rechazado');
  });
});
