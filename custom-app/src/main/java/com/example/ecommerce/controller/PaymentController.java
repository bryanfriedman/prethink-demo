package com.example.ecommerce.controller;

import com.example.ecommerce.annotations.RateLimited;
import com.example.ecommerce.dto.PaymentRequest;
import com.example.ecommerce.entity.Payment;
import com.example.ecommerce.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @RateLimited(requestsPerMinute = 10)
    @PostMapping("/process")
    public ResponseEntity<Payment> processPayment(@Valid @RequestBody PaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.processPayment(request));
    }

    @PostMapping("/refund")
    public ResponseEntity<Payment> refundPayment(@RequestParam Long paymentId) {
        return ResponseEntity.ok(paymentService.refundPayment(paymentId));
    }
}
