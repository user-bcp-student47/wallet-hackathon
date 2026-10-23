package pe.wallet.onboarding.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ConfigResourceTest {

    @Test
    void exposesDemoFlag() {
        given().when().get("/api/config").then().statusCode(200).body("demoEnabled", is(true));
    }
}
