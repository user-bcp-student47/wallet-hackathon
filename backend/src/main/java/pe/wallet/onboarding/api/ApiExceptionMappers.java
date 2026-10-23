package pe.wallet.onboarding.api;

import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;
import pe.wallet.onboarding.client.KycException;

public class ApiExceptionMappers {

    public record ErrorBody(String error, String message) {}

    @ServerExceptionMapper
    public Response apiException(ApiException e) {
        return body(e.httpStatus(), e.code(), e.getMessage());
    }

    @ServerExceptionMapper
    public Response kycException(KycException e) {
        return body(e.httpStatus(), e.code(), e.getMessage());
    }

    @ServerExceptionMapper
    public Response validation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(v -> v.getMessage())
                .sorted()
                .findFirst()
                .orElse("Solicitud inválida");
        return body(400, "INVALID_REQUEST", message);
    }

    private static Response body(int status, String code, String message) {
        return Response.status(status).type(MediaType.APPLICATION_JSON).entity(new ErrorBody(code, message)).build();
    }
}
