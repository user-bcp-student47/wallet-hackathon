package pe.wallet.onboarding.domain;

public enum EventType {
    ONBOARDING_STARTED("onboarding_started"),
    STEP_COMPLETED("step_completed"),
    KYC_SUBMIT_FAILED("kyc_submit_failed"),
    KYC_SUBMIT_ACCEPTED("kyc_submit_accepted"),
    KYC_DECIDED("kyc_decided"),
    WALLET_ACTIVATED("wallet_activated"),
    FLOW_CLOSED("flow_closed");

    private final String wireName;

    EventType(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}
