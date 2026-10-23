package pe.wallet.onboarding.domain;

import java.time.Instant;

public record RunSummary(
        String runId,
        Instant startedAt,
        String status,
        Metric flowDuration,
        Metric kycReview,
        Metric activation) {}
