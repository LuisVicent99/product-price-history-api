package com.product.pricing.infrastructure.cache;

import com.product.pricing.domain.PriceRepository;
import com.product.pricing.domain.ProductRepository;
import com.product.pricing.domain.error.ProductNotFoundException;
import com.product.pricing.domain.model.DateInterval;
import com.product.pricing.domain.model.Price;
import com.product.pricing.domain.model.PriceTimeline;
import com.product.pricing.domain.model.Product;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PriceTimelineCacheTest {

    private static final class FakeProductRepository implements ProductRepository {

        final AtomicInteger existsCalls = new AtomicInteger();
        final Set<Long> existingIds = new HashSet<>();

        @Override
        public CompletionStage<Product> insert(String name, String description) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletionStage<Optional<Product>> findById(long id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletionStage<Boolean> exists(long id) {
            existsCalls.incrementAndGet();
            return CompletableFuture.completedStage(existingIds.contains(id));
        }
    }

    private static final class FakePriceRepository implements PriceRepository {

        final AtomicInteger loadCalls = new AtomicInteger();
        final Map<Long, List<Price>> pricesByProduct = new HashMap<>();

        @Override
        public CompletionStage<Price> insert(long productId, BigDecimal amount, String currency,
                                             DateInterval validity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletionStage<List<Price>> findAllByProduct(long productId) {
            loadCalls.incrementAndGet();
            return CompletableFuture.completedStage(pricesByProduct.getOrDefault(productId, List.of()));
        }
    }

    private static Price price(long productId) {
        return new Price(1L, productId, new BigDecimal("10.00"), "EUR",
            new DateInterval(LocalDate.of(2026, 1, 1), null));
    }

    private static PriceTimeline await(CompletionStage<PriceTimeline> stage) throws Exception {
        return stage.toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    @Test
    void secondQueryForSameProductDoesNotHitTheRepositoryAgain() throws Exception {
        FakeProductRepository products = new FakeProductRepository();
        FakePriceRepository prices = new FakePriceRepository();
        prices.pricesByProduct.put(1L, List.of(price(1L)));
        PriceTimelineCache cache = new PriceTimelineCache(products, prices);

        assertEquals(1, await(cache.timelineOf(1L)).prices().size());
        assertEquals(1, await(cache.timelineOf(1L)).prices().size());

        assertEquals(1, prices.loadCalls.get());
        assertEquals(0, products.existsCalls.get());
    }

    @Test
    void invalidateForcesAReloadOnNextQuery() throws Exception {
        FakeProductRepository products = new FakeProductRepository();
        FakePriceRepository prices = new FakePriceRepository();
        prices.pricesByProduct.put(1L, List.of(price(1L)));
        PriceTimelineCache cache = new PriceTimelineCache(products, prices);

        await(cache.timelineOf(1L));
        cache.invalidate(1L);
        await(cache.timelineOf(1L));

        assertEquals(2, prices.loadCalls.get());
    }

    @Test
    void missingProductFailsAndLeavesNoCacheEntry() throws Exception {
        FakeProductRepository products = new FakeProductRepository();
        FakePriceRepository prices = new FakePriceRepository();
        PriceTimelineCache cache = new PriceTimelineCache(products, prices);

        ExecutionException first = assertThrows(ExecutionException.class,
            () -> await(cache.timelineOf(7L)));
        assertInstanceOf(ProductNotFoundException.class, rootCause(first));

        assertThrows(ExecutionException.class, () -> await(cache.timelineOf(7L)));

        assertEquals(2, prices.loadCalls.get());
        assertEquals(2, products.existsCalls.get());
    }

    @Test
    void emptyTimelineOfExistingProductIsCached() throws Exception {
        FakeProductRepository products = new FakeProductRepository();
        FakePriceRepository prices = new FakePriceRepository();
        products.existingIds.add(1L);
        PriceTimelineCache cache = new PriceTimelineCache(products, prices);

        assertTrue(await(cache.timelineOf(1L)).prices().isEmpty());
        assertTrue(await(cache.timelineOf(1L)).prices().isEmpty());

        assertEquals(1, prices.loadCalls.get());
        assertEquals(1, products.existsCalls.get());
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}
