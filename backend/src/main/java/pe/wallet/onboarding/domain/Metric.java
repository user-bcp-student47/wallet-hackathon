package pe.wallet.onboarding.domain;

public record Metric(Long millis, State state) {

    public enum State {
        /** Valor calculado. */
        MEASURED,
        /** Aún no ocurre el evento final que lo define. */
        PENDING,
        /** No aplica para la corrida, por ejemplo la activación de un registro rechazado. */
        NOT_AVAILABLE
    }

    public static Metric measured(long millis) {
        return new Metric(millis, State.MEASURED);
    }

    public static Metric pending() {
        return new Metric(null, State.PENDING);
    }

    public static Metric notAvailable() {
        return new Metric(null, State.NOT_AVAILABLE);
    }
}
