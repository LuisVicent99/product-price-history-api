package com.product.pricing.api.dto;

import com.product.pricing.domain.model.Price;

import java.math.BigDecimal;

public record CurrentPriceView(BigDecimal value) {

    public static CurrentPriceView from(Price price) {
        return new CurrentPriceView(price.amount());
    }
}
