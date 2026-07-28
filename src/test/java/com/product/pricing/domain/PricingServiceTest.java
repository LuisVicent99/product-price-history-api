package com.product.pricing.domain;

import com.product.pricing.domain.error.InvalidDateRangeException;
import com.product.pricing.domain.error.InvalidRequestException;
import com.product.pricing.domain.error.PriceNotFoundException;
import com.product.pricing.domain.error.ProductNotFoundException;
import com.product.pricing.domain.model.DateInterval;
import com.product.pricing.domain.model.Price;
import com.product.pricing.domain.model.PriceTimeline;
import com.product.pricing.domain.model.Product;
import com.product.pricing.domain.model.ProductPriceHistory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PricingServiceTest {

    private static final LocalDate JAN_1 = LocalDate.of(2026, Month.JANUARY, 1);
    private static final LocalDate JAN_31 = LocalDate.of(2026, Month.JANUARY, 31);

    private final RecordingProductRepository products = new RecordingProductRepository();
    private final RecordingPriceRepository prices = new RecordingPriceRepository();
    private final RecordingTimelineProvider timelines = new RecordingTimelineProvider();
    private final PricingService service = new PricingService(products, prices, timelines);

    private static <T> T await(CompletionStage<T> stage) throws Exception {
        return stage.toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    private static Throwable causeOf(Executable executable) {
        ExecutionException error = assertThrows(ExecutionException.class, executable::execute);
        return error.getCause();
    }

    private interface Executable {
        void execute() throws Exception;
    }

    @Test
    void createProductPersistsValidInput() throws Exception {
        Product created = await(service.createProduct("Monitor", "4K"));
        assertEquals("Monitor", created.name());
        assertEquals(List.of("Monitor|4K"), products.inserted);
    }

    @Test
    void createProductRejectsNullName() {
        InvalidRequestException error = assertThrows(InvalidRequestException.class,
            () -> service.createProduct(null, "x"));
        assertEquals(0, products.inserted.size());
        assertContains(error, "name");
    }

    @Test
    void createProductRejectsBlankName() {
        assertContains(assertThrows(InvalidRequestException.class,
            () -> service.createProduct("   ", "x")), "name");
    }

    @Test
    void createProductRejectsTooLongName() {
        String tooLongName = "x".repeat(151);
        assertContains(assertThrows(InvalidRequestException.class,
            () -> service.createProduct(tooLongName, "x")), "name");
    }

    @Test
    void createProductRejectsTooLongDescription() {
        String tooLongDescription = "x".repeat(501);
        assertContains(assertThrows(InvalidRequestException.class,
            () -> service.createProduct("Valid", tooLongDescription)), "description");
    }

    @Test
    void createProductAcceptsNullDescription() throws Exception {
        await(service.createProduct("Valid", null));
        assertEquals(List.of("Valid|null"), products.inserted);
    }

    @Test
    void addPriceRejectsNullValue() {
        assertContains(assertThrows(InvalidRequestException.class,
            () -> service.addPrice(1L, null, "EUR", JAN_1, JAN_31)), "value");
    }

    @Test
    void addPriceRejectsNegativeValue() {
        BigDecimal negative = new BigDecimal("-1");
        assertContains(assertThrows(InvalidRequestException.class,
            () -> service.addPrice(1L, negative, "EUR", JAN_1, JAN_31)), "value");
    }

    @Test
    void addPriceRejectsMalformedCurrency() {
        assertContains(assertThrows(InvalidRequestException.class,
            () -> service.addPrice(1L, BigDecimal.TEN, "EURO", JAN_1, JAN_31)), "currency");
    }

    @Test
    void addPriceRejectsInvalidDateRange() {
        assertThrows(InvalidDateRangeException.class,
            () -> service.addPrice(1L, BigDecimal.TEN, "EUR", JAN_31, JAN_1));
    }

    @Test
    void addPriceDefaultsCurrencyWhenAbsentAndInvalidatesCache() throws Exception {
        await(service.addPrice(7L, BigDecimal.TEN, null, JAN_1, JAN_31));
        assertEquals("EUR", prices.lastCurrency);
        assertEquals(List.of(7L), timelines.invalidated);
    }

    @Test
    void addPriceKeepsProvidedCurrency() throws Exception {
        await(service.addPrice(7L, BigDecimal.TEN, "usd", JAN_1, JAN_31));
        assertEquals("usd", prices.lastCurrency);
    }

    @Test
    void priceAtReturnsPriceInForce() throws Exception {
        Price inForce = price(7L);
        timelines.timelines.put(7L, new PriceTimeline(List.of(inForce)));
        assertSame(inForce, await(service.priceAt(7L, LocalDate.of(2026, Month.JUNE, 15))));
    }

    @Test
    void priceAtFailsWhenNoPriceCoversDate() {
        timelines.timelines.put(7L, new PriceTimeline(List.of(price(7L))));
        Throwable cause = causeOf(() -> await(service.priceAt(7L, LocalDate.of(2030, Month.JANUARY, 1))));
        assertInstanceOf(PriceNotFoundException.class, cause);
    }

    @Test
    void historyCombinesProductAndTimeline() throws Exception {
        products.byId.put(7L, new Product(7L, "Monitor", "4K"));
        prices.history.put(7L, List.of(price(7L)));
        ProductPriceHistory history = await(service.history(7L));
        assertEquals("Monitor", history.product().name());
        assertEquals(1, history.prices().prices().size());
    }

    @Test
    void historyFailsForMissingProduct() {
        prices.history.put(7L, List.of());
        Throwable cause = causeOf(() -> await(service.history(7L)));
        assertInstanceOf(ProductNotFoundException.class, cause);
    }

    private static void assertContains(InvalidRequestException error, String field) {
        assertEquals(true, error.getMessage().contains(field),
            "message should name field '" + field + "' but was: " + error.getMessage());
    }

    private static Price price(long productId) {
        return new Price(1L, productId, new BigDecimal("10.00"), "EUR",
            new DateInterval(LocalDate.of(2026, Month.JANUARY, 1), LocalDate.of(2026, Month.DECEMBER, 31)));
    }

    private static final class RecordingProductRepository implements ProductRepository {

        final List<String> inserted = new ArrayList<>();
        final java.util.Map<Long, Product> byId = new java.util.HashMap<>();

        @Override
        public CompletionStage<Product> insert(String name, String description) {
            inserted.add(name + "|" + description);
            return CompletableFuture.completedStage(new Product(1L, name, description));
        }

        @Override
        public CompletionStage<Optional<Product>> findById(long id) {
            return CompletableFuture.completedStage(Optional.ofNullable(byId.get(id)));
        }

        @Override
        public CompletionStage<Boolean> exists(long id) {
            return CompletableFuture.completedStage(byId.containsKey(id));
        }
    }

    private static final class RecordingPriceRepository implements PriceRepository {

        final java.util.Map<Long, List<Price>> history = new java.util.HashMap<>();
        String lastCurrency;

        @Override
        public CompletionStage<Price> insert(long productId, BigDecimal amount, String currency,
                                             DateInterval validity) {
            lastCurrency = currency;
            return CompletableFuture.completedStage(new Price(1L, productId, amount, currency, validity));
        }

        @Override
        public CompletionStage<List<Price>> findAllByProduct(long productId) {
            return CompletableFuture.completedStage(history.getOrDefault(productId, List.of()));
        }
    }

    private static final class RecordingTimelineProvider implements PriceTimelineProvider {

        final java.util.Map<Long, PriceTimeline> timelines = new java.util.HashMap<>();
        final List<Long> invalidated = new ArrayList<>();

        @Override
        public CompletionStage<PriceTimeline> timelineOf(long productId) {
            return CompletableFuture.completedStage(
                timelines.getOrDefault(productId, new PriceTimeline(List.of())));
        }

        @Override
        public void invalidate(long productId) {
            invalidated.add(productId);
        }
    }
}
