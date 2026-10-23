package pe.wallet.onboarding.service;

import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import pe.wallet.onboarding.domain.EventEntity;
import pe.wallet.onboarding.domain.EventType;
import pe.wallet.onboarding.domain.EventView;
import pe.wallet.onboarding.domain.KycOutcome;
import pe.wallet.onboarding.domain.KycVerification;
import pe.wallet.onboarding.domain.RunEntity;
import pe.wallet.onboarding.domain.RunMetrics;
import pe.wallet.onboarding.domain.RunSummary;

/** Corridas y eventos. Todas las marcas de tiempo son la hora del servidor. */
@ApplicationScoped
@Transactional
public class RunService {

    private final Clock clock = Clock.systemUTC();

    public RunEntity start() {
        RunEntity run = new RunEntity();
        run.id = UUID.randomUUID().toString();
        run.createdAt = Instant.now(clock);
        run.persist();
        addEvent(run.id, EventType.ONBOARDING_STARTED, null, run.createdAt);
        return run;
    }

    public Optional<RunEntity> find(String runId) {
        return RunEntity.<RunEntity>findByIdOptional(runId);
    }

    public void record(String runId, EventType type, String meta) {
        addEvent(runId, type, meta, Instant.now(clock));
    }

    public void setVerification(String runId, String verificationId, String walletId) {
        RunEntity run = RunEntity.findById(runId);
        run.verificationId = verificationId;
        run.walletId = walletId;
    }

    public Instant now() {
        return Instant.now(clock);
    }

    /** t1 se registra en el instante en que se envió la petición que el mock aceptó; el resto, al recibir la respuesta. */
    public void recordSubmissionAccepted(String runId, KycVerification verification, Instant sentAt) {
        setVerification(runId, verification.id(), verification.walletId());
        addEvent(runId, EventType.KYC_SUBMIT_ACCEPTED, null, sentAt);
        if (verification.outcome() != KycOutcome.MANUAL_REVIEW) {
            applyFinalOutcome(runId, verification.outcome(), now());
        }
    }

    /** Registra la decisión final una sola vez; repetir la misma decisión no duplica eventos. */
    public void recordDecision(String runId, KycOutcome outcome) {
        if (!hasEvent(runId, EventType.KYC_DECIDED)) {
            applyFinalOutcome(runId, outcome, now());
        }
    }

    public boolean isClosed(String runId) {
        return hasEvent(runId, EventType.FLOW_CLOSED);
    }

    public void closeAsAbandoned(String runId) {
        if (!isClosed(runId)) {
            addEvent(runId, EventType.FLOW_CLOSED, RunMetrics.ABANDONED, now());
        }
    }

    private void applyFinalOutcome(String runId, KycOutcome outcome, Instant at) {
        addEvent(runId, EventType.KYC_DECIDED, outcome.name(), at);
        if (outcome == KycOutcome.APPROVED) {
            addEvent(runId, EventType.WALLET_ACTIVATED, null, at);
        }
        if (!isClosed(runId)) {
            addEvent(runId, EventType.FLOW_CLOSED, outcome.name(), at);
        }
    }

    public List<EventView> events(String runId) {
        return EventEntity.<EventEntity>list("runId", Sort.by("occurredAt").and("id"), runId).stream()
                .map(EventEntity::toView)
                .toList();
    }

    public boolean hasEvent(String runId, EventType type) {
        return EventEntity.count("runId = ?1 and type = ?2", runId, type) > 0;
    }

    public RunSummary summary(String runId) {
        return RunMetrics.summarize(runId, events(runId));
    }

    public List<RunSummary> summaries() {
        Map<String, List<EventView>> byRun = allEvents().stream()
                .collect(Collectors.groupingBy(e -> e.runId, Collectors.mapping(EventEntity::toView, Collectors.toList())));
        return byRun.entrySet().stream()
                .map(e -> RunMetrics.summarize(e.getKey(), e.getValue()))
                .sorted((a, b) -> b.startedAt().compareTo(a.startedAt()))
                .toList();
    }

    public List<EventEntity> allEvents() {
        return EventEntity.listAll(Sort.by("occurredAt").and("id"));
    }

    private void addEvent(String runId, EventType type, String meta, Instant at) {
        EventEntity event = new EventEntity();
        event.runId = runId;
        event.type = type;
        event.occurredAt = at;
        event.meta = meta;
        event.persist();
    }
}
