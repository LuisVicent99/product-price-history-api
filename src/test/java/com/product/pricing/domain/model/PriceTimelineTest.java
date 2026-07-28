package com.product.pricing.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PriceTimelineTest {

    private static Price price(long id, LocalDate initDate, LocalDate endDate) {
        return new Price(id, 1L, new BigDecimal("10.00"), "EUR", new DateInterval(initDate, endDate));
    }

    private static LocalDate date(int month, int day) {
        return LocalDate.of(2026, month, day);
    }

    @Test
    void findAtOnEmptyTimelineReturnsEmpty() {
        PriceTimeline timeline = new PriceTimeline(List.of());
        assertTrue(timeline.findAt(date(1, 1)).isEmpty());
    }

    @Test
    void findAtBeforeFirstInitDateReturnsEmpty() {
        PriceTimeline timeline = new PriceTimeline(List.of(price(1, date(1, 10), date(1, 20))));
        assertTrue(timeline.findAt(date(1, 9)).isEmpty());
    }

    @Test
    void findAtExactInitDateReturnsThePrice() {
        PriceTimeline timeline = new PriceTimeline(List.of(price(1, date(1, 10), date(1, 20))));
        assertEquals(1L, timeline.findAt(date(1, 10)).orElseThrow().id());
    }

    @Test
    void findAtExactEndDateReturnsThePriceBecauseEndDateIsInclusive() {
        PriceTimeline timeline = new PriceTimeline(List.of(price(1, date(1, 10), date(1, 20))));
        assertEquals(1L, timeline.findAt(date(1, 20)).orElseThrow().id());
    }

    @Test
    void findAtDayAfterEndDateReturnsEmpty() {
        PriceTimeline timeline = new PriceTimeline(List.of(price(1, date(1, 10), date(1, 20))));
        assertTrue(timeline.findAt(date(1, 21)).isEmpty());
    }

    @Test
    void findAtInGapBetweenPricesReturnsEmpty() {
        PriceTimeline timeline = new PriceTimeline(List.of(
            price(1, date(1, 1), date(1, 5)),
            price(2, date(1, 10), null)));
        assertTrue(timeline.findAt(date(1, 7)).isEmpty());
    }

    @Test
    void openEndedPriceMatchesFarFutureDates() {
        PriceTimeline timeline = new PriceTimeline(List.of(price(1, date(1, 10), null)));
        assertEquals(1L, timeline.findAt(LocalDate.of(2099, Month.DECEMBER, 31)).orElseThrow().id());
    }

    @Test
    void contiguousPricesResolveEachBoundaryToItsOwnPrice() {
        PriceTimeline timeline = new PriceTimeline(List.of(
            price(1, date(1, 1), date(1, 10)),
            price(2, date(1, 11), date(1, 20))));
        assertEquals(1L, timeline.findAt(date(1, 10)).orElseThrow().id());
        assertEquals(2L, timeline.findAt(date(1, 11)).orElseThrow().id());
        assertTrue(timeline.findAt(date(1, 21)).isEmpty());
    }

    @Test
    void unsortedInputIsSortedByInitDate() {
        PriceTimeline timeline = new PriceTimeline(List.of(
            price(2, date(3, 1), null),
            price(1, date(1, 1), date(2, 28))));
        assertEquals(List.of(1L, 2L), timeline.prices().stream().map(Price::id).toList());
    }

    @Test
    void pricesListIsImmutable() {
        PriceTimeline timeline = new PriceTimeline(List.of(price(1, date(1, 1), null)));
        List<Price> prices = timeline.prices();
        Price extra = price(2, date(2, 1), null);
        assertThrows(UnsupportedOperationException.class, () -> prices.add(extra));
    }

    @Test
    void findAtPicksTheRightPriceAmongMany() {
        PriceTimeline timeline = new PriceTimeline(List.of(
            price(1, date(1, 1), date(1, 31)),
            price(2, date(2, 1), date(2, 28)),
            price(3, date(3, 1), date(3, 31)),
            price(4, date(5, 1), null)));
        assertEquals(2L, timeline.findAt(date(2, 15)).orElseThrow().id());
        assertEquals(3L, timeline.findAt(date(3, 31)).orElseThrow().id());
        assertTrue(timeline.findAt(date(4, 15)).isEmpty());
        assertEquals(Optional.of(4L), timeline.findAt(date(6, 1)).map(Price::id));
    }
}
