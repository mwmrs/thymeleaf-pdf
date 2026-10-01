package com.example.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Daten für die Rechnung. In einer echten Anwendung würde dieses DTO
 * aus JPA-Entitäten (z. B. {@code OrderEntity}) befüllt.
 */
public record OrderDto(String id,
                       LocalDate date,
                       String customerName,
                       String customerAddress,
                       List<OrderItemDto> items) {

    public BigDecimal total() {
        return items.stream()
                .map(OrderItemDto::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
