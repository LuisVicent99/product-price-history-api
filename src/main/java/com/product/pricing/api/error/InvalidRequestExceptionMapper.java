package com.product.pricing.api.error;

import com.product.pricing.domain.error.InvalidRequestException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class InvalidRequestExceptionMapper implements ExceptionMapper<InvalidRequestException> {

    @Override
    public Response toResponse(InvalidRequestException exception) {
        return Response.status(Response.Status.BAD_REQUEST)
            .type(Problem.MEDIA_TYPE)
            .entity(Problem.of(400, "Invalid request", exception.getMessage(), "VALIDATION_ERROR"))
            .build();
    }
}
