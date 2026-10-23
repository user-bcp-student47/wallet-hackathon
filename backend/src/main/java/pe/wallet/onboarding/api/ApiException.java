package pe.wallet.onboarding.api;

/** Error de negocio con código estable para el frontend. */
public class ApiException extends RuntimeException {

    private final String code;
    private final int httpStatus;

    public ApiException(String code, int httpStatus, String message) {
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

    public static ApiException runNotFound() {
        return new ApiException("RUN_NOT_FOUND", 404, "La corrida no existe");
    }

    public static ApiException runClosed() {
        return new ApiException("RUN_CLOSED", 409, "La corrida ya está cerrada");
    }

    public static ApiException alreadySubmitted() {
        return new ApiException("ALREADY_SUBMITTED", 409, "La corrida ya tiene una verificación aceptada");
    }

    public static ApiException submitInProgress() {
        return new ApiException("SUBMIT_IN_PROGRESS", 409, "Ya hay un envío en curso para esta corrida");
    }

    public static ApiException noVerification() {
        return new ApiException("NO_VERIFICATION", 409, "La corrida aún no tiene una verificación aceptada");
    }

    public static ApiException demoDisabled() {
        return new ApiException("DEMO_DISABLED", 403, "El modo demo está desactivado");
    }

    public static ApiException invalidStep() {
        return new ApiException("INVALID_STEP", 400, "Paso desconocido");
    }
}
