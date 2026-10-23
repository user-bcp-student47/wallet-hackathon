import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { formatDuration, formatMetric } from '../core/format';
import { FlowService } from '../core/flow.service';
import { SummaryView } from '../core/models';
import { OnboardingApi } from '../core/onboarding-api';

@Component({
  selector: 'app-summary',
  imports: [RouterLink],
  template: `
    <h1>Resumen de onboarding</h1>
    @if (state() === 'loading') {
      <p class="muted" role="status">Cargando…</p>
    } @else if (state() === 'disabled') {
      <p class="alert" role="alert">El resumen solo está disponible en modo demo.</p>
      <a routerLink="/">Volver al onboarding</a>
    } @else if (state() === 'error') {
      <p class="alert" role="alert">No se pudo cargar el resumen. Intenta nuevamente.</p>
    } @else if (summary(); as s) {
      <section class="median" aria-label="Mediana">
        @if (s.medianFlowMillis !== null) {
          <p>
            Mediana de la duración del flujo: <strong>{{ format(s.medianFlowMillis) }}</strong>
            ({{ s.measuredRuns }} {{ s.measuredRuns === 1 ? 'corrida' : 'corridas' }}).
          </p>
        } @else {
          <p>Aún no hay corridas con envío aceptado para calcular la mediana.</p>
        }
        <p class="muted">Referencia de producto: {{ format(s.referenceMillis) }}.</p>
      </section>

      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th scope="col">Corrida</th>
              <th scope="col">Inicio</th>
              <th scope="col">Resultado</th>
              <th scope="col">Duración del flujo</th>
              <th scope="col">Revisión KYC</th>
              <th scope="col">Hasta la activación</th>
            </tr>
          </thead>
          <tbody>
            @for (r of s.runs; track r.runId) {
              <tr>
                <td><code>{{ r.runId.slice(0, 8) }}</code></td>
                <td>{{ r.startedAt.slice(0, 19).replace('T', ' ') }}</td>
                <td>{{ r.status }}</td>
                <td>{{ metric(r.flowDuration) }}</td>
                <td>{{ metric(r.kycReview) }}</td>
                <td>{{ metric(r.activation) }}</td>
              </tr>
            } @empty {
              <tr>
                <td colspan="6">Sin corridas registradas.</td>
              </tr>
            }
          </tbody>
        </table>
      </div>

      <button type="button" class="btn secondary" (click)="exportEvents()">Exportar eventos (JSON)</button>
    }
  `,
})
export class SummaryComponent implements OnInit {
  private readonly api = inject(OnboardingApi);
  private readonly flow = inject(FlowService);

  protected readonly state = signal<'loading' | 'disabled' | 'error' | 'ready'>('loading');
  protected readonly summary = signal<SummaryView | null>(null);
  protected readonly format = formatDuration;
  protected readonly metric = formatMetric;

  async ngOnInit(): Promise<void> {
    await this.flow.loadConfig();
    if (!this.flow.demo()) {
      this.state.set('disabled');
      return;
    }
    try {
      this.summary.set(await firstValueFrom(this.api.summary()));
      this.state.set('ready');
    } catch {
      this.state.set('error');
    }
  }

  protected async exportEvents(): Promise<void> {
    const events = await firstValueFrom(this.api.events());
    const blob = new Blob([JSON.stringify(events, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'onboarding-events.json';
    link.click();
    URL.revokeObjectURL(url);
  }
}
