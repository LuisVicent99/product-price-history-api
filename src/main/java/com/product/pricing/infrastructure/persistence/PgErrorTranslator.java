package com.product.pricing.infrastructure.persistence;

import com.product.pricing.domain.error.InvalidRequestException;
import com.product.pricing.domain.error.OverlappingPriceException;
import com.product.pricing.domain.error.ProductNotFoundException;
import io.vertx.pgclient.PgException;

import java.util.Set;

final class PgErrorTranslator {

    private static final String EXCLUSION_VIOLATION = "23P01";
    private static final String FOREIGN_KEY_VIOLATION = "23503";
    private static final String DEADLOCK_DETECTED = "40P01";
    private static final Set<String> INVALID_DATA = Set.of("23502", "23514", "22001");

    private PgErrorTranslator() {
    }

    static boolean isDeadlock(Throwable error) {
        return error instanceof PgException pgException
            && DEADLOCK_DETECTED.equals(pgException.getSqlState());
    }

    static Throwable translatePriceInsert(Throwable error, long productId) {
        if (error instanceof PgException pgException) {
            String state = pgException.getSqlState();
            if (EXCLUSION_VIOLATION.equals(state)) {
                return new OverlappingPriceException(productId);
            }
            if (FOREIGN_KEY_VIOLATION.equals(state)) {
                return new ProductNotFoundException(productId);
            }
            if (INVALID_DATA.contains(state)) {
                return new InvalidRequestException(
                    "price data rejected by database constraints: " + pgException.getErrorMessage());
            }
        }
        return error;
    }

    static Throwable translateProductInsert(Throwable error) {
        if (error instanceof PgException pgException && INVALID_DATA.contains(pgException.getSqlState())) {
            return new InvalidRequestException(
                "product data rejected by database constraints: " + pgException.getErrorMessage());
        }
        return error;
    }
}
