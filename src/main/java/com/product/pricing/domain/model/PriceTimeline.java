package com.product.pricing.domain.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class PriceTimeline {

    private final List<Price> prices;
    private final long[] initEpochDays;

    public PriceTimeline(List<Price> prices) {
        List<Price> sorted = new ArrayList<>(prices);
        sorted.sort(Comparator.comparing(price -> price.validity().initDate()));
        this.prices = List.copyOf(sorted);
        this.initEpochDays = new long[this.prices.size()];
        for (int i = 0; i < this.prices.size(); i++) {
            this.initEpochDays[i] = this.prices.get(i).validity().initDate().toEpochDay();
        }
    }

    public List<Price> prices() {
        return prices;
    }

    public Optional<Price> findAt(LocalDate date) {
        long day = date.toEpochDay();
        int low = 0;
        int high = initEpochDays.length - 1;
        int latestStartedIndex = -1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (initEpochDays[mid] <= day) {
                latestStartedIndex = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        if (latestStartedIndex < 0) {
            return Optional.empty();
        }
        Price candidate = prices.get(latestStartedIndex);
        return candidate.validity().contains(date) ? Optional.of(candidate) : Optional.empty();
    }
}
