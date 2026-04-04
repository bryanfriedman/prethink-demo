package com.example.ecommerce.service;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class EmailService {

    public void sendEmail(String to, String subject, String templateName, Map<String, Object> variables) {
        // Stub: would delegate to an email provider (SendGrid, SES, etc.)
    }

    public void sendBulkEmail(java.util.List<String> recipients, String subject, String templateName,
                              Map<String, Object> variables) {
        // Stub: bulk email sending
    }
}
