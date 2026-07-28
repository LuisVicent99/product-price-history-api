package com.product.pricing.api;

import com.product.pricing.api.dto.CurrentPriceView;
import com.product.pricing.api.dto.PriceHistoryView;
import com.product.pricing.api.dto.PriceRequest;
import com.product.pricing.api.dto.PriceView;
import com.product.pricing.api.error.InvalidDateFormatException;
import com.product.pricing.domain.PricingService;
import com.product.pricing.domain.error.InvalidRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.concurrent.CompletionStage;

@Path("/products/{productId}/prices")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PriceResource {

    private final PricingService service;

    public PriceResource(PricingService service) {
        this.service = service;
    }

    @POST
    public CompletionStage<Response> add(@PathParam("productId") long productId, PriceRequest request) {
        if (request == null) {
            throw new InvalidRequestException("request body is required");
        }
        return service.addPrice(productId, request.value(), request.currency(),
                request.initDate(), request.endDate())
            .thenApply(price -> Response
                .created(URI.create("/products/" + productId + "/prices/" + price.id()))
                .entity(PriceView.from(price))
                .build());
    }

    @GET
    public CompletionStage<Response> get(@PathParam("productId") long productId,
                                         @QueryParam("date") String date) {
        if (date == null) {
            return service.history(productId)
                .thenApply(history -> Response.ok(PriceHistoryView.from(history)).build());
        }
        LocalDate parsed = parseIsoDate(date);
        return service.priceAt(productId, parsed)
            .thenApply(price -> Response.ok(CurrentPriceView.from(price)).build());
    }

    private static LocalDate parseIsoDate(String raw) {
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException _) {
            throw new InvalidDateFormatException(raw);
        }
    }
}
