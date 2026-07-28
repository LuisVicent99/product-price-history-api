package com.product.pricing.api.error;

import com.product.pricing.domain.error.OverlappingPriceException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class OverlappingPriceExceptionMapper implements ExceptionMapper<OverlappingPriceException> {

    @Override
    public Response toResponse(OverlappingPriceException exception) {
        return Response.status(Response.Status.CONFLICT)
            .type(Problem.MEDIA_TYPE)
            .entity(Problem.of(409, "Overlapping price", exception.getMessage(), "PRICE_OVERLAP"))
            .build();
    }
}
