package pe.wallet.onboarding.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Cálculo puro de tiempos a partir de eventos.
 * t0 inicio, t1 envío aceptado, t2 decisión final del KYC, t3 wallet activa.
 */
public final class RunMetrics {

    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String MANUAL_REVIEW = "MANUAL_REVIEW";
    public static final String ABANDONED = "ABANDONED";

    private RunMetrics() {}

    public static RunSummary summarize(String runId, List<EventView> events) {
        List<EventView> ordered = events.stream().sorted(Comparator.comparing(EventView::at)).toList();

        Optional<Instant> t0 = first(ordered, EventType.ONBOARDING_STARTED);
        Optional<Instant> t1 = first(ordered, EventType.KYC_SUBMIT_ACCEPTED);
        Optional<Instant> t2 = first(ordered, EventType.KYC_DECIDED);
        Optional<Instant> t3 = first(ordered, EventType.WALLET_ACTIVATED);

        Metric flow = t0.isPresent() && t1.isPresent() ? Metric.measured(between(t0.get(), t1.get())) : Metric.notAvailable();

        Metric kycReview;
        Metric activation;
        if (t1.isEmpty()) {
            kycReview = Metric.notAvailable();
            activation = Metric.notAvailable();
        } else {
            kycReview = t2.map(t -> Metric.measured(between(t1.get(), t))).orElseGet(Metric::pending);
            if (t3.isPresent()) {
                activation = Metric.measured(between(t1.get(), t3.get()));
            } else if (t2.isPresent()) {
                activation = Metric.notAvailable();
            } else {
                activation = Metric.pending();
            }
        }

        return new RunSummary(runId, t0.orElse(null), status(ordered, t1, t2), flow, kycReview, activation);
    }

    /** Mediana de la duración del flujo sobre las corridas que tienen envío aceptado. */
    public static Optional<Long> medianFlowMillis(List<RunSummary> summaries) {
        long[] values = summaries.stream()
                .map(RunSummary::flowDuration)
                .filter(m -> m.state() == Metric.State.MEASURED)
                .mapToLong(Metric::millis)
                .sorted()
                .toArray();
        if (values.length == 0) {
            return Optional.empty();
        }
        int mid = values.length / 2;
        return Optional.of(values.length % 2 == 1 ? values[mid] : (values[mid - 1] + values[mid]) / 2);
    }

    private static String status(List<EventView> events, Optional<Instant> t1, Optional<Instant> t2) {
        return events.stream()
                .filter(e -> e.type() == EventType.FLOW_CLOSED)
                .map(EventView::meta)
                .findFirst()
                .orElseGet(() -> t1.isPresent() && t2.isEmpty() ? MANUAL_REVIEW : IN_PROGRESS);
    }

    private static Optional<Instant> first(List<EventView> events, EventType type) {
        return events.stream().filter(e -> e.type() == type).map(EventView::at).findFirst();
    }

    private static long between(Instant from, Instant to) {
        return Duration.between(from, to).toMillis();
    }
}
