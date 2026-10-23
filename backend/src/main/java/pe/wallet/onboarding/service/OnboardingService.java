package pe.wallet.onboarding.service;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import pe.wallet.onboarding.api.ApiException;
import pe.wallet.onboarding.api.OnboardingDtos.EventExport;
import pe.wallet.onboarding.api.OnboardingDtos.RunView;
import pe.wallet.onboarding.api.OnboardingDtos.SummaryView;
import pe.wallet.onboarding.api.OnboardingDtos.VerificationView;
import pe.wallet.onboarding.client.KycException;
import pe.wallet.onboarding.client.KycGateway;
import pe.wallet.onboarding.config.OnboardingConfig;
import pe.wallet.onboarding.domain.EventType;
import pe.wallet.onboarding.domain.EventView;
import pe.wallet.onboarding.domain.KycDecision;
import pe.wallet.onboarding.domain.KycOutcome;
import pe.wallet.onboarding.domain.KycScenario;
import pe.wallet.onboarding.domain.KycVerification;
import pe.wallet.onboarding.domain.RunEntity;
import pe.wallet.onboarding.domain.RunMetrics;
import pe.wallet.onboarding.domain.RunSummary;

@ApplicationScoped
public class OnboardingService {

    public static final long REFERENCE_MILLIS = 3 * 60 * 1000L;
    private static final Set<String> STEPS = Set.of("phone", "otp", "personal", "document");

    private final RunService runs;
    private final KycGateway gateway;
    private final OnboardingConfig config;
    private final Set<String> submitting = ConcurrentHashMap.newKeySet();

    public OnboardingService(RunService runs, KycGateway gateway, OnboardingConfig config) {
        this.runs = runs;
        this.gateway = gateway;
        this.config = config;
    }

    public RunEntity start() {
        return runs.start();
    }

    public void completeStep(String runId, String step) {
        if (!STEPS.contains(step)) {
            throw ApiException.invalidStep();
        }
        requireOpenRun(runId);
        runs.record(runId, EventType.STEP_COMPLETED, step);
    }

    public RunView submitKyc(String runId, String document, KycScenario requestedScenario) {
        runs.find(runId).orElseThrow(ApiException::runNotFound);
        if (runs.hasEvent(runId, EventType.KYC_SUBMIT_ACCEPTED)) {
            throw ApiException.alreadySubmitted();
        }
        if (runs.isClosed(runId)) {
            throw ApiException.runClosed();
        }
        if (!submitting.add(runId)) {
            throw ApiException.submitInProgress();
        }
        try {
            KycScenario scenario = config.demo().enabled() && requestedScenario != null ? requestedScenario : KycScenario.APPROVED;
            Instant sentAt = runs.now();
            KycVerification verification;
            try {
                verification = gateway.create(document, scenario);
            } catch (KycException e) {
                runs.record(runId, EventType.KYC_SUBMIT_FAILED, e.code());
                throw e;
            }
            runs.recordSubmissionAccepted(runId, verification, sentAt);
        } finally {
            submitting.remove(runId);
        }
        return view(runId);
    }

    public RunView decide(String runId, KycDecision decision) {
        requireDemo();
        RunEntity run = runs.find(runId).orElseThrow(ApiException::runNotFound);
        if (run.verificationId == null) {
            throw ApiException.noVerification();
        }
        KycVerification verification = gateway.decide(run.verificationId, decision);
        KycOutcome outcome = verification.outcome();
        if (outcome != KycOutcome.MANUAL_REVIEW) {
            runs.recordDecision(runId, outcome);
        }
        return view(runId);
    }

    public void close(String runId) {
        runs.find(runId).orElseThrow(ApiException::runNotFound);
        runs.closeAsAbandoned(runId);
    }

    public RunView view(String runId) {
        RunEntity run = runs.find(runId).orElseThrow(ApiException::runNotFound);
        List<EventView> events = runs.events(runId);
        RunSummary summary = RunMetrics.summarize(runId, events);

        List<String> steps = new ArrayList<>();
        for (EventView e : events) {
            if (e.type() == EventType.STEP_COMPLETED && !steps.contains(e.meta())) {
                steps.add(e.meta());
            }
        }

        VerificationView verification = null;
        if (run.verificationId != null) {
            String outcome = events.stream()
                    .filter(e -> e.type() == EventType.KYC_DECIDED)
                    .map(EventView::meta)
                    .findFirst()
                    .orElse(KycOutcome.MANUAL_REVIEW.name());
            boolean active = events.stream().anyMatch(e -> e.type() == EventType.WALLET_ACTIVATED);
            verification = new VerificationView(run.verificationId, run.walletId, outcome, active);
        }
        return new RunView(runId, summary.startedAt(), summary.status(), steps, verification, summary);
    }

    public SummaryView summary() {
        requireDemo();
        List<RunSummary> all = runs.summaries();
        int measured = (int) all.stream().filter(s -> s.flowDuration().millis() != null).count();
        return new SummaryView(REFERENCE_MILLIS, RunMetrics.medianFlowMillis(all).orElse(null), measured, all);
    }

    public List<EventExport> events() {
        requireDemo();
        return runs.allEvents().stream()
                .map(e -> new EventExport(e.runId, e.type.wireName(), e.occurredAt, e.meta))
                .toList();
    }

    private void requireOpenRun(String runId) {
        runs.find(runId).orElseThrow(ApiException::runNotFound);
        if (runs.isClosed(runId)) {
            throw ApiException.runClosed();
        }
    }

    private void requireDemo() {
        if (!config.demo().enabled()) {
            throw ApiException.demoDisabled();
        }
    }
}
