package com.example.report;

import java.math.BigDecimal;

/** Eine Rechnungsposition. */
public record OrderItemDto(String description, int quantity, BigDecimal price) {

    public BigDecimal total() {
        return price.multiply(BigDecimal.valueOf(quantity));
    }
}
