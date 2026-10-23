package pe.wallet.onboarding.domain;

/** Resultado de onboarding mostrado al usuario; se deriva del estado que informa el mock. */
public enum KycOutcome {
    APPROVED,
    MANUAL_REVIEW,
    REJECTED;

    public static KycOutcome fromMockStatus(String status) {
        return switch (status) {
            case "ACTIVE" -> APPROVED;
            case "PENDING" -> MANUAL_REVIEW;
            case "REJECTED" -> REJECTED;
            default -> throw new IllegalArgumentException("Estado KYC desconocido: " + status);
        };
    }
}
