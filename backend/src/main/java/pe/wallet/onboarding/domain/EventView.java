package pe.wallet.onboarding.domain;

import java.time.Instant;

/** Vista inmutable de un evento, usada por el cálculo de métricas. */
public record EventView(EventType type, Instant at, String meta) {}
