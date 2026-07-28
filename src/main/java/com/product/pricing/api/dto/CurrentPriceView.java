package com.product.pricing.api.dto;

import com.product.pricing.domain.model.Price;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.math.BigDecimal;

@RegisterForReflection
public record CurrentPriceView(BigDecimal value) {

    public static CurrentPriceView from(Price price) {
        return new CurrentPriceView(price.amount());
    }
}
