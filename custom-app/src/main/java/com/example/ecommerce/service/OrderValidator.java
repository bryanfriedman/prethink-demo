package com.example.ecommerce.service;

import com.example.ecommerce.entity.Order;
import org.springframework.stereotype.Component;

@Component
public class OrderValidator {

    public boolean validateOrderForCancellation(Order order) {
        return order.getStatus() != Order.OrderStatus.SHIPPED
                && order.getStatus() != Order.OrderStatus.DELIVERED;
    }

    public boolean validateOrderTransition(Order.OrderStatus current, Order.OrderStatus target) {
        return switch (current) {
            case PENDING -> target == Order.OrderStatus.CONFIRMED || target == Order.OrderStatus.CANCELLED;
            case CONFIRMED -> target == Order.OrderStatus.PROCESSING || target == Order.OrderStatus.CANCELLED;
            case PROCESSING -> target == Order.OrderStatus.SHIPPED || target == Order.OrderStatus.CANCELLED;
            case SHIPPED -> target == Order.OrderStatus.DELIVERED;
            default -> false;
        };
    }
}
