package com.ecommerce.store.controller.api;

import com.ecommerce.store.dto.request.CreateIntentRequest;
import com.ecommerce.store.dto.response.OrderResponse;
import com.ecommerce.store.service.PaymentService;
import com.ecommerce.store.service.StripeService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.model.Event;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentApiController {

    private final PaymentService payments;
    private final StripeService stripe;
    private final ObjectMapper mapper;

    public PaymentApiController(PaymentService payments, StripeService stripe, ObjectMapper mapper) {
        this.payments = payments;
        this.stripe = stripe;
        this.mapper = mapper;
    }

    /** Create a payment intent for one of the caller's orders. */
    @PostMapping("/create-intent")
    public Map<String, String> createIntent(@Valid @RequestBody CreateIntentRequest request) {
        return Map.of("clientSecret", payments.createIntent(request.orderId()));
    }

    /** Mock/dev: confirm payment succeeded and settle the order. */
    @PostMapping("/confirm")
    public OrderResponse confirm(@Valid @RequestBody CreateIntentRequest request) {
        return payments.confirmMockPayment(request.orderId());
    }

    /** Stripe webhook (public but signature-verified). */
    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> webhook(
            @RequestBody(required = false) String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String signature) {

        String body = payload == null ? "" : payload;
        Event event;
        try {
            event = stripe.constructWebhookEvent(body, signature);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Webhook signature verification failed"));
        }

        if ("payment_intent.succeeded".equals(event.getType())) {
            try {
                JsonNode root = mapper.readTree(body);
                String piId = root.path("data").path("object").path("id").asText(null);
                if (piId != null) {
                    payments.markPaidByIntent(piId);
                }
            } catch (Exception ignored) {
                // malformed payload after a valid signature — acknowledge anyway
            }
        }
        return ResponseEntity.ok(Map.of("received", true));
    }
}
