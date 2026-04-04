package com.example.ecommerce.service;

import com.example.ecommerce.annotations.Auditable;
import com.example.ecommerce.annotations.TenantScoped;
import com.example.ecommerce.client.FraudCheckClient;
import com.example.ecommerce.dto.PaymentRequest;
import com.example.ecommerce.entity.Payment;
import com.example.ecommerce.messaging.PaymentEventProducer;
import com.example.ecommerce.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@TenantScoped
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private static final int MAX_RETRIES = 3;
    private static final BigDecimal FRAUD_THRESHOLD = new BigDecimal("5000.00");
    private static final BigDecimal PARTIAL_PAYMENT_MIN = new BigDecimal("10.00");

    private final PaymentRepository paymentRepository;
    private final FraudCheckClient fraudCheckClient;
    private final PaymentEventProducer paymentEventProducer;

    public PaymentService(PaymentRepository paymentRepository,
                          FraudCheckClient fraudCheckClient,
                          PaymentEventProducer paymentEventProducer) {
        this.paymentRepository = paymentRepository;
        this.fraudCheckClient = fraudCheckClient;
        this.paymentEventProducer = paymentEventProducer;
    }

    @Transactional
    public Payment processPayment(PaymentRequest request) {
        BigDecimal amount = request.amount();
        String provider = request.provider();

        // Validate amount
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive");
        }

        // Fraud check with different thresholds per provider
        if (amount.compareTo(FRAUD_THRESHOLD) > 0 || "crypto".equalsIgnoreCase(provider)) {
            Map<String, Object> fraudResult = fraudCheckClient.checkTransaction(request.orderId(), amount);
            String riskLevel = (String) fraudResult.get("riskLevel");

            if ("HIGH".equals(riskLevel)) {
                log.warn("High fraud risk for order {}, amount {}", request.orderId(), amount);
                throw new IllegalStateException("Payment declined: high fraud risk");
            } else if ("MEDIUM".equals(riskLevel)) {
                if (amount.compareTo(new BigDecimal("10000.00")) > 0) {
                    log.warn("Medium risk + high amount for order {}", request.orderId());
                    throw new IllegalStateException("Payment requires manual review");
                }
                log.info("Medium fraud risk accepted for order {}", request.orderId());
            }
        }

        // Currency conversion for international providers
        BigDecimal processedAmount = amount;
        if ("stripe_eu".equalsIgnoreCase(provider) || "adyen".equalsIgnoreCase(provider)) {
            BigDecimal exchangeRate = getExchangeRate(provider);
            if (exchangeRate != null) {
                processedAmount = amount.multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP);
            }
        }

        // Check for existing partial payments on this order
        List<Payment> existingPayments = paymentRepository.findByOrderId(request.orderId());
        BigDecimal alreadyPaid = existingPayments.stream()
                .filter(p -> p.getStatus() == Payment.PaymentStatus.COMPLETED)
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (alreadyPaid.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal remaining = amount.subtract(alreadyPaid);
            if (remaining.compareTo(PARTIAL_PAYMENT_MIN) < 0 && remaining.compareTo(BigDecimal.ZERO) > 0) {
                log.info("Remaining amount {} below minimum, processing full", remaining);
            } else if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalStateException("Order already fully paid");
            }
            processedAmount = remaining;
        }

        // Retry logic for transient provider failures
        Payment payment = null;
        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                payment = new Payment();
                payment.setOrderId(request.orderId());
                payment.setAmount(processedAmount);
                payment.setProvider(provider);
                payment.setStatus(Payment.PaymentStatus.COMPLETED);
                payment.setProcessedAt(LocalDateTime.now());
                payment = paymentRepository.save(payment);
                lastException = null;
                break;
            } catch (Exception e) {
                lastException = e;
                log.warn("Payment attempt {}/{} failed for order {}: {}",
                        attempt, MAX_RETRIES, request.orderId(), e.getMessage());
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(attempt * 500L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Payment interrupted", ie);
                    }
                }
            }
        }

        if (lastException != null) {
            Payment failed = new Payment();
            failed.setOrderId(request.orderId());
            failed.setAmount(processedAmount);
            failed.setProvider(provider);
            failed.setStatus(Payment.PaymentStatus.FAILED);
            failed.setProcessedAt(LocalDateTime.now());
            paymentRepository.save(failed);
            throw new RuntimeException("Payment failed after " + MAX_RETRIES + " attempts", lastException);
        }

        paymentEventProducer.publishPaymentProcessed(payment);
        return payment;
    }

    @Auditable(action = "PAYMENT_REFUND")
    @Transactional
    public Payment refundPayment(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found: " + paymentId));

        if (payment.getStatus() != Payment.PaymentStatus.COMPLETED) {
            throw new IllegalStateException("Can only refund completed payments");
        }

        payment.setStatus(Payment.PaymentStatus.REFUNDED);
        Payment saved = paymentRepository.save(payment);
        paymentEventProducer.publishPaymentRefunded(saved);
        return saved;
    }

    private BigDecimal getExchangeRate(String provider) {
        if ("stripe_eu".equalsIgnoreCase(provider)) {
            return new BigDecimal("0.92");
        } else if ("adyen".equalsIgnoreCase(provider)) {
            return new BigDecimal("0.85");
        }
        return null;
    }
}
