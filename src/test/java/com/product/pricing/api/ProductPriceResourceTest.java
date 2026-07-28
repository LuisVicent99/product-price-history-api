package com.product.pricing.api;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
class ProductPriceResourceTest {

    private static long createProduct() {
        return given()
            .contentType("application/json")
            .body(Map.of("name", "Monitor", "description", "4K monitor"))
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

    private static void addPrice(long productId, String value, String initDate, String endDate) {
        given()
            .contentType("application/json")
            .body(priceBody(value, initDate, endDate))
            .post("/products/" + productId + "/prices")
            .then().statusCode(201);
    }

    @Test
    void createProductReturns201WithLocationAndBody() {
        given()
            .contentType("application/json")
            .body(Map.of("name", "Keyboard", "description", "Mechanical"))
            .post("/products")
            .then()
            .statusCode(201)
            .header("Location", containsString("/products/"))
            .body("id", notNullValue())
            .body("name", equalTo("Keyboard"))
            .body("description", equalTo("Mechanical"));
    }

    @Test
    void addPriceReturns201WithLocation() {
        long productId = createProduct();
        given()
            .contentType("application/json")
            .body(priceBody("19.99", "2026-01-01", "2026-01-31"))
            .post("/products/" + productId + "/prices")
            .then()
            .statusCode(201)
            .header("Location", containsString("/products/" + productId + "/prices/"))
            .body("value", equalTo(19.99f))
            .body("initDate", equalTo("2026-01-01"))
            .body("endDate", equalTo("2026-01-31"));
    }

    @Test
    void addPriceWithInitDateEqualToEndDateReturns400() {
        long productId = createProduct();
        given()
            .contentType("application/json")
            .body(priceBody("19.99", "2026-01-10", "2026-01-10"))
            .post("/products/" + productId + "/prices")
            .then()
            .statusCode(400)
            .body("code", equalTo("INVALID_DATE_RANGE"));
    }

    @Test
    void addPriceToMissingProductReturns404WithProductCode() {
        given()
            .contentType("application/json")
            .body(priceBody("19.99", "2026-01-01", "2026-01-31"))
            .post("/products/999999999/prices")
            .then()
            .statusCode(404)
            .body("code", equalTo("PRODUCT_NOT_FOUND"));
    }

    @Test
    void addOverlappingPriceReturns409() {
        long productId = createProduct();
        addPrice(productId, "10.00", "2026-01-01", "2026-01-31");
        given()
            .contentType("application/json")
            .body(priceBody("12.00", "2026-01-31", "2026-02-15"))
            .post("/products/" + productId + "/prices")
            .then()
            .statusCode(409)
            .body("code", equalTo("PRICE_OVERLAP"));
    }

    @Test
    void contiguousPriceStartingTheDayAfterIsAccepted() {
        long productId = createProduct();
        addPrice(productId, "10.00", "2026-01-01", "2026-01-31");
        addPrice(productId, "12.00", "2026-02-01", "2026-02-28");
    }

    @Test
    void historyReturnsNameDescriptionAndPricesOrderedByInitDate() {
        long productId = createProduct();
        addPrice(productId, "20.00", "2026-03-01", null);
        addPrice(productId, "10.00", "2026-01-01", "2026-02-28");
        given()
            .get("/products/" + productId + "/prices")
            .then()
            .statusCode(200)
            .body("name", equalTo("Monitor"))
            .body("description", equalTo("4K monitor"))
            .body("prices", hasSize(2))
            .body("prices[0].value", equalTo(10.00f))
            .body("prices[0].initDate", equalTo("2026-01-01"))
            .body("prices[0].endDate", equalTo("2026-02-28"))
            .body("prices[1].value", equalTo(20.00f))
            .body("prices[1].initDate", equalTo("2026-03-01"))
            .body("prices[1].endDate", nullValue());
    }

    @Test
    void currentPriceReturnsOnlyTheValue() {
        long productId = createProduct();
        addPrice(productId, "99.99", "2026-01-01", "2026-12-31");
        given()
            .get("/products/" + productId + "/prices?date=2026-06-15")
            .then()
            .statusCode(200)
            .body("value", equalTo(99.99f));
    }

    @Test
    void currentPriceAtInclusiveEndBoundaryIsStillInForce() {
        long productId = createProduct();
        addPrice(productId, "50.00", "2026-01-10", "2026-01-20");
        given()
            .get("/products/" + productId + "/prices?date=2026-01-20")
            .then()
            .statusCode(200)
            .body("value", equalTo(50.00f));
        given()
            .get("/products/" + productId + "/prices?date=2026-01-21")
            .then()
            .statusCode(404)
            .body("code", equalTo("PRICE_NOT_FOUND"));
    }

    @Test
    void currentPriceOnDateWithoutPriceReturns404WithPriceCode() {
        long productId = createProduct();
        addPrice(productId, "10.00", "2026-01-01", "2026-01-31");
        given()
            .get("/products/" + productId + "/prices?date=2025-06-15")
            .then()
            .statusCode(404)
            .body("code", equalTo("PRICE_NOT_FOUND"));
    }

    @Test
    void currentPriceOfMissingProductReturns404WithProductCode() {
        given()
            .get("/products/999999999/prices?date=2026-06-15")
            .then()
            .statusCode(404)
            .body("code", equalTo("PRODUCT_NOT_FOUND"));
    }

    @Test
    void historyOfMissingProductReturns404WithProductCode() {
        given()
            .get("/products/999999999/prices")
            .then()
            .statusCode(404)
            .body("code", equalTo("PRODUCT_NOT_FOUND"));
    }

    @Test
    void malformedDateQueryParamReturns400WithClearMessage() {
        long productId = createProduct();
        given()
            .get("/products/" + productId + "/prices?date=15-06-2026")
            .then()
            .statusCode(400)
            .body("code", equalTo("INVALID_DATE_FORMAT"))
            .body("detail", containsString("ISO-8601"));
    }

    private static void assertProductValidationError(Map<String, Object> body, String failingField) {
        given()
            .contentType("application/json")
            .body(body)
            .post("/products")
            .then()
            .statusCode(400)
            .contentType("application/problem+json")
            .body("code", equalTo("VALIDATION_ERROR"))
            .body("detail", containsString(failingField));
    }

    private static void assertPriceValidationError(Map<String, Object> body, String failingField) {
        long productId = createProduct();
        given()
            .contentType("application/json")
            .body(body)
            .post("/products/" + productId + "/prices")
            .then()
            .statusCode(400)
            .contentType("application/problem+json")
            .body("code", equalTo("VALIDATION_ERROR"))
            .body("detail", containsString(failingField));
    }

    private static Map<String, Object> productBody(String name, String description) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("description", description);
        return body;
    }

    @Test
    void createProductWithNullNameReturns400() {
        assertProductValidationError(productBody(null, "valid description"), "name");
    }

    @Test
    void createProductWithEmptyNameReturns400() {
        assertProductValidationError(productBody("", "valid description"), "name");
    }

    @Test
    void createProductWithBlankNameReturns400() {
        assertProductValidationError(productBody("   ", "valid description"), "name");
    }

    @Test
    void createProductWithTooLongNameReturns400() {
        assertProductValidationError(productBody("x".repeat(151), "valid description"), "name");
    }

    @Test
    void createProductWithTooLongDescriptionReturns400() {
        assertProductValidationError(productBody("Valid name", "x".repeat(501)), "description");
    }

    @Test
    void addPriceWithNullValueReturns400() {
        assertPriceValidationError(priceBody(null, "2026-01-01", "2026-01-31"), "value");
    }

    @Test
    void addPriceWithNegativeValueReturns400() {
        assertPriceValidationError(priceBody("-5.00", "2026-01-01", "2026-01-31"), "value");
    }

    @Test
    void addPriceWithMalformedCurrencyReturns400() {
        Map<String, Object> body = priceBody("10.00", "2026-01-01", "2026-01-31");
        body.put("currency", "EURO");
        assertPriceValidationError(body, "currency");
    }

    @Test
    void createProductWithoutBodyReturns400() {
        given()
            .contentType("application/json")
            .post("/products")
            .then()
            .statusCode(400)
            .contentType("application/problem+json")
            .body("code", equalTo("VALIDATION_ERROR"))
            .body("detail", containsString("body"));
    }

    @Test
    void addPriceWithoutBodyReturns400() {
        long productId = createProduct();
        given()
            .contentType("application/json")
            .post("/products/" + productId + "/prices")
            .then()
            .statusCode(400)
            .contentType("application/problem+json")
            .body("code", equalTo("VALIDATION_ERROR"))
            .body("detail", containsString("body"));
    }

    @Test
    void createProductWithNonObjectJsonBodyReturns400() {
        given()
            .contentType("application/json")
            .body("[1, 2, 3]")
            .post("/products")
            .then()
            .statusCode(400)
            .contentType("application/problem+json")
            .body("code", equalTo("VALIDATION_ERROR"));
    }

    @Test
    void addPriceWithNonObjectJsonBodyReturns400() {
        long productId = createProduct();
        given()
            .contentType("application/json")
            .body("\"just a string\"")
            .post("/products/" + productId + "/prices")
            .then()
            .statusCode(400)
            .contentType("application/problem+json")
            .body("code", equalTo("VALIDATION_ERROR"));
    }
}
