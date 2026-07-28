package com.product.pricing.api.dto;

import com.product.pricing.domain.model.Price;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.math.BigDecimal;
import java.time.LocalDate;

@RegisterForReflection
public record PriceView(BigDecimal value, LocalDate initDate, LocalDate endDate) {

    public static PriceView from(Price price) {
        return new PriceView(price.amount(), price.validity().initDate(), price.validity().endDate());
    }
}
