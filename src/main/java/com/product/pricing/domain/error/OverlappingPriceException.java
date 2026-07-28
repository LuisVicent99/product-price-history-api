package com.product.pricing.domain.error;

public class OverlappingPriceException extends RuntimeException {

    public OverlappingPriceException(long productId) {
        super("Price validity overlaps an existing price of product " + productId);
    }
}
