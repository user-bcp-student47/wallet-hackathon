package pe.wallet.onboarding.api;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static io.restassured.RestAssured.given;

import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import pe.wallet.onboarding.WireMockResource;

/** Utilidades compartidas por las pruebas de la API. */
abstract class OnboardingApiTestBase {

    static final String MOCK_VERIFICATION = "kyc-1";

    @BeforeEach
    void resetMock() {
        WireMockResource.server().resetAll();
    }

    static String kycBody(String status) {
        return "{\"id\":\"" + MOCK_VERIFICATION + "\",\"wallet_id\":\"w-1\",\"scenario\":\"APPROVED\",\"status\":\"" + status + "\"}";
    }

    static void stubCreate(int status, String body) {
        WireMockResource.server().stubFor(post(urlEqualTo("/kyc/verifications"))
                .willReturn(aResponse().withStatus(status).withHeader("Content-Type", "application/json").withBody(body)));
    }

    static void stubDecision(int status, String body) {
        WireMockResource.server().stubFor(post(urlEqualTo("/kyc/verifications/" + MOCK_VERIFICATION + "/decision"))
                .willReturn(aResponse().withStatus(status).withHeader("Content-Type", "application/json").withBody(body)));
    }

    static String startRun() {
        return given().when().post("/api/onboarding/runs").then().statusCode(201).extract().path("runId");
    }

    static ValidatableResponse submit(String runId, String document, String scenario) {
        String body = scenario == null
                ? "{\"document\":\"" + document + "\"}"
                : "{\"document\":\"" + document + "\",\"scenario\":\"" + scenario + "\"}";
        return given().contentType(ContentType.JSON).body(body).when().post("/api/onboarding/runs/" + runId + "/kyc").then();
    }

    static ValidatableResponse decide(String runId, String decision) {
        return given().contentType(ContentType.JSON)
                .body("{\"decision\":\"" + decision + "\"}")
                .when()
                .post("/api/onboarding/runs/" + runId + "/kyc/decision")
                .then();
    }

    static ValidatableResponse getRun(String runId) {
        return given().when().get("/api/onboarding/runs/" + runId).then();
    }

    static List<String> eventTypes(String runId) {
        return given().when().get("/api/onboarding/events").then().statusCode(200).extract().jsonPath()
                .getList("findAll { it.runId == '" + runId + "' }.type");
    }
}
