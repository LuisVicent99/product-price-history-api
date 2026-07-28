package com.product.pricing.api.error;

import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class MismatchedInputExceptionMapper implements ExceptionMapper<MismatchedInputException> {

    @Override
    public Response toResponse(MismatchedInputException exception) {
        return Response.status(Response.Status.BAD_REQUEST)
            .type(Problem.MEDIA_TYPE)
            .entity(Problem.of(400, "Invalid request body",
                "request body must be a valid JSON object", "VALIDATION_ERROR"))
            .build();
    }
}
