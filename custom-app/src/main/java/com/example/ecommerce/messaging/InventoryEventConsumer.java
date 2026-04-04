package com.example.ecommerce.messaging;

import com.example.ecommerce.service.InventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class InventoryEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventConsumer.class);

    private final InventoryService inventoryService;

    public InventoryEventConsumer(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @KafkaListener(topics = "inventory-updates", groupId = "ecommerce-service")
    public void handleInventoryUpdate(Map<String, Object> event) {
        String eventType = (String) event.get("eventType");
        String sku = (String) event.get("sku");

        log.info("Received inventory event: type={}, sku={}", eventType, sku);

        // Process inventory update events from warehouse systems
        // In a real implementation, this would reconcile inventory counts
    }
}
