package com.example.ecommerce.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class NotificationConfig {

    @Value("${app.notifications.order-confirmation-template:order-confirmation}")
    private String orderConfirmationTemplate;

    @Value("${app.notifications.shipping-template:shipping-notification}")
    private String shippingTemplate;

    @Value("${app.notifications.refund-template:refund-notification}")
    private String refundTemplate;

    @Value("${app.notifications.inventory-alert-email:inventory-team@example.com}")
    private String inventoryAlertEmail;

    @Value("${app.notifications.from-address:noreply@example.com}")
    private String fromAddress;

    public String getOrderConfirmationTemplate() { return orderConfirmationTemplate; }
    public String getShippingTemplate() { return shippingTemplate; }
    public String getRefundTemplate() { return refundTemplate; }
    public String getInventoryAlertEmail() { return inventoryAlertEmail; }
    public String getFromAddress() { return fromAddress; }
}
