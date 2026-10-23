package pe.wallet.onboarding.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Corrida de onboarding. No guarda datos personales ni el documento. */
@Entity
@Table(name = "onboarding_run")
public class RunEntity extends PanacheEntityBase {

    @Id
    public String id;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "verification_id")
    public String verificationId;

    @Column(name = "wallet_id")
    public String walletId;
}
