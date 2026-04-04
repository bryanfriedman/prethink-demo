package com.example.ecommerce.dto;

import jakarta.validation.constraints.NotNull;

public record InventoryAdjustmentRequest(
        @NotNull Integer quantityChange,
        @NotNull String reason
) {}
