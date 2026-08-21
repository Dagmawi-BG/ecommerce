package com.ecommerce.store.dto.request;

import jakarta.validation.constraints.NotNull;

public record CreateIntentRequest(@NotNull Long orderId) {
}
