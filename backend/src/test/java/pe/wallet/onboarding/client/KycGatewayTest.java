package pe.wallet.onboarding.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.tomakehurst.wiremock.http.Fault;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.wallet.onboarding.WireMockResource;
import pe.wallet.onboarding.domain.KycDecision;
import pe.wallet.onboarding.domain.KycOutcome;
import pe.wallet.onboarding.domain.KycScenario;
import pe.wallet.onboarding.domain.KycVerification;

@QuarkusTest
@WithTestResource(WireMockResource.class)
class KycGatewayTest {

    @Inject
    KycGateway gateway;

    @BeforeEach
    void resetMock() {
        WireMockResource.server().resetAll();
    }

    private static String kyc(String status) {
        return "{\"id\":\"kyc-1\",\"wallet_id\":\"w-1\",\"scenario\":\"APPROVED\",\"status\":\"" + status + "\"}";
    }

    private static void stubCreate(int status, String body) {
        WireMockResource.server().stubFor(post(urlEqualTo("/kyc/verifications"))
                .willReturn(aResponse().withStatus(status).withHeader("Content-Type", "application/json").withBody(body)));
    }

    private KycException createFails() {
        return assertThrows(KycException.class, () -> gateway.create("TEST-001", KycScenario.APPROVED));
    }

    @Test
    void mapsMockStatusToOutcomeOnCreate() {
        stubCreate(201, kyc("ACTIVE"));
        KycVerification approved = gateway.create("TEST-001", KycScenario.APPROVED);
        assertEquals(KycOutcome.APPROVED, approved.outcome());
        assertEquals("kyc-1", approved.id());
        assertEquals("w-1", approved.walletId());

        stubCreate(201, kyc("PENDING"));
        assertEquals(KycOutcome.MANUAL_REVIEW, gateway.create("TEST-001", KycScenario.MANUAL_REVIEW).outcome());

        stubCreate(201, kyc("REJECTED"));
        assertEquals(KycOutcome.REJECTED, gateway.create("TEST-001", KycScenario.REJECTED).outcome());
    }

    @Test
    void getsAndDecidesVerification() {
        WireMockResource.server().stubFor(get(urlEqualTo("/kyc/verifications/kyc-1"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(kyc("PENDING"))));
        WireMockResource.server().stubFor(post(urlEqualTo("/kyc/verifications/kyc-1/decision"))
                .withRequestBody(equalToJson("{\"decision\":\"ACTIVE\"}"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(kyc("ACTIVE"))));

        assertEquals(KycOutcome.MANUAL_REVIEW, gateway.get("kyc-1").outcome());
        assertEquals(KycOutcome.APPROVED, gateway.decide("kyc-1", KycDecision.ACTIVE).outcome());
    }

    @Test
    void mapsKycUnavailable() {
        stubCreate(503, "{\"error\":\"KYC_UNAVAILABLE\"}");
        KycException e = createFails();
        assertEquals(KycException.KYC_UNAVAILABLE, e.code());
        assertEquals(503, e.httpStatus());
    }

    @Test
    void mapsOtherServiceUnavailableToMockError() {
        stubCreate(503, "{\"error\":\"OTHER\"}");
        KycException e = createFails();
        assertEquals(KycException.MOCK_ERROR, e.code());
        assertEquals(502, e.httpStatus());
    }

    @Test
    void mapsBadRequestToMockError() {
        stubCreate(400, "{\"error\":\"INVALID_REQUEST\"}");
        KycException e = createFails();
        assertEquals(KycException.MOCK_ERROR, e.code());
        assertEquals(502, e.httpStatus());
    }

    @Test
    void mapsDecisionConflict() {
        WireMockResource.server().stubFor(post(urlEqualTo("/kyc/verifications/kyc-1/decision"))
                .willReturn(aResponse().withStatus(409).withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"DECISION_CONFLICT\"}")));
        KycException e = assertThrows(KycException.class, () -> gateway.decide("kyc-1", KycDecision.REJECTED));
        assertEquals(KycException.DECISION_CONFLICT, e.code());
        assertEquals(409, e.httpStatus());
    }

    @Test
    void mapsInvalidDecision() {
        WireMockResource.server().stubFor(post(urlEqualTo("/kyc/verifications/kyc-1/decision"))
                .willReturn(aResponse().withStatus(400).withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"INVALID_DECISION\"}")));
        KycException e = assertThrows(KycException.class, () -> gateway.decide("kyc-1", KycDecision.ACTIVE));
        assertEquals(KycException.INVALID_DECISION, e.code());
        assertEquals(400, e.httpStatus());
    }

    @Test
    void mapsNotFound() {
        WireMockResource.server().stubFor(get(urlEqualTo("/kyc/verifications/nope"))
                .willReturn(aResponse().withStatus(404).withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"NOT_FOUND\"}")));
        KycException e = assertThrows(KycException.class, () -> gateway.get("nope"));
        assertEquals(KycException.VERIFICATION_NOT_FOUND, e.code());
        assertEquals(404, e.httpStatus());
    }

    @Test
    void mapsGroupLimit() {
        stubCreate(429, "{\"error\":\"GROUP_LIMIT_RESET_REQUIRED\"}");
        KycException e = createFails();
        assertEquals(KycException.MOCK_LIMIT_REACHED, e.code());
        assertEquals(502, e.httpStatus());
    }

    @Test
    void mapsNetworkFailure() {
        WireMockResource.server().stubFor(post(urlEqualTo("/kyc/verifications"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        KycException e = createFails();
        assertEquals(KycException.MOCK_UNREACHABLE, e.code());
        assertEquals(504, e.httpStatus());
    }

    @Test
    void sendsOnlyDocumentTypeDocumentAndScenario() {
        stubCreate(201, kyc("ACTIVE"));
        gateway.create("TEST-001", KycScenario.APPROVED);

        WireMockResource.server().verify(postRequestedFor(urlEqualTo("/kyc/verifications"))
                .withRequestBody(equalToJson(
                        "{\"documentType\":\"TEST\",\"document\":\"TEST-001\",\"scenario\":\"APPROVED\"}", false, false)));
    }

    @Test
    void doesNotLogRequestBody() {
        List<String> messages = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                messages.add(record.getMessage());
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        };
        handler.setLevel(Level.ALL);
        Logger root = Logger.getLogger("");
        Level previous = root.getLevel();
        root.setLevel(Level.ALL);
        root.addHandler(handler);
        try {
            stubCreate(201, kyc("ACTIVE"));
            gateway.create("TEST-SECRET-123", KycScenario.APPROVED);
        } finally {
            root.removeHandler(handler);
            root.setLevel(previous);
        }
        assertFalse(messages.stream().anyMatch(m -> m != null && m.contains("TEST-SECRET-123")));
    }
}
