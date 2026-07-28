package com.product.pricing.api.error;

import com.product.pricing.domain.error.InvalidDateRangeException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class InvalidDateRangeExceptionMapper implements ExceptionMapper<InvalidDateRangeException> {

    @Override
    public Response toResponse(InvalidDateRangeException exception) {
        return Response.status(Response.Status.BAD_REQUEST)
            .type(Problem.MEDIA_TYPE)
            .entity(Problem.of(400, "Invalid date range", exception.getMessage(), "INVALID_DATE_RANGE"))
            .build();
    }
}
