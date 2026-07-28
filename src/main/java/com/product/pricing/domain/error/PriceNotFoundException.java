package com.product.pricing.domain.error;

import java.time.LocalDate;

public class PriceNotFoundException extends RuntimeException {

    public PriceNotFoundException(long productId, LocalDate date) {
        super("Product " + productId + " has no price in force at " + date);
    }
}
