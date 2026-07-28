package com.product.pricing.api.dto;

import com.product.pricing.domain.model.Product;

public record ProductView(long id, String name, String description) {

    public static ProductView from(Product product) {
        return new ProductView(product.id(), product.name(), product.description());
    }
}
