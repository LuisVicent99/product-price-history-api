package com.product.pricing.domain.model;

import java.math.BigDecimal;

public record Price(long id, long productId, BigDecimal amount, String currency, DateInterval validity) {
}
