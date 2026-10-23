package pe.wallet.onboarding.api;

import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.Test;
import pe.wallet.onboarding.WireMockResource;
import pe.wallet.onboarding.domain.EventView;
import pe.wallet.onboarding.domain.RunEntity;
import pe.wallet.onboarding.service.RunService;

@QuarkusTest
@WithTestResource(WireMockResource.class)
class OnboardingApiTest extends OnboardingApiTestBase {

    @Inject
    RunService runService;

    // --- 4.1 corridas, pasos, consulta y cierre ---

    @Test
    void startsRunAndRecordsSteps() {
        String id = startRun();

        given().when().post("/api/onboarding/runs/" + id + "/steps/phone").then().statusCode(204);
        given().when().post("/api/onboarding/runs/" + id + "/steps/otp").then().statusCode(204);
        given().when().post("/api/onboarding/runs/" + id + "/steps/phone").then().statusCode(204);

        getRun(id).statusCode(200)
                .body("runId", equalTo(id))
                .body("startedAt", notNullValue())
                .body("status", equalTo("IN_PROGRESS"))
                .body("completedSteps", equalTo(List.of("phone", "otp")))
                .body("verification", nullValue());
    }

    @Test
    void unknownRunIsNotFound() {
        given().when().get("/api/onboarding/runs/nope").then().statusCode(404).body("error", equalTo("RUN_NOT_FOUND"));
        given().when().post("/api/onboarding/runs/nope/steps/phone").then().statusCode(404).body("error", equalTo("RUN_NOT_FOUND"));
        given().when().post("/api/onboarding/runs/nope/close").then().statusCode(404).body("error", equalTo("RUN_NOT_FOUND"));
    }

    @Test
    void rejectsUnknownStep() {
        String id = startRun();
        given().when().post("/api/onboarding/runs/" + id + "/steps/selfie").then().statusCode(400).body("error", equalTo("INVALID_STEP"));
    }

    @Test
    void closesRunAsAbandonedAndBlocksFurtherSteps() {
        String id = startRun();

        given().when().post("/api/onboarding/runs/" + id + "/close").then().statusCode(204);
        given().when().post("/api/onboarding/runs/" + id + "/close").then().statusCode(204);

        getRun(id).body("status", equalTo("ABANDONED"));
        given().when().post("/api/onboarding/runs/" + id + "/steps/phone").then().statusCode(409).body("error", equalTo("RUN_CLOSED"));
        assertTrue(eventTypes(id).stream().filter("flow_closed"::equals).count() == 1);
    }

    // --- 4.2 envío al KYC ---

    @Test
    void approvedSubmissionActivatesWalletAndClosesRun() {
        String id = startRun();
        stubCreate(201, kycBody("ACTIVE"));

        submit(id, "TEST-001", "APPROVED").statusCode(201)
                .body("status", equalTo("APPROVED"))
                .body("verification.walletId", equalTo("w-1"))
                .body("verification.outcome", equalTo("APPROVED"))
                .body("verification.walletActive", equalTo(true))
                .body("metrics.flowDuration.state", equalTo("MEASURED"))
                .body("metrics.kycReview.state", equalTo("MEASURED"))
                .body("metrics.activation.state", equalTo("MEASURED"));

        assertTrue(eventTypes(id).containsAll(List.of("onboarding_started", "kyc_submit_accepted", "kyc_decided", "wallet_activated", "flow_closed")));
    }

    @Test
    void manualReviewKeepsRunPendingWithoutActiveWallet() {
        String id = startRun();
        stubCreate(201, kycBody("PENDING"));

        submit(id, "TEST-001", "MANUAL_REVIEW").statusCode(201)
                .body("status", equalTo("MANUAL_REVIEW"))
                .body("verification.outcome", equalTo("MANUAL_REVIEW"))
                .body("verification.walletActive", equalTo(false))
                .body("metrics.kycReview.state", equalTo("PENDING"))
                .body("metrics.activation.state", equalTo("PENDING"));
    }

    @Test
    void rejectedSubmissionHasNoActivation() {
        String id = startRun();
        stubCreate(201, kycBody("REJECTED"));

        submit(id, "TEST-001", "REJECTED").statusCode(201)
                .body("status", equalTo("REJECTED"))
                .body("verification.walletActive", equalTo(false))
                .body("metrics.activation.state", equalTo("NOT_AVAILABLE"));
    }

    @Test
    void unavailableThenRetryKeepsFlowOpenAndCountsRetryInsideDuration() {
        String id = startRun();
        stubCreate(503, "{\"error\":\"KYC_UNAVAILABLE\"}");

        submit(id, "TEST-001", "UNAVAILABLE").statusCode(503).body("error", equalTo("KYC_UNAVAILABLE"));
        getRun(id).body("status", equalTo("IN_PROGRESS")).body("verification", nullValue());

        stubCreate(201, kycBody("ACTIVE"));
        submit(id, "TEST-001", "APPROVED").statusCode(201)
                .body("verification.outcome", equalTo("APPROVED"))
                .body("metrics.flowDuration.state", equalTo("MEASURED"));

        List<String> types = eventTypes(id);
        assertTrue(types.contains("kyc_submit_failed"));
        assertTrue(types.contains("kyc_submit_accepted"));
    }

    @Test
    void invalidDocumentIsRejectedWithoutCallingTheMock() {
        String id = startRun();

        submit(id, "ABC-001", null).statusCode(400).body("error", equalTo("INVALID_REQUEST"));
        given().contentType("application/json").body("{}").when().post("/api/onboarding/runs/" + id + "/kyc").then()
                .statusCode(400).body("error", equalTo("INVALID_REQUEST"));

        WireMockResource.server().verify(0, postRequestedFor(urlEqualTo("/kyc/verifications")));
    }

    @Test
    void secondSubmissionAfterAcceptedIsRejectedWithoutCallingTheMock() {
        String id = startRun();
        stubCreate(201, kycBody("PENDING"));
        submit(id, "TEST-001", "MANUAL_REVIEW").statusCode(201);

        submit(id, "TEST-001", "MANUAL_REVIEW").statusCode(409).body("error", equalTo("ALREADY_SUBMITTED"));

        WireMockResource.server().verify(1, postRequestedFor(urlEqualTo("/kyc/verifications")));
    }

    @Test
    void sendsOnlyDocumentTypeDocumentAndScenarioToTheMock() {
        String id = startRun();
        stubCreate(201, kycBody("ACTIVE"));

        submit(id, "TEST-ONLY", "APPROVED").statusCode(201);

        WireMockResource.server().verify(postRequestedFor(urlEqualTo("/kyc/verifications"))
                .withRequestBody(equalToJson("{\"documentType\":\"TEST\",\"document\":\"TEST-ONLY\",\"scenario\":\"APPROVED\"}", false, false)));
    }

    @Test
    void doesNotPersistTheDocumentNumber() {
        String id = startRun();
        stubCreate(201, kycBody("ACTIVE"));
        submit(id, "TEST-SECRET-999", "APPROVED").statusCode(201);

        RunEntity run = runService.find(id).orElseThrow();
        assertFalse((run.verificationId + run.walletId).contains("SECRET"));
        for (EventView event : runService.events(id)) {
            assertFalse(String.valueOf(event.meta()).contains("SECRET"));
        }
        given().when().get("/api/onboarding/events").then().body(not(containsString("TEST-SECRET-999")));
    }

    // --- 4.3 resolución manual ---

    @Test
    void decisionActivatesPendingWallet() {
        String id = startRun();
        stubCreate(201, kycBody("PENDING"));
        submit(id, "TEST-001", "MANUAL_REVIEW").statusCode(201);
        stubDecision(200, kycBody("ACTIVE"));

        decide(id, "ACTIVE").statusCode(200)
                .body("status", equalTo("APPROVED"))
                .body("verification.walletActive", equalTo(true))
                .body("metrics.kycReview.state", equalTo("MEASURED"))
                .body("metrics.activation.state", equalTo("MEASURED"));
    }

    @Test
    void decisionRejectsPendingWallet() {
        String id = startRun();
        stubCreate(201, kycBody("PENDING"));
        submit(id, "TEST-001", "MANUAL_REVIEW").statusCode(201);
        stubDecision(200, kycBody("REJECTED"));

        decide(id, "REJECTED").statusCode(200)
                .body("status", equalTo("REJECTED"))
                .body("verification.walletActive", equalTo(false))
                .body("metrics.activation.state", equalTo("NOT_AVAILABLE"));
    }

    @Test
    void repeatedDecisionDoesNotDuplicateEvents() {
        String id = startRun();
        stubCreate(201, kycBody("PENDING"));
        submit(id, "TEST-001", "MANUAL_REVIEW").statusCode(201);
        stubDecision(200, kycBody("ACTIVE"));

        decide(id, "ACTIVE").statusCode(200);
        decide(id, "ACTIVE").statusCode(200).body("status", equalTo("APPROVED"));

        assertTrue(eventTypes(id).stream().filter("kyc_decided"::equals).count() == 1);
        assertTrue(eventTypes(id).stream().filter("wallet_activated"::equals).count() == 1);
    }

    @Test
    void conflictingDecisionKeepsFinalState() {
        String id = startRun();
        stubCreate(201, kycBody("PENDING"));
        submit(id, "TEST-001", "MANUAL_REVIEW").statusCode(201);
        stubDecision(200, kycBody("ACTIVE"));
        decide(id, "ACTIVE").statusCode(200);

        stubDecision(409, "{\"error\":\"DECISION_CONFLICT\"}");
        decide(id, "REJECTED").statusCode(409).body("error", equalTo("DECISION_CONFLICT"));

        getRun(id).body("status", equalTo("APPROVED")).body("verification.walletActive", equalTo(true));
    }

    @Test
    void decisionBeforeVerificationIsConflict() {
        String id = startRun();
        decide(id, "ACTIVE").statusCode(409).body("error", equalTo("NO_VERIFICATION"));
    }

    @Test
    void invalidDecisionBodyIsRejected() {
        String id = startRun();
        given().contentType("application/json").body("{}").when().post("/api/onboarding/runs/" + id + "/kyc/decision").then()
                .statusCode(400).body("error", equalTo("INVALID_REQUEST"));
    }

    // --- 4.4 resumen y exportación con el modo demo activo ---

    @Test
    void summaryListsRunsWithMedianAndReference() {
        String id = startRun();
        stubCreate(201, kycBody("ACTIVE"));
        submit(id, "TEST-001", "APPROVED").statusCode(201);

        given().when().get("/api/onboarding/summary").then().statusCode(200)
                .body("referenceMillis", equalTo(180000))
                .body("medianFlowMillis", notNullValue())
                .body("measuredRuns", org.hamcrest.Matchers.greaterThanOrEqualTo(1))
                .body("runs.runId", hasItems(id));
    }

    @Test
    void eventsExportUsesWireNamesAndNoPersonalData() {
        String id = startRun();
        given().when().post("/api/onboarding/runs/" + id + "/steps/phone").then().statusCode(204);

        given().when().get("/api/onboarding/events").then().statusCode(200)
                .body("findAll { it.runId == '" + id + "' }.type", hasItems("onboarding_started", "step_completed"))
                .body("findAll { it.runId == '" + id + "' }.meta", not(empty()));
    }

    @Test
    void zeroCallsToMockForPlainRunLifecycle() {
        startRun();
        WireMockResource.server().verify(0, WireMock.anyRequestedFor(WireMock.anyUrl()));
    }
}
