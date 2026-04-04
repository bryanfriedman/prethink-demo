package com.example.ecommerce.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Component
public class ReportingDao {

    private final JdbcTemplate jdbcTemplate;

    public ReportingDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> querySalesByDateRange(LocalDate start, LocalDate end) {
        return jdbcTemplate.queryForList(
                "SELECT DATE(created_at) as sale_date, SUM(total_amount) as total "
                        + "FROM orders WHERE created_at BETWEEN ? AND ? GROUP BY DATE(created_at)",
                start, end);
    }

    public List<Map<String, Object>> queryRevenueByCategory() {
        return jdbcTemplate.queryForList(
                "SELECT p.category, SUM(oi.price * oi.quantity) as revenue "
                        + "FROM order_items oi JOIN products p ON oi.product_id = p.id "
                        + "GROUP BY p.category ORDER BY revenue DESC");
    }

    public List<Map<String, Object>> queryTopProducts(int limit) {
        return jdbcTemplate.queryForList(
                "SELECT p.name, p.sku, SUM(oi.quantity) as total_sold "
                        + "FROM order_items oi JOIN products p ON oi.product_id = p.id "
                        + "GROUP BY p.name, p.sku ORDER BY total_sold DESC LIMIT ?",
                limit);
    }

    public BigDecimal queryTotalRevenue(LocalDate start, LocalDate end) {
        return jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(total_amount), 0) FROM orders WHERE created_at BETWEEN ? AND ?",
                BigDecimal.class, start, end);
    }
}
