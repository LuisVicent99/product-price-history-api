package com.product.pricing.api.error;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class InvalidDateFormatExceptionMapper implements ExceptionMapper<InvalidDateFormatException> {

    @Override
    public Response toResponse(InvalidDateFormatException exception) {
        return Response.status(Response.Status.BAD_REQUEST)
            .type(Problem.MEDIA_TYPE)
            .entity(Problem.of(400, "Invalid date format", exception.getMessage(), "INVALID_DATE_FORMAT"))
            .build();
    }
}
