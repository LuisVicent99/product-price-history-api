package com.product.pricing.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PriceRequest(BigDecimal value, String currency, LocalDate initDate, LocalDate endDate) {
}
