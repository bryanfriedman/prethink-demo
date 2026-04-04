package com.example.ecommerce.service;

import com.example.ecommerce.annotations.Auditable;
import com.example.ecommerce.annotations.TenantScoped;
import com.example.ecommerce.dto.OrderRequest;
import com.example.ecommerce.entity.Customer;
import com.example.ecommerce.entity.Order;
import com.example.ecommerce.entity.OrderItem;
import com.example.ecommerce.messaging.OrderEventProducer;
import com.example.ecommerce.repository.CustomerRepository;
import com.example.ecommerce.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles order lifecycle management, reporting/analytics, and customer notifications.
 * This class has grown organically over several quarters as the team added features.
 */
@Service
@TenantScoped
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    // --- Group 1: Order lifecycle fields ---
    private final OrderRepository orderRepository;
    private final OrderValidator orderValidator;

    // --- Group 2: Reporting/analytics fields ---
    private final ReportingDao reportingDao;
    private final MetricsCollector metricsCollector;

    // --- Group 3: Notification fields ---
    private final EmailService emailService;
    private final NotificationConfig notificationConfig;

    // Other dependencies used across groups
    private final OrderEventProducer orderEventProducer;
    private final CustomerRepository customerRepository;

    public OrderService(OrderRepository orderRepository,
                        OrderValidator orderValidator,
                        ReportingDao reportingDao,
                        MetricsCollector metricsCollector,
                        EmailService emailService,
                        NotificationConfig notificationConfig,
                        OrderEventProducer orderEventProducer,
                        CustomerRepository customerRepository) {
        this.orderRepository = orderRepository;
        this.orderValidator = orderValidator;
        this.reportingDao = reportingDao;
        this.metricsCollector = metricsCollector;
        this.emailService = emailService;
        this.notificationConfig = notificationConfig;
        this.orderEventProducer = orderEventProducer;
        this.customerRepository = customerRepository;
    }

    // =========================================================================
    // Group 1: Order Lifecycle — uses orderRepository, orderValidator
    // =========================================================================

    @Transactional
    public Order createOrder(OrderRequest request) {
        Order order = new Order();
        order.setCustomerId(request.customerId());
        order.setStatus(Order.OrderStatus.PENDING);
        order.setCreatedAt(LocalDateTime.now());
        Order saved = orderRepository.save(order);
        orderEventProducer.publishOrderCreated(saved);
        return saved;
    }

    public Order getOrder(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found: " + id));
    }

    public List<Order> getOrdersByCustomer(Long customerId) {
        return orderRepository.findByCustomerId(customerId);
    }

    public Order.OrderStatus getOrderStatus(Long id) {
        return getOrder(id).getStatus();
    }

    @Auditable(action = "ORDER_CANCEL")
    @Transactional
    public Order cancelOrder(Long id) {
        Order order = getOrder(id);
        if (!orderValidator.validateOrderForCancellation(order)) {
            throw new IllegalStateException("Cannot cancel order in status: " + order.getStatus());
        }
        order.setStatus(Order.OrderStatus.CANCELLED);
        Order saved = orderRepository.save(order);
        orderEventProducer.publishOrderCancelled(saved);
        return saved;
    }

    @Transactional
    public Order updateOrderStatus(Long id, Order.OrderStatus newStatus) {
        Order order = getOrder(id);
        if (!orderValidator.validateOrderTransition(order.getStatus(), newStatus)) {
            throw new IllegalStateException(
                    "Invalid transition from " + order.getStatus() + " to " + newStatus);
        }
        order.setStatus(newStatus);
        return orderRepository.save(order);
    }

    public List<Order> getOrderHistory(Long customerId, Order.OrderStatus statusFilter,
                                       LocalDateTime from, LocalDateTime to) {
        List<Order> orders = orderRepository.findByCustomerId(customerId);
        return orders.stream()
                .filter(o -> statusFilter == null || o.getStatus() == statusFilter)
                .filter(o -> from == null || !o.getCreatedAt().isBefore(from))
                .filter(o -> to == null || !o.getCreatedAt().isAfter(to))
                .sorted(Comparator.comparing(Order::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    // =========================================================================
    // Group 2: Reporting & Analytics — uses reportingDao, metricsCollector
    // =========================================================================

    public Map<String, Object> generateSalesReport(LocalDate startDate, LocalDate endDate) {
        List<Map<String, Object>> dailySales = reportingDao.querySalesByDateRange(startDate, endDate);
        BigDecimal totalRevenue = reportingDao.queryTotalRevenue(startDate, endDate);

        metricsCollector.incrementCounter("reports.sales.generated");
        metricsCollector.recordGauge("reports.sales.last_total_revenue", totalRevenue);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("startDate", startDate);
        report.put("endDate", endDate);
        report.put("totalRevenue", totalRevenue);
        report.put("dailyBreakdown", dailySales);
        report.put("averageDailyRevenue", dailySales.isEmpty()
                ? BigDecimal.ZERO
                : totalRevenue.divide(BigDecimal.valueOf(dailySales.size()), 2, RoundingMode.HALF_UP));

        long totalOrders = dailySales.stream()
                .mapToLong(row -> ((Number) row.getOrDefault("order_count", 0)).longValue())
                .sum();
        report.put("totalOrders", totalOrders);
        report.put("generatedAt", LocalDateTime.now());
        return report;
    }

    public List<Map<String, Object>> calculateRevenueByCategory() {
        List<Map<String, Object>> results = reportingDao.queryRevenueByCategory();
        metricsCollector.incrementCounter("reports.revenue_by_category.generated");

        BigDecimal total = results.stream()
                .map(row -> (BigDecimal) row.get("revenue"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return results.stream().map(row -> {
            Map<String, Object> enriched = new LinkedHashMap<>(row);
            BigDecimal revenue = (BigDecimal) row.get("revenue");
            if (total.compareTo(BigDecimal.ZERO) > 0) {
                enriched.put("percentage", revenue.divide(total, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)));
            } else {
                enriched.put("percentage", BigDecimal.ZERO);
            }
            return enriched;
        }).collect(Collectors.toList());
    }

    public List<Map<String, Object>> getTopSellingProducts(int limit) {
        metricsCollector.incrementCounter("reports.top_products.generated");
        List<Map<String, Object>> topProducts = reportingDao.queryTopProducts(limit);

        long rank = 1;
        for (Map<String, Object> product : topProducts) {
            product.put("rank", rank++);
        }
        return topProducts;
    }

    public Map<String, Object> exportOrderData(LocalDate startDate, LocalDate endDate, String format) {
        List<Map<String, Object>> salesData = reportingDao.querySalesByDateRange(startDate, endDate);
        metricsCollector.incrementCounter("reports.export." + format);

        Map<String, Object> exportResult = new LinkedHashMap<>();
        exportResult.put("format", format);
        exportResult.put("recordCount", salesData.size());
        exportResult.put("data", salesData);
        exportResult.put("exportedAt", LocalDateTime.now());

        if ("csv".equalsIgnoreCase(format)) {
            exportResult.put("contentType", "text/csv");
        } else if ("json".equalsIgnoreCase(format)) {
            exportResult.put("contentType", "application/json");
        } else {
            exportResult.put("contentType", "application/octet-stream");
        }
        return exportResult;
    }

    // =========================================================================
    // Group 3: Notifications — uses emailService, notificationConfig
    // =========================================================================

    public void sendOrderConfirmation(Long orderId, String customerEmail) {
        String template = notificationConfig.getOrderConfirmationTemplate();
        Map<String, Object> variables = Map.of(
                "orderId", orderId,
                "fromAddress", notificationConfig.getFromAddress()
        );
        emailService.sendEmail(customerEmail, "Order Confirmation #" + orderId, template, variables);
        log.info("Sent order confirmation for order {} to {}", orderId, customerEmail);
    }

    public void sendShippingNotification(Long orderId, String customerEmail, String trackingNumber) {
        String template = notificationConfig.getShippingTemplate();
        Map<String, Object> variables = Map.of(
                "orderId", orderId,
                "trackingNumber", trackingNumber,
                "fromAddress", notificationConfig.getFromAddress()
        );
        emailService.sendEmail(customerEmail, "Your Order #" + orderId + " Has Shipped", template, variables);
        log.info("Sent shipping notification for order {} to {}", orderId, customerEmail);
    }

    public void sendRefundNotification(Long orderId, String customerEmail, BigDecimal refundAmount) {
        String template = notificationConfig.getRefundTemplate();
        Map<String, Object> variables = Map.of(
                "orderId", orderId,
                "refundAmount", refundAmount,
                "fromAddress", notificationConfig.getFromAddress()
        );
        emailService.sendEmail(customerEmail, "Refund Processed for Order #" + orderId, template, variables);
        log.info("Sent refund notification for order {} to {}", orderId, customerEmail);
    }

    public void notifyInventoryTeam(String sku, int currentQuantity, int threshold) {
        String alertEmail = notificationConfig.getInventoryAlertEmail();
        Map<String, Object> variables = Map.of(
                "sku", sku,
                "currentQuantity", currentQuantity,
                "threshold", threshold
        );
        emailService.sendEmail(alertEmail, "Low Inventory Alert: " + sku,
                "inventory-alert", variables);
        log.info("Sent low inventory alert for SKU {} to {}", sku, alertEmail);
    }

    // =========================================================================
    // Feature Envy: This method mostly accesses Customer fields
    // =========================================================================

    public String formatCustomerInvoice(Long orderId) {
        Order order = getOrder(orderId);
        Customer customer = customerRepository.findById(order.getCustomerId())
                .orElseThrow(() -> new RuntimeException("Customer not found"));

        StringBuilder invoice = new StringBuilder();
        String lang = customer.getPreferredLanguage() != null ? customer.getPreferredLanguage() : "en";

        if ("es".equals(lang)) {
            invoice.append("Factura para: ").append(customer.getName()).append("\n");
        } else if ("fr".equals(lang)) {
            invoice.append("Facture pour: ").append(customer.getName()).append("\n");
        } else {
            invoice.append("Invoice for: ").append(customer.getName()).append("\n");
        }

        invoice.append("Email: ").append(customer.getEmail()).append("\n");
        invoice.append("Address: ").append(customer.getAddress()).append("\n");

        if (customer.getLoyaltyTier() != null) {
            invoice.append("Loyalty Tier: ").append(customer.getLoyaltyTier().name()).append("\n");
            if (customer.getLoyaltyTier() == Customer.LoyaltyTier.PLATINUM) {
                invoice.append("*** PLATINUM MEMBER — Priority Support ***\n");
            } else if (customer.getLoyaltyTier() == Customer.LoyaltyTier.GOLD) {
                invoice.append("*** GOLD MEMBER — Free Shipping ***\n");
            }
        }

        invoice.append("Order #").append(order.getId()).append("\n");
        invoice.append("Total: $").append(order.getTotalAmount()).append("\n");
        return invoice.toString();
    }

    // =========================================================================
    // High Complexity: calculateFinalPrice — cyclomatic complexity ~20
    // =========================================================================

    public BigDecimal calculateFinalPrice(Order order, Customer customer, String couponCode,
                                          boolean isHolidaySale, String shippingMethod) {
        BigDecimal subtotal = order.getTotalAmount();
        if (subtotal == null || subtotal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Order subtotal must be positive");
        }

        BigDecimal discount = BigDecimal.ZERO;

        // Loyalty tier discount
        if (customer.getLoyaltyTier() != null) {
            switch (customer.getLoyaltyTier()) {
                case PLATINUM:
                    discount = discount.add(subtotal.multiply(new BigDecimal("0.15")));
                    break;
                case GOLD:
                    discount = discount.add(subtotal.multiply(new BigDecimal("0.10")));
                    break;
                case SILVER:
                    discount = discount.add(subtotal.multiply(new BigDecimal("0.05")));
                    break;
                case BRONZE:
                    if (subtotal.compareTo(new BigDecimal("100")) > 0) {
                        discount = discount.add(subtotal.multiply(new BigDecimal("0.02")));
                    }
                    break;
            }
        }

        // Coupon code handling
        if (couponCode != null && !couponCode.isBlank()) {
            if (couponCode.startsWith("PCT")) {
                int pct = Integer.parseInt(couponCode.substring(3));
                if (pct > 0 && pct <= 50) {
                    BigDecimal couponDiscount = subtotal.multiply(BigDecimal.valueOf(pct))
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                    discount = discount.add(couponDiscount);
                }
            } else if (couponCode.startsWith("FLAT")) {
                BigDecimal flatAmount = new BigDecimal(couponCode.substring(4));
                if (flatAmount.compareTo(subtotal) < 0) {
                    discount = discount.add(flatAmount);
                }
            } else if ("FREESHIP".equals(couponCode)) {
                // Handled below in shipping section
            } else if (couponCode.startsWith("BOGO")) {
                List<OrderItem> items = order.getItems();
                if (items != null && items.size() >= 2) {
                    BigDecimal cheapest = items.stream()
                            .map(OrderItem::getPrice)
                            .min(BigDecimal::compareTo)
                            .orElse(BigDecimal.ZERO);
                    discount = discount.add(cheapest);
                }
            }
        }

        // Bulk discount: >10 items gets extra 5%
        if (order.getItems() != null) {
            int totalQuantity = order.getItems().stream()
                    .mapToInt(OrderItem::getQuantity)
                    .sum();
            if (totalQuantity > 10) {
                discount = discount.add(subtotal.multiply(new BigDecimal("0.05")));
            } else if (totalQuantity > 5) {
                discount = discount.add(subtotal.multiply(new BigDecimal("0.02")));
            }
        }

        // Holiday pricing
        if (isHolidaySale) {
            LocalDate today = LocalDate.now();
            if (today.getMonth() == Month.NOVEMBER && today.getDayOfMonth() >= 25) {
                // Black Friday week
                discount = discount.add(subtotal.multiply(new BigDecimal("0.10")));
            } else if (today.getMonth() == Month.DECEMBER && today.getDayOfMonth() <= 25) {
                // Holiday season
                discount = discount.add(subtotal.multiply(new BigDecimal("0.07")));
            } else {
                discount = discount.add(subtotal.multiply(new BigDecimal("0.03")));
            }
        }

        // Weekend surcharge for express shipping
        BigDecimal shippingCost = BigDecimal.ZERO;
        if ("express".equalsIgnoreCase(shippingMethod)) {
            shippingCost = new BigDecimal("19.99");
            if (LocalDate.now().getDayOfWeek() == DayOfWeek.SATURDAY
                    || LocalDate.now().getDayOfWeek() == DayOfWeek.SUNDAY) {
                shippingCost = shippingCost.add(new BigDecimal("10.00"));
            }
        } else if ("standard".equalsIgnoreCase(shippingMethod)) {
            shippingCost = subtotal.compareTo(new BigDecimal("50")) >= 0
                    ? BigDecimal.ZERO : new BigDecimal("5.99");
        } else if ("overnight".equalsIgnoreCase(shippingMethod)) {
            shippingCost = new BigDecimal("39.99");
        }

        if ("FREESHIP".equals(couponCode)) {
            shippingCost = BigDecimal.ZERO;
        }

        // Cap discount at 50% of subtotal
        BigDecimal maxDiscount = subtotal.multiply(new BigDecimal("0.50"));
        if (discount.compareTo(maxDiscount) > 0) {
            discount = maxDiscount;
        }

        BigDecimal finalPrice = subtotal.subtract(discount).add(shippingCost);
        return finalPrice.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }
}
