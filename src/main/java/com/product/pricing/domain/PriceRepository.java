package com.product.pricing.domain;

import com.product.pricing.domain.model.DateInterval;
import com.product.pricing.domain.model.Price;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

public interface PriceRepository {

    CompletionStage<Price> insert(long productId, BigDecimal amount, String currency, DateInterval validity);

    CompletionStage<Optional<Price>> findAt(long productId, LocalDate date);

    CompletionStage<List<Price>> findAllByProduct(long productId);
}
