package com.product.pricing.api.dto;

import com.product.pricing.domain.model.PriceTimeline;
import com.product.pricing.domain.model.ProductPriceHistory;

import java.util.List;

public record PriceHistoryView(String name, String description, List<PriceView> prices) {

    public static PriceHistoryView from(ProductPriceHistory history) {
        PriceTimeline timeline = history.prices();
        List<PriceView> views = timeline.prices().stream().map(PriceView::from).toList();
        return new PriceHistoryView(history.product().name(), history.product().description(), views);
    }
}
