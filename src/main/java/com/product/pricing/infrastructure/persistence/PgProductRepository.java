package com.product.pricing.infrastructure.persistence;

import com.product.pricing.domain.ProductRepository;
import com.product.pricing.domain.error.InvalidRequestException;
import com.product.pricing.domain.model.Product;
import io.vertx.core.Future;
import io.vertx.pgclient.PgException;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Tuple;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

@ApplicationScoped
public class PgProductRepository implements ProductRepository {

    private static final List<String> SQLSTATE_INVALID_DATA = List.of("23502", "23514", "22001");

    private static final String INSERT =
        "INSERT INTO product (name, description) VALUES ($1, $2) RETURNING id, name, description";
    private static final String SELECT_BY_ID =
        "SELECT id, name, description FROM product WHERE id = $1";
    private static final String SELECT_EXISTS =
        "SELECT 1 FROM product WHERE id = $1";

    private final Pool pool;

    public PgProductRepository(Pool pool) {
        this.pool = pool;
    }

    @Override
    public CompletionStage<Product> insert(String name, String description) {
        return pool.preparedQuery(INSERT)
            .execute(Tuple.of(name, description))
            .map(rows -> ProductRowMapper.from(rows.iterator().next()))
            .recover(error -> Future.failedFuture(translateInsertError(error)))
            .toCompletionStage();
    }

    private static Throwable translateInsertError(Throwable error) {
        if (error instanceof PgException pgException
            && SQLSTATE_INVALID_DATA.contains(pgException.getSqlState())) {
            return new InvalidRequestException(
                "product data rejected by database constraints: " + pgException.getErrorMessage());
        }
        return error;
    }

    @Override
    public CompletionStage<Optional<Product>> findById(long id) {
        return pool.preparedQuery(SELECT_BY_ID)
            .execute(Tuple.of(id))
            .map(rows -> rows.size() == 0
                ? Optional.<Product>empty()
                : Optional.of(ProductRowMapper.from(rows.iterator().next())))
            .toCompletionStage();
    }

    @Override
    public CompletionStage<Boolean> exists(long id) {
        return pool.preparedQuery(SELECT_EXISTS)
            .execute(Tuple.of(id))
            .map(rows -> rows.size() > 0)
            .toCompletionStage();
    }
}
