package com.product.pricing.domain;

import com.product.pricing.domain.model.PriceTimeline;

import java.util.concurrent.CompletionStage;

public interface PriceTimelineProvider {

    CompletionStage<PriceTimeline> timelineOf(long productId);

    void invalidate(long productId);
}
