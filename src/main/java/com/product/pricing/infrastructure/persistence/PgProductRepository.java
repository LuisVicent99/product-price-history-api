package com.product.pricing.infrastructure.persistence;

import com.product.pricing.domain.ProductRepository;
import com.product.pricing.domain.model.Product;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Tuple;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

@ApplicationScoped
public class PgProductRepository implements ProductRepository {

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
            .toCompletionStage();
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
