package com.product.pricing.domain;

import com.product.pricing.domain.model.Product;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

public interface ProductRepository {

    CompletionStage<Product> insert(String name, String description);

    CompletionStage<Optional<Product>> findById(long id);

    CompletionStage<Boolean> exists(long id);
}
