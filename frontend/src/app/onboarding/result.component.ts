import { Component, inject } from '@angular/core';
import { FlowService } from '../core/flow.service';

@Component({
  selector: 'app-result',
  template: `
    @if (flow.run(); as run) {
      @if (run.verification; as v) {
        <section class="result" aria-live="polite">
          @switch (v.outcome) {
            @case ('APPROVED') {
              <h1 class="result-title ok">Registro aprobado</h1>
              <p>Estado: <strong>APPROVED</strong>. Tu wallet está activa.</p>
              <p class="muted">Wallet: <code>{{ v.walletId }}</code></p>
            }
            @case ('MANUAL_REVIEW') {
              <h1 class="result-title pending">Revisión manual</h1>
              <p>Estado: <strong>MANUAL_REVIEW</strong>. Tu registro requiere revisión manual y tu wallet aún no está activa.</p>
              <p class="muted">Wallet: <code>{{ v.walletId }}</code></p>
              @if (flow.demo()) {
                <div class="demo-box" role="group" aria-label="Resolución manual (demo)">
                  <p class="muted">Modo demo: resuelve la revisión manual.</p>
                  <div class="row">
                    <button type="button" class="btn" (click)="flow.decide('ACTIVE')">Resolver: ACTIVE</button>
                    <button type="button" class="btn secondary" (click)="flow.decide('REJECTED')">Resolver: REJECTED</button>
                  </div>
                </div>
              }
            }
            @case ('REJECTED') {
              <h1 class="result-title bad">Registro rechazado</h1>
              <p>Estado: <strong>REJECTED</strong>. Tu registro fue rechazado y tu wallet no está activa.</p>
            }
          }
          @if (flow.error(); as error) {
            <div class="alert" role="alert">{{ error }}</div>
          }
          <button type="button" class="btn secondary" (click)="flow.restart()">Empezar de nuevo</button>
        </section>
      }
    }
  `,
})
export class ResultComponent {
  protected readonly flow = inject(FlowService);
}
