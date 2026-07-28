package com.product.pricing.infrastructure.cache;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.product.pricing.domain.PriceRepository;
import com.product.pricing.domain.PriceTimelineProvider;
import com.product.pricing.domain.ProductRepository;
import com.product.pricing.domain.error.ProductNotFoundException;
import com.product.pricing.domain.model.PriceTimeline;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@ApplicationScoped
public class PriceTimelineCache implements PriceTimelineProvider {

    private final ProductRepository products;
    private final PriceRepository prices;
    private final AsyncLoadingCache<Long, PriceTimeline> cache;

    public PriceTimelineCache(ProductRepository products, PriceRepository prices) {
        this.products = products;
        this.prices = prices;
        this.cache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofMinutes(10))
            .buildAsync((productId, executor) -> load(productId));
    }

    private CompletableFuture<PriceTimeline> load(long productId) {
        return prices.findAllByProduct(productId)
            .thenCompose(priceList -> priceList.isEmpty()
                ? emptyTimelineIfProductExists(productId)
                : CompletableFuture.completedStage(new PriceTimeline(priceList)))
            .toCompletableFuture();
    }

    private CompletionStage<PriceTimeline> emptyTimelineIfProductExists(long productId) {
        return products.exists(productId).thenCompose(exists -> exists
            ? CompletableFuture.completedStage(new PriceTimeline(List.of()))
            : CompletableFuture.failedStage(new ProductNotFoundException(productId)));
    }

    @Override
    public CompletionStage<PriceTimeline> timelineOf(long productId) {
        return cache.get(productId);
    }

    @Override
    public void invalidate(long productId) {
        cache.synchronous().invalidate(productId);
    }
}
