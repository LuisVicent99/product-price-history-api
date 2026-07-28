package com.product.pricing.domain;

import com.product.pricing.domain.error.InvalidRequestException;
import com.product.pricing.domain.error.PriceNotFoundException;
import com.product.pricing.domain.error.ProductNotFoundException;
import com.product.pricing.domain.model.DateInterval;
import com.product.pricing.domain.model.Price;
import com.product.pricing.domain.model.PriceTimeline;
import com.product.pricing.domain.model.Product;
import com.product.pricing.domain.model.ProductPriceHistory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class PricingService {

    private static final String DEFAULT_CURRENCY = "EUR";

    private final ProductRepository products;
    private final PriceRepository prices;
    private final PriceTimelineProvider timelines;

    public PricingService(ProductRepository products, PriceRepository prices, PriceTimelineProvider timelines) {
        this.products = products;
        this.prices = prices;
        this.timelines = timelines;
    }

    public CompletionStage<Product> createProduct(String name, String description) {
        if (name == null || name.isBlank()) {
            throw new InvalidRequestException("name is required and must not be blank");
        }
        if (name.length() > 150) {
            throw new InvalidRequestException("name must be at most 150 characters, got " + name.length());
        }
        if (description != null && description.length() > 500) {
            throw new InvalidRequestException(
                "description must be at most 500 characters, got " + description.length());
        }
        return products.insert(name, description);
    }

    public CompletionStage<Price> addPrice(long productId, BigDecimal amount, String currency,
                                           LocalDate initDate, LocalDate endDate) {
        if (amount == null) {
            throw new InvalidRequestException("value is required");
        }
        if (amount.signum() < 0) {
            throw new InvalidRequestException("value must not be negative, got " + amount);
        }
        if (currency != null && !currency.matches("[A-Za-z]{3}")) {
            throw new InvalidRequestException("currency must be exactly 3 letters, got '" + currency + "'");
        }
        DateInterval validity = new DateInterval(initDate, endDate);
        String effectiveCurrency = currency == null ? DEFAULT_CURRENCY : currency;
        return prices.insert(productId, amount, effectiveCurrency, validity)
            .thenApply(inserted -> {
                timelines.invalidate(productId);
                return inserted;
            });
    }

    public CompletionStage<Price> priceAt(long productId, LocalDate date) {
        return timelines.timelineOf(productId).thenCompose(timeline -> timeline.findAt(date)
            .<CompletionStage<Price>>map(CompletableFuture::completedStage)
            .orElseGet(() -> CompletableFuture.failedStage(new PriceNotFoundException(productId, date))));
    }

    public CompletionStage<ProductPriceHistory> history(long productId) {
        return products.findById(productId)
            .thenCombine(prices.findAllByProduct(productId), (product, priceList) -> product
                .map(found -> new ProductPriceHistory(found, new PriceTimeline(priceList)))
                .orElseThrow(() -> new ProductNotFoundException(productId)));
    }
}
