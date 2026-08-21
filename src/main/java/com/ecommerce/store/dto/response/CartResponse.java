package com.ecommerce.store.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record CartResponse(
        List<Line> items,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal total,
        String couponCode,
        int itemCount) {

    public record Line(
            Long productId,
            String sku,
            String name,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal lineTotal) {
    }
}
