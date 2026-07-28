package com.product.pricing.domain;

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

    public PricingService(ProductRepository products, PriceRepository prices) {
        this.products = products;
        this.prices = prices;
    }

    public CompletionStage<Product> createProduct(String name, String description) {
        return products.insert(name, description);
    }

    public CompletionStage<Price> addPrice(long productId, BigDecimal amount, String currency,
                                           LocalDate initDate, LocalDate endDate) {
        DateInterval validity = new DateInterval(initDate, endDate);
        String effectiveCurrency = currency == null || currency.isBlank() ? DEFAULT_CURRENCY : currency;
        return prices.insert(productId, amount, effectiveCurrency, validity);
    }

    public CompletionStage<Price> priceAt(long productId, LocalDate date) {
        return prices.findAt(productId, date).thenCompose(found -> found
            .<CompletionStage<Price>>map(CompletableFuture::completedStage)
            .orElseGet(() -> products.exists(productId).thenCompose(exists -> exists
                ? CompletableFuture.failedStage(new PriceNotFoundException(productId, date))
                : CompletableFuture.failedStage(new ProductNotFoundException(productId)))));
    }

    public CompletionStage<ProductPriceHistory> history(long productId) {
        return products.findById(productId)
            .thenCombine(prices.findAllByProduct(productId), (product, priceList) -> product
                .map(found -> new ProductPriceHistory(found, new PriceTimeline(priceList)))
                .orElseThrow(() -> new ProductNotFoundException(productId)));
    }
}
