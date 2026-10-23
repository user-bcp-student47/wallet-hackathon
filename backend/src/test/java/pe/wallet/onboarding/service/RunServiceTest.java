package pe.wallet.onboarding.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import pe.wallet.onboarding.domain.EventType;
import pe.wallet.onboarding.domain.EventView;
import pe.wallet.onboarding.domain.RunEntity;

@QuarkusTest
class RunServiceTest {

    @Inject
    RunService service;

    @Inject
    EntityManager em;

    @Test
    void startRegistersRunAndStartEventWithServerTime() {
        Instant before = Instant.now();
        RunEntity run = service.start();
        Instant after = Instant.now();

        List<EventView> events = service.events(run.id);
        assertEquals(1, events.size());
        assertEquals(EventType.ONBOARDING_STARTED, events.get(0).type());
        assertFalse(events.get(0).at().isBefore(before.minusSeconds(1)));
        assertFalse(events.get(0).at().isAfter(after.plusSeconds(1)));
    }

    @Test
    void eventsAndRunsAreReadBackFromTheDatabase() {
        RunEntity run = service.start();
        service.record(run.id, EventType.STEP_COMPLETED, "phone");
        service.setVerification(run.id, "kyc-1", "w-1");

        RunEntity stored = service.find(run.id).orElseThrow();
        assertEquals("kyc-1", stored.verificationId);
        assertEquals("w-1", stored.walletId);
        assertNotNull(stored.createdAt);

        List<EventView> events = service.events(run.id);
        assertEquals(List.of(EventType.ONBOARDING_STARTED, EventType.STEP_COMPLETED), events.stream().map(EventView::type).toList());
        assertEquals("phone", events.get(1).meta());
        assertTrue(service.hasEvent(run.id, EventType.STEP_COMPLETED));
        assertFalse(service.hasEvent(run.id, EventType.KYC_SUBMIT_ACCEPTED));
    }

    @Test
    void summariesIncludeEveryRun() {
        RunEntity run = service.start();
        assertTrue(service.summaries().stream().anyMatch(s -> s.runId().equals(run.id)));
    }

    @Test
    @Transactional
    void tablesHaveNoPersonalDataColumns() {
        @SuppressWarnings("unchecked")
        List<String> columns = em.createNativeQuery(
                        "select lower(column_name) from information_schema.columns "
                                + "where lower(table_name) in ('onboarding_run', 'onboarding_event')")
                .getResultList();

        assertEquals(
                Set.of("id", "created_at", "verification_id", "wallet_id", "run_id", "event_type", "occurred_at", "meta"),
                Set.copyOf(columns));
    }
}
