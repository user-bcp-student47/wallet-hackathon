package pe.wallet.onboarding.client;

/** Error del mock KYC traducido a un código estable del backend. */
public class KycException extends RuntimeException {

    public static final String KYC_UNAVAILABLE = "KYC_UNAVAILABLE";
    public static final String DECISION_CONFLICT = "DECISION_CONFLICT";
    public static final String INVALID_DECISION = "INVALID_DECISION";
    public static final String VERIFICATION_NOT_FOUND = "VERIFICATION_NOT_FOUND";
    public static final String MOCK_LIMIT_REACHED = "MOCK_LIMIT_REACHED";
    public static final String MOCK_UNREACHABLE = "MOCK_UNREACHABLE";
    public static final String MOCK_ERROR = "MOCK_ERROR";

    private final String code;
    private final int httpStatus;

    public KycException(String code, int httpStatus, String message) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String code() {
        return code;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
