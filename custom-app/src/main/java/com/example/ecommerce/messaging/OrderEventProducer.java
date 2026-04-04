package com.example.ecommerce.messaging;

import com.example.ecommerce.entity.Order;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class OrderEventProducer {

    private static final String TOPIC = "order-events";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public OrderEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishOrderCreated(Order order) {
        kafkaTemplate.send(TOPIC, order.getId().toString(), Map.of(
                "eventType", "ORDER_CREATED",
                "orderId", order.getId(),
                "customerId", order.getCustomerId(),
                "status", order.getStatus().name()
        ));
    }

    public void publishOrderCancelled(Order order) {
        kafkaTemplate.send(TOPIC, order.getId().toString(), Map.of(
                "eventType", "ORDER_CANCELLED",
                "orderId", order.getId(),
                "customerId", order.getCustomerId()
        ));
    }
}
