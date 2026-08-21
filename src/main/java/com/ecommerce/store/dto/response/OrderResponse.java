package com.ecommerce.store.dto.response;

import com.ecommerce.store.model.Order;
import com.ecommerce.store.model.ShippingAddress;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        String orderNumber,
        String status,
        String currency,
        BigDecimal subtotalAmount,
        BigDecimal discountAmount,
        String couponCode,
        BigDecimal totalAmount,
        Instant createdAt,
        ShippingAddress shippingAddress,
        List<Line> items) {

    public record Line(
            String productName,
            String productSku,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal subtotal) {
    }

    public static OrderResponse from(Order order) {
        List<Line> lines = order.getItems().stream()
                .map(i -> new Line(
                        i.getProductName(),
                        i.getProductSku(),
                        i.getQuantity(),
                        i.getUnitPrice(),
                        i.getSubtotal()))
                .toList();

        return new OrderResponse(
                order.getOrderNumber(),
                order.getStatus().name(),
                order.getCurrency(),
                order.getSubtotalAmount(),
                order.getDiscountAmount(),
                order.getCouponCode(),
                order.getTotalAmount(),
                order.getCreatedAt(),
                order.getShippingAddress(),
                lines);
    }
}
