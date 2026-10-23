package pe.wallet.onboarding.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import pe.wallet.onboarding.domain.KycDecision;
import pe.wallet.onboarding.domain.KycScenario;
import pe.wallet.onboarding.domain.RunSummary;

public final class OnboardingDtos {

    private OnboardingDtos() {}

    public record SubmitKycRequest(
            @NotBlank(message = "El documento es obligatorio")
            @Size(max = 200, message = "El documento es demasiado largo")
            @Pattern(regexp = "^TEST-.*", message = "El documento debe empezar por TEST-")
            String document,
            KycScenario scenario) {}

    public record DecisionRequest(@NotNull(message = "La decisión es obligatoria") KycDecision decision) {}

    public record StartRunResponse(String runId, Instant startedAt) {}

    public record VerificationView(String id, String walletId, String outcome, boolean walletActive) {}

    public record RunView(
            String runId,
            Instant startedAt,
            String status,
            List<String> completedSteps,
            VerificationView verification,
            RunSummary metrics) {}

    public record SummaryView(long referenceMillis, Long medianFlowMillis, int measuredRuns, List<RunSummary> runs) {}

    public record EventExport(String runId, String type, Instant at, String meta) {}
}
