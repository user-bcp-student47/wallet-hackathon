package pe.wallet.onboarding.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RunMetricsTest {

    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");

    private static EventView ev(EventType type, long secondsAfterStart) {
        return new EventView(type, T0.plusSeconds(secondsAfterStart), null);
    }

    private static EventView ev(EventType type, long secondsAfterStart, String meta) {
        return new EventView(type, T0.plusSeconds(secondsAfterStart), meta);
    }

    private static void assertMeasured(Metric metric, long millis) {
        assertEquals(Metric.State.MEASURED, metric.state());
        assertEquals(millis, metric.millis());
    }

    @Test
    void immediateApproval() {
        RunSummary s = RunMetrics.summarize("r1", List.of(
                ev(EventType.ONBOARDING_STARTED, 0),
                ev(EventType.KYC_SUBMIT_ACCEPTED, 120),
                ev(EventType.KYC_DECIDED, 121, "APPROVED"),
                ev(EventType.WALLET_ACTIVATED, 121),
                ev(EventType.FLOW_CLOSED, 121, "APPROVED")));

        assertMeasured(s.flowDuration(), 120_000);
        assertMeasured(s.kycReview(), 1_000);
        assertMeasured(s.activation(), 1_000);
        assertEquals("APPROVED", s.status());
        assertEquals(T0, s.startedAt());
    }

    @Test
    void manualReviewResolvedToActive() {
        RunSummary s = RunMetrics.summarize("r2", List.of(
                ev(EventType.ONBOARDING_STARTED, 0),
                ev(EventType.KYC_SUBMIT_ACCEPTED, 100),
                ev(EventType.KYC_DECIDED, 400, "APPROVED"),
                ev(EventType.WALLET_ACTIVATED, 400),
                ev(EventType.FLOW_CLOSED, 400, "APPROVED")));

        assertMeasured(s.flowDuration(), 100_000);
        assertMeasured(s.kycReview(), 300_000);
        assertMeasured(s.activation(), 300_000);
    }

    @Test
    void rejectionHasNoActivation() {
        RunSummary s = RunMetrics.summarize("r3", List.of(
                ev(EventType.ONBOARDING_STARTED, 0),
                ev(EventType.KYC_SUBMIT_ACCEPTED, 90),
                ev(EventType.KYC_DECIDED, 91, "REJECTED"),
                ev(EventType.FLOW_CLOSED, 91, "REJECTED")));

        assertMeasured(s.flowDuration(), 90_000);
        assertMeasured(s.kycReview(), 1_000);
        assertEquals(Metric.State.NOT_AVAILABLE, s.activation().state());
        assertEquals("REJECTED", s.status());
    }

    @Test
    void pendingReviewKeepsKycAndActivationPending() {
        RunSummary s = RunMetrics.summarize("r4", List.of(
                ev(EventType.ONBOARDING_STARTED, 0),
                ev(EventType.KYC_SUBMIT_ACCEPTED, 80)));

        assertMeasured(s.flowDuration(), 80_000);
        assertEquals(Metric.State.PENDING, s.kycReview().state());
        assertEquals(Metric.State.PENDING, s.activation().state());
        assertEquals(RunMetrics.MANUAL_REVIEW, s.status());
    }

    @Test
    void retryAfterUnavailableKeepsRetriesInsideFlowDuration() {
        RunSummary s = RunMetrics.summarize("r5", List.of(
                ev(EventType.ONBOARDING_STARTED, 0),
                ev(EventType.KYC_SUBMIT_FAILED, 60, "KYC_UNAVAILABLE"),
                ev(EventType.KYC_SUBMIT_ACCEPTED, 150),
                ev(EventType.KYC_DECIDED, 151, "APPROVED"),
                ev(EventType.WALLET_ACTIVATED, 151)));

        assertMeasured(s.flowDuration(), 150_000);
        assertMeasured(s.kycReview(), 1_000);
    }

    @Test
    void runWithoutAcceptedSubmitHasNoMeasures() {
        RunSummary s = RunMetrics.summarize("r6", List.of(
                ev(EventType.ONBOARDING_STARTED, 0),
                ev(EventType.STEP_COMPLETED, 10, "phone")));

        assertEquals(Metric.State.NOT_AVAILABLE, s.flowDuration().state());
        assertEquals(Metric.State.NOT_AVAILABLE, s.kycReview().state());
        assertEquals(Metric.State.NOT_AVAILABLE, s.activation().state());
        assertEquals(RunMetrics.IN_PROGRESS, s.status());
    }

    @Test
    void eventOrderDoesNotMatter() {
        RunSummary s = RunMetrics.summarize("r7", List.of(
                ev(EventType.KYC_SUBMIT_ACCEPTED, 50),
                ev(EventType.ONBOARDING_STARTED, 0)));

        assertMeasured(s.flowDuration(), 50_000);
    }

    private static RunSummary completed(String id, long flowSeconds) {
        return RunMetrics.summarize(id, List.of(
                ev(EventType.ONBOARDING_STARTED, 0),
                ev(EventType.KYC_SUBMIT_ACCEPTED, flowSeconds)));
    }

    @Test
    void medianOfOddNumberOfRuns() {
        List<RunSummary> runs = List.of(completed("a", 300), completed("b", 100), completed("c", 200));
        assertEquals(Optional.of(200_000L), RunMetrics.medianFlowMillis(runs));
    }

    @Test
    void medianOfEvenNumberOfRunsAveragesMiddleValues() {
        List<RunSummary> runs = List.of(completed("a", 100), completed("b", 200), completed("c", 300), completed("d", 400));
        assertEquals(Optional.of(250_000L), RunMetrics.medianFlowMillis(runs));
    }

    @Test
    void medianWithoutCompletedRuns() {
        assertTrue(RunMetrics.medianFlowMillis(List.of()).isEmpty());
    }

    @Test
    void medianExcludesRunsAbandonedBeforeAcceptedSubmit() {
        RunSummary abandoned = RunMetrics.summarize("x", List.of(
                ev(EventType.ONBOARDING_STARTED, 0),
                ev(EventType.FLOW_CLOSED, 999, "ABANDONED")));
        List<RunSummary> runs = List.of(completed("a", 100), completed("b", 300), abandoned);

        assertEquals("ABANDONED", abandoned.status());
        assertEquals(Optional.of(200_000L), RunMetrics.medianFlowMillis(runs));
    }
}
