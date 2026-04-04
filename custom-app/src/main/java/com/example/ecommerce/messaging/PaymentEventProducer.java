package com.example.ecommerce.messaging;

import com.example.ecommerce.entity.Payment;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PaymentEventProducer {

    private static final String TOPIC = "payment-processed";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public PaymentEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishPaymentProcessed(Payment payment) {
        kafkaTemplate.send(TOPIC, payment.getId().toString(), Map.of(
                "eventType", "PAYMENT_PROCESSED",
                "paymentId", payment.getId(),
                "orderId", payment.getOrderId(),
                "amount", payment.getAmount().toString(),
                "provider", payment.getProvider()
        ));
    }

    public void publishPaymentRefunded(Payment payment) {
        kafkaTemplate.send(TOPIC, payment.getId().toString(), Map.of(
                "eventType", "PAYMENT_REFUNDED",
                "paymentId", payment.getId(),
                "orderId", payment.getOrderId(),
                "amount", payment.getAmount().toString()
        ));
    }
}
