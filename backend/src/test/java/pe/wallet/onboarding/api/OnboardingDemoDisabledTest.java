package pe.wallet.onboarding.api;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pe.wallet.onboarding.WireMockResource;

@QuarkusTest
@WithTestResource(WireMockResource.class)
@TestProfile(OnboardingDemoDisabledTest.DemoOff.class)
class OnboardingDemoDisabledTest extends OnboardingApiTestBase {

    public static class DemoOff implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("onboarding.demo.enabled", "false");
        }
    }

    @Test
    void configReportsDemoDisabled() {
        given().when().get("/api/config").then().statusCode(200).body("demoEnabled", equalTo(false));
    }

    @Test
    void forcesApprovedScenarioWhenDemoIsOff() {
        String id = startRun();
        stubCreate(201, kycBody("ACTIVE"));

        submit(id, "TEST-001", "REJECTED").statusCode(201);

        WireMockResource.server().verify(postRequestedFor(urlEqualTo("/kyc/verifications"))
                .withRequestBody(equalToJson("{\"documentType\":\"TEST\",\"document\":\"TEST-001\",\"scenario\":\"APPROVED\"}", false, false)));
    }

    @Test
    void manualDecisionIsForbiddenWithoutCallingTheMock() {
        String id = startRun();
        stubCreate(201, kycBody("ACTIVE"));
        submit(id, "TEST-001", null).statusCode(201);
        WireMockResource.server().resetRequests();

        decide(id, "ACTIVE").statusCode(403).body("error", equalTo("DEMO_DISABLED"));

        WireMockResource.server().verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void summaryAndEventsAreForbidden() {
        given().when().get("/api/onboarding/summary").then().statusCode(403).body("error", equalTo("DEMO_DISABLED"));
        given().when().get("/api/onboarding/events").then().statusCode(403).body("error", equalTo("DEMO_DISABLED"));
    }
}
