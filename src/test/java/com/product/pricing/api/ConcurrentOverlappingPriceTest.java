package com.product.pricing.api;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class ConcurrentOverlappingPriceTest {

    @Test
    void exactlyOneOfTwoConcurrentOverlappingInsertsSucceeds() throws Exception {
        long productId = given()
            .contentType("application/json")
            .body(Map.of("name", "Laptop", "description", "Concurrency probe"))
            .post("/products")
            .then().statusCode(201)
            .extract().jsonPath().getLong("id");

        int attempts = 2;
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> submissions = new ArrayList<>();
        try {
            for (int i = 0; i < attempts; i++) {
                submissions.add(executor.submit(() -> {
                    start.await();
                    return given()
                        .contentType("application/json")
                        .body(Map.of("value", "10.00", "initDate", "2026-01-01", "endDate", "2026-06-30"))
                        .post("/products/" + productId + "/prices")
                        .statusCode();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> submission : submissions) {
                statuses.add(submission.get());
            }
            statuses.sort(Integer::compareTo);
            assertEquals(List.of(201, 409), statuses);
        } finally {
            executor.shutdown();
        }
    }
}
