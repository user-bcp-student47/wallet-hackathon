package pe.wallet.onboarding.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.util.function.Supplier;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import pe.wallet.onboarding.domain.KycDecision;
import pe.wallet.onboarding.domain.KycScenario;
import pe.wallet.onboarding.domain.KycVerification;

/** Única puerta de salida hacia el mock KYC. No registra cuerpos de petición. */
@ApplicationScoped
public class KycGateway {

    private static final String DOCUMENT_TYPE = "TEST";

    private final KycMockClient client;
    private final ObjectMapper mapper;

    public KycGateway(@RestClient KycMockClient client, ObjectMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    public KycVerification create(String document, KycScenario scenario) {
        return parse(call(() -> client.create(new KycMockClient.CreateBody(DOCUMENT_TYPE, document, scenario.name()))), 201);
    }

    public KycVerification get(String verificationId) {
        return parse(call(() -> client.get(verificationId)), 200);
    }

    public KycVerification decide(String verificationId, KycDecision decision) {
        return parse(call(() -> client.decide(verificationId, new KycMockClient.DecisionBody(decision.name()))), 200);
    }

    private Response call(Supplier<Response> request) {
        try {
            return request.get();
        } catch (WebApplicationException e) {
            return e.getResponse();
        } catch (ProcessingException e) {
            throw new KycException(KycException.MOCK_UNREACHABLE, 504, "No se pudo contactar al mock KYC");
        }
    }

    private KycVerification parse(Response response, int... successStatuses) {
        try (response) {
            int status = response.getStatus();
            String body = response.hasEntity() ? response.readEntity(String.class) : "";
            JsonNode json = readJson(body);
            for (int ok : successStatuses) {
                if (status == ok) {
                    return toVerification(json);
                }
            }
            throw toException(status, json.path("error").asText(""));
        } catch (ProcessingException e) {
            throw new KycException(KycException.MOCK_UNREACHABLE, 504, "No se pudo leer la respuesta del mock KYC");
        }
    }

    private JsonNode readJson(String body) {
        if (body == null || body.isBlank()) {
            return mapper.createObjectNode();
        }
        try {
            return mapper.readTree(body);
        } catch (Exception e) {
            return mapper.createObjectNode();
        }
    }

    private KycVerification toVerification(JsonNode json) {
        String id = json.path("id").asText(null);
        String walletId = json.path("wallet_id").asText(null);
        String status = json.path("status").asText(null);
        if (id == null || status == null) {
            throw new KycException(KycException.MOCK_ERROR, 502, "Respuesta inesperada del mock KYC");
        }
        return new KycVerification(id, walletId, status);
    }

    private KycException toException(int status, String errorCode) {
        if (status == 503 && KycException.KYC_UNAVAILABLE.equals(errorCode)) {
            return new KycException(KycException.KYC_UNAVAILABLE, 503, "El servicio KYC no está disponible");
        }
        if (status == 409 && "DECISION_CONFLICT".equals(errorCode)) {
            return new KycException(KycException.DECISION_CONFLICT, 409, "La verificación ya tiene una decisión final distinta");
        }
        if (status == 400 && "INVALID_DECISION".equals(errorCode)) {
            return new KycException(KycException.INVALID_DECISION, 400, "Decisión inválida");
        }
        if (status == 404) {
            return new KycException(KycException.VERIFICATION_NOT_FOUND, 404, "La verificación no existe en el mock");
        }
        if (status == 429) {
            return new KycException(KycException.MOCK_LIMIT_REACHED, 502, "El mock alcanzó su límite y debe reiniciarse");
        }
        return new KycException(KycException.MOCK_ERROR, 502, "El mock KYC respondió con un error (" + status + ")");
    }
}
