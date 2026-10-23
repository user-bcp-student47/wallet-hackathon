import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { EventExport, RunSummary, SummaryView } from '../core/models';
import { SummaryComponent } from './summary.component';

function runSummary(id: string, overrides: Partial<RunSummary> = {}): RunSummary {
  return {
    runId: id,
    startedAt: '2026-01-01T10:00:00Z',
    status: 'APPROVED',
    flowDuration: { millis: 125_000, state: 'MEASURED' },
    kycReview: { millis: 800, state: 'MEASURED' },
    activation: { millis: 800, state: 'MEASURED' },
    ...overrides,
  };
}

describe('SummaryComponent', () => {
  let fixture: ComponentFixture<SummaryComponent>;
  let http: HttpTestingController;
  let el: HTMLElement;

  async function mount(demo: boolean, summary?: SummaryView): Promise<void> {
    fixture = TestBed.createComponent(SummaryComponent);
    el = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
    (await vi.waitFor(() => http.expectOne('/api/config'))).flush({ demoEnabled: demo });
    if (demo) {
      (await vi.waitFor(() => http.expectOne('/api/onboarding/summary'))).flush(summary!);
    }
    await fixture.whenStable();
    fixture.detectChanges();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('shows each run with flow, KYC review and activation in separate columns', async () => {
    await mount(true, {
      referenceMillis: 180_000,
      medianFlowMillis: 125_000,
      measuredRuns: 1,
      runs: [
        runSummary('aaaaaaaa-1111'),
        runSummary('bbbbbbbb-2222', {
          status: 'MANUAL_REVIEW',
          kycReview: { millis: null, state: 'PENDING' },
          activation: { millis: null, state: 'PENDING' },
        }),
        runSummary('cccccccc-3333', {
          status: 'REJECTED',
          activation: { millis: null, state: 'NOT_AVAILABLE' },
        }),
      ],
    });

    const headers = Array.from(el.querySelectorAll('th')).map((h) => h.textContent?.trim());
    expect(headers).toEqual(['Corrida', 'Inicio', 'Resultado', 'Duración del flujo', 'Revisión KYC', 'Hasta la activación']);

    const rows = Array.from(el.querySelectorAll('tbody tr')).map((tr) =>
      Array.from(tr.querySelectorAll('td')).map((td) => td.textContent?.trim()),
    );
    expect(rows[0]).toEqual(['aaaaaaaa', '2026-01-01 10:00:00', 'APPROVED', '2 min 5 s', '800 ms', '800 ms']);
    expect(rows[1].slice(4)).toEqual(['Pendiente', 'Pendiente']);
    expect(rows[2][5]).toBe('—');
  });

  it('shows the median with the number of runs and the 3-minute reference', async () => {
    await mount(true, { referenceMillis: 180_000, medianFlowMillis: 125_000, measuredRuns: 4, runs: [runSummary('a')] });

    const text = el.querySelector('.median')?.textContent ?? '';
    expect(text).toContain('2 min 5 s');
    expect(text).toContain('4 corridas');
    expect(text).toContain('3 min 0 s');
  });

  it('says there is not enough data when no run has an accepted submission', async () => {
    await mount(true, { referenceMillis: 180_000, medianFlowMillis: null, measuredRuns: 0, runs: [] });

    expect(el.textContent).toContain('Aún no hay corridas');
    expect(el.textContent).toContain('Sin corridas registradas');
  });

  it('does not include checkout, MAU or CAC metrics', async () => {
    await mount(true, { referenceMillis: 180_000, medianFlowMillis: 1000, measuredRuns: 1, runs: [runSummary('a')] });

    const text = (el.textContent ?? '').toLowerCase();
    for (const forbidden of ['checkout', 'mau', 'cac', 'usuarios activos', 'costo de adquisición']) {
      expect(text).not.toContain(forbidden);
    }
  });

  it('is unavailable outside demo mode and does not request the summary', async () => {
    await mount(false);

    expect(el.textContent).toContain('solo está disponible en modo demo');
    http.expectNone('/api/onboarding/summary');
  });

  it('exports the events as JSON without personal data', async () => {
    await mount(true, { referenceMillis: 180_000, medianFlowMillis: 1000, measuredRuns: 1, runs: [runSummary('a')] });
    const events: EventExport[] = [
      { runId: 'a', type: 'onboarding_started', at: '2026-01-01T10:00:00Z', meta: null },
      { runId: 'a', type: 'step_completed', at: '2026-01-01T10:00:10Z', meta: 'phone' },
    ];
    let exported: Blob | undefined;
    const createUrl = vi.spyOn(URL, 'createObjectURL').mockImplementation((blob) => {
      exported = blob as Blob;
      return 'blob:test';
    });
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);

    el.querySelector<HTMLButtonElement>('button')!.click();
    (await vi.waitFor(() => http.expectOne('/api/onboarding/events'))).flush(events);
    await vi.waitFor(() => expect(createUrl).toHaveBeenCalled());

    const content = await exported!.text();
    expect(JSON.parse(content)).toEqual(events);
    expect(content).not.toMatch(/912345678|TEST-|fullName|birth/i);
    vi.restoreAllMocks();
  });
});
