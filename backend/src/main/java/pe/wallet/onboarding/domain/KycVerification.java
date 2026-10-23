package pe.wallet.onboarding.domain;

public record KycVerification(String id, String walletId, String status) {

    public KycOutcome outcome() {
        return KycOutcome.fromMockStatus(status);
    }
}
