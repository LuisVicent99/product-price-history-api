package com.product.pricing.infrastructure.persistence;

import com.product.pricing.domain.PriceRepository;
import com.product.pricing.domain.error.OverlappingPriceException;
import com.product.pricing.domain.error.ProductNotFoundException;
import com.product.pricing.domain.model.DateInterval;
import com.product.pricing.domain.model.Price;
import io.vertx.core.Future;
import io.vertx.pgclient.PgException;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

@ApplicationScoped
public class PgPriceRepository implements PriceRepository {

    private static final String SQLSTATE_EXCLUSION_VIOLATION = "23P01";
    private static final String SQLSTATE_FOREIGN_KEY_VIOLATION = "23503";

    private static final String INSERT =
        "INSERT INTO price (product_id, amount, currency, init_date, end_date) "
            + "VALUES ($1, $2, $3, $4, $5) "
            + "RETURNING id, product_id, amount, currency, init_date, end_date";
    private static final String SELECT_IN_FORCE_AT =
        "SELECT id, product_id, amount, currency, init_date, end_date "
            + "FROM price WHERE product_id = $1 AND validity @> $2::date";
    private static final String SELECT_HISTORY =
        "SELECT id, product_id, amount, currency, init_date, end_date "
            + "FROM price WHERE product_id = $1 ORDER BY init_date";

    private final Pool pool;

    public PgPriceRepository(Pool pool) {
        this.pool = pool;
    }

    @Override
    public CompletionStage<Price> insert(long productId, BigDecimal amount, String currency, DateInterval validity) {
        return pool.preparedQuery(INSERT)
            .execute(Tuple.of(productId, amount, currency, validity.initDate(), validity.endDate()))
            .map(rows -> PriceRowMapper.from(rows.iterator().next()))
            .recover(error -> Future.failedFuture(translateInsertError(error, productId)))
            .toCompletionStage();
    }

    @Override
    public CompletionStage<Optional<Price>> findAt(long productId, LocalDate date) {
        return pool.preparedQuery(SELECT_IN_FORCE_AT)
            .execute(Tuple.of(productId, date))
            .map(rows -> rows.size() == 0
                ? Optional.<Price>empty()
                : Optional.of(PriceRowMapper.from(rows.iterator().next())))
            .toCompletionStage();
    }

    @Override
    public CompletionStage<List<Price>> findAllByProduct(long productId) {
        return pool.preparedQuery(SELECT_HISTORY)
            .execute(Tuple.of(productId))
            .map(rows -> {
                List<Price> result = new ArrayList<>(rows.size());
                for (Row row : rows) {
                    result.add(PriceRowMapper.from(row));
                }
                return result;
            })
            .toCompletionStage();
    }

    private static Throwable translateInsertError(Throwable error, long productId) {
        if (error instanceof PgException pgException) {
            if (SQLSTATE_EXCLUSION_VIOLATION.equals(pgException.getSqlState())) {
                return new OverlappingPriceException(productId);
            }
            if (SQLSTATE_FOREIGN_KEY_VIOLATION.equals(pgException.getSqlState())) {
                return new ProductNotFoundException(productId);
            }
        }
        return error;
    }
}
