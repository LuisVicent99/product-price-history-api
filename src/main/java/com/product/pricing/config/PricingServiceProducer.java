package com.product.pricing.config;

import com.product.pricing.domain.PriceRepository;
import com.product.pricing.domain.PricingService;
import com.product.pricing.domain.ProductRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class PricingServiceProducer {

    @Produces
    @ApplicationScoped
    PricingService pricingService(ProductRepository products, PriceRepository prices) {
        return new PricingService(products, prices);
    }
}
