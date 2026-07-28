package com.product.pricing.api.error;

public class InvalidDateFormatException extends RuntimeException {

    public InvalidDateFormatException(String rawValue) {
        super("Query parameter 'date' must be an ISO-8601 date (yyyy-MM-dd), got: '" + rawValue + "'");
    }
}
