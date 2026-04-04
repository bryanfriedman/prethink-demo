package com.example.ecommerce.controller;

import com.example.ecommerce.entity.Order;
import com.example.ecommerce.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @Test
    @WithMockUser
    void getOrder_returnsOrder() throws Exception {
        Order order = new Order();
        order.setId(1L);
        order.setCustomerId(42L);
        order.setStatus(Order.OrderStatus.PENDING);
        order.setTotalAmount(new BigDecimal("99.99"));
        order.setCreatedAt(LocalDateTime.of(2025, 1, 15, 10, 30));

        when(orderService.getOrder(1L)).thenReturn(order);

        mockMvc.perform(get("/api/orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @WithMockUser
    void listOrders_returnsAllOrders() throws Exception {
        Order order1 = new Order();
        order1.setId(1L);
        order1.setStatus(Order.OrderStatus.PENDING);
        order1.setTotalAmount(BigDecimal.TEN);
        order1.setCreatedAt(LocalDateTime.now());

        Order order2 = new Order();
        order2.setId(2L);
        order2.setStatus(Order.OrderStatus.SHIPPED);
        order2.setTotalAmount(new BigDecimal("250.00"));
        order2.setCreatedAt(LocalDateTime.now());

        when(orderService.getAllOrders()).thenReturn(List.of(order1, order2));

        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @WithMockUser
    void getOrderStatus_returnsStatus() throws Exception {
        when(orderService.getOrderStatus(1L)).thenReturn(Order.OrderStatus.CONFIRMED);

        mockMvc.perform(get("/api/orders/1/status"))
                .andExpect(status().isOk());
    }
}
