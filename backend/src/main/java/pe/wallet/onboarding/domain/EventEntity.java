package pe.wallet.onboarding.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/** Evento de onboarding. `meta` solo contiene valores no personales (paso, estado, código de error). */
@Entity
@Table(name = "onboarding_event", indexes = @Index(name = "idx_event_run", columnList = "run_id"))
public class EventEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "run_id", nullable = false)
    public String runId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    public EventType type;

    @Column(name = "occurred_at", nullable = false)
    public Instant occurredAt;

    @Column(name = "meta")
    public String meta;

    public EventView toView() {
        return new EventView(type, occurredAt, meta);
    }
}
