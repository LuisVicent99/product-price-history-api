package com.product.pricing;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

@QuarkusTest
class HealthSmokeTest {

    @Test
    void readinessEndpointReturns200() {
        given()
            .when().get("/q/health/ready")
            .then().statusCode(200);
    }
}
