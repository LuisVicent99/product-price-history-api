package com.product.pricing.infrastructure.persistence;

import com.product.pricing.domain.model.DateInterval;
import com.product.pricing.domain.model.Price;
import io.vertx.sqlclient.Row;

final class PriceRowMapper {

    private PriceRowMapper() {
    }

    static Price from(Row row) {
        return new Price(
            row.getLong("id"),
            row.getLong("product_id"),
            row.getBigDecimal("amount"),
            row.getString("currency"),
            new DateInterval(row.getLocalDate("init_date"), row.getLocalDate("end_date")));
    }
}
