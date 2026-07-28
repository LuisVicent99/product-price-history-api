package com.product.pricing.api;

import com.product.pricing.api.dto.ProductRequest;
import com.product.pricing.api.dto.ProductView;
import com.product.pricing.domain.PricingService;
import com.product.pricing.domain.error.InvalidRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.util.concurrent.CompletionStage;

@Path("/products")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ProductResource {

    private final PricingService service;

    public ProductResource(PricingService service) {
        this.service = service;
    }

    @POST
    public CompletionStage<Response> create(ProductRequest request) {
        if (request == null) {
            throw new InvalidRequestException("request body is required");
        }
        return service.createProduct(request.name(), request.description())
            .thenApply(product -> Response
                .created(URI.create("/products/" + product.id()))
                .entity(ProductView.from(product))
                .build());
    }
}
