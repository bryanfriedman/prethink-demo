package com.example.ecommerce.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Summary view of an order for list/search results.
 */
@Getter
@Setter
public class OrderSummaryDTO {

    private Long orderId;
    private Long customerId;
    private String customerName;
    private String customerEmail;
    private String status;
    private BigDecimal totalAmount;
    private BigDecimal discountApplied;
    private int itemCount;
    private String shippingMethod;
    private String trackingNumber;
    private LocalDateTime createdAt;
    private LocalDateTime lastUpdatedAt;

    public boolean isPaid() {
        return "CONFIRMED".equals(status) || "PROCESSING".equals(status)
                || "SHIPPED".equals(status) || "DELIVERED".equals(status);
    }
}
