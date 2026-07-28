package com.product.pricing.domain.model;

import com.product.pricing.domain.error.InvalidDateRangeException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.Month;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DateIntervalTest {

    private static final LocalDate JAN_1 = LocalDate.of(2026, Month.JANUARY, 1);
    private static final LocalDate JAN_10 = LocalDate.of(2026, Month.JANUARY, 10);
    private static final LocalDate JAN_20 = LocalDate.of(2026, Month.JANUARY, 20);

    @Test
    void allowsNullEndDateAsOpenEnded() {
        DateInterval interval = new DateInterval(JAN_1, null);
        assertTrue(interval.contains(LocalDate.of(2099, Month.DECEMBER, 31)));
    }

    @Test
    void rejectsMissingInitDate() {
        assertThrows(InvalidDateRangeException.class, () -> new DateInterval(null, JAN_10));
    }

    @Test
    void rejectsInitDateEqualToEndDate() {
        assertThrows(InvalidDateRangeException.class, () -> new DateInterval(JAN_10, JAN_10));
    }

    @Test
    void rejectsInitDateAfterEndDate() {
        assertThrows(InvalidDateRangeException.class, () -> new DateInterval(JAN_20, JAN_10));
    }

    @Test
    void containsIsInclusiveAtBothBoundaries() {
        DateInterval interval = new DateInterval(JAN_10, JAN_20);
        assertTrue(interval.contains(JAN_10));
        assertTrue(interval.contains(JAN_20));
        assertFalse(interval.contains(JAN_10.minusDays(1)));
        assertFalse(interval.contains(JAN_20.plusDays(1)));
    }

    @Test
    void openEndedContainsFromInitDateOn() {
        DateInterval interval = new DateInterval(JAN_10, null);
        assertFalse(interval.contains(JAN_10.minusDays(1)));
        assertTrue(interval.contains(JAN_10));
    }

}
