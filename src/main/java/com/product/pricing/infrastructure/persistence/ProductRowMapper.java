package com.product.pricing.infrastructure.persistence;

import com.product.pricing.domain.model.Product;
import io.vertx.sqlclient.Row;

final class ProductRowMapper {

    private ProductRowMapper() {
    }

    static Product from(Row row) {
        return new Product(row.getLong("id"), row.getString("name"), row.getString("description"));
    }
}
