package com.example.ecommerce.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record PaymentRequest(
        @NotNull Long orderId,
        @NotNull BigDecimal amount,
        @NotNull String provider
) {}
