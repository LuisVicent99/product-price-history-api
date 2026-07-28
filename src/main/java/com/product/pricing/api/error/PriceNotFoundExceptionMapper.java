package com.product.pricing.api.error;

import com.product.pricing.domain.error.PriceNotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class PriceNotFoundExceptionMapper implements ExceptionMapper<PriceNotFoundException> {

    @Override
    public Response toResponse(PriceNotFoundException exception) {
        return Response.status(Response.Status.NOT_FOUND)
            .type(Problem.MEDIA_TYPE)
            .entity(Problem.of(404, "Price not found", exception.getMessage(), "PRICE_NOT_FOUND"))
            .build();
    }
}
