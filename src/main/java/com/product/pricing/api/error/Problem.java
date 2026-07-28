package com.product.pricing.api.error;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record Problem(String type, String title, int status, String detail, String code) {

    public static final String MEDIA_TYPE = "application/problem+json";

    public static Problem of(int status, String title, String detail, String code) {
        return new Problem("about:blank", title, status, detail, code);
    }
}
