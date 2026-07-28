package com.product.pricing.api;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class CacheCoherenceTest {

    private static long createProduct() {
        return given()
            .contentType("application/json")
            .body(Map.of("name", "Router", "description", "WiFi 7"))
            .post("/products")
            .then().statusCode(201)
            .extract().jsonPath().getLong("id");
    }

    private static Map<String, Object> priceBody(String value, String initDate, String endDate) {
        Map<String, Object> body = new HashMap<>();
        body.put("value", value);
        body.put("initDate", initDate);
        body.put("endDate", endDate);
        return body;
    }

    @Test
    void priceInsertedAfterCacheLoadIsVisibleOnNextQuery() {
        long productId = createProduct();
        given()
            .contentType("application/json")
            .body(priceBody("10.00", "2026-01-01", "2026-01-31"))
            .post("/products/" + productId + "/prices")
            .then().statusCode(201);
        given()
            .get("/products/" + productId + "/prices?date=2026-01-15")
            .then().statusCode(200)
            .body("value", equalTo(10.00f));

        given()
            .contentType("application/json")
            .body(priceBody("20.00", "2026-02-01", "2026-02-28"))
            .post("/products/" + productId + "/prices")
            .then().statusCode(201);

        given()
            .get("/products/" + productId + "/prices?date=2026-02-15")
            .then().statusCode(200)
            .body("value", equalTo(20.00f));
    }

    @Test
    void rejectedOverlappingInsertDoesNotChangeTheCachedAnswer() {
        long productId = createProduct();
        given()
            .contentType("application/json")
            .body(priceBody("10.00", "2026-01-01", "2026-01-31"))
            .post("/products/" + productId + "/prices")
            .then().statusCode(201);
        given()
            .get("/products/" + productId + "/prices?date=2026-01-15")
            .then().statusCode(200)
            .body("value", equalTo(10.00f));

        given()
            .contentType("application/json")
            .body(priceBody("99.99", "2026-01-10", "2026-02-10"))
            .post("/products/" + productId + "/prices")
            .then().statusCode(409);

        given()
            .get("/products/" + productId + "/prices?date=2026-01-15")
            .then().statusCode(200)
            .body("value", equalTo(10.00f));
    }

    @Test
    void missingProductLookupKeepsReturning404() {
        given()
            .get("/products/987654321/prices?date=2026-01-15")
            .then().statusCode(404)
            .body("code", equalTo("PRODUCT_NOT_FOUND"));
        given()
            .get("/products/987654321/prices?date=2026-01-15")
            .then().statusCode(404)
            .body("code", equalTo("PRODUCT_NOT_FOUND"));
    }
}
