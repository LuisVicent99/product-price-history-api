package com.product.pricing.api.error;

import com.product.pricing.domain.error.ProductNotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ProductNotFoundExceptionMapper implements ExceptionMapper<ProductNotFoundException> {

    @Override
    public Response toResponse(ProductNotFoundException exception) {
        return Response.status(Response.Status.NOT_FOUND)
            .type(Problem.MEDIA_TYPE)
            .entity(Problem.of(404, "Product not found", exception.getMessage(), "PRODUCT_NOT_FOUND"))
            .build();
    }
}
