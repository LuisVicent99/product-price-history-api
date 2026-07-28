package com.product.pricing.domain.error;

public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(long productId) {
        super("Product " + productId + " does not exist");
    }
}
