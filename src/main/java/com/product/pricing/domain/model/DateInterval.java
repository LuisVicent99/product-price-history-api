package com.product.pricing.domain.model;

import com.product.pricing.domain.error.InvalidDateRangeException;

import java.time.LocalDate;

public record DateInterval(LocalDate initDate, LocalDate endDate) {

    public DateInterval {
        if (initDate == null) {
            throw new InvalidDateRangeException("initDate is required");
        }
        if (endDate != null && !initDate.isBefore(endDate)) {
            throw new InvalidDateRangeException(
                "initDate (" + initDate + ") must be strictly before endDate (" + endDate + ")");
        }
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(initDate) && (endDate == null || !date.isAfter(endDate));
    }

    public boolean overlaps(DateInterval other) {
        return (other.endDate == null || !initDate.isAfter(other.endDate))
            && (endDate == null || !other.initDate.isAfter(endDate));
    }
}
