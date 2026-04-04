package com.example.ecommerce.service;

import com.example.ecommerce.annotations.Auditable;
import com.example.ecommerce.dto.InventoryAdjustmentRequest;
import com.example.ecommerce.entity.InventoryRecord;
import com.example.ecommerce.repository.InventoryRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private static final int LOW_STOCK_THRESHOLD = 10;
    private static final int CRITICAL_STOCK_THRESHOLD = 3;

    private final InventoryRecordRepository inventoryRecordRepository;

    public InventoryService(InventoryRecordRepository inventoryRecordRepository) {
        this.inventoryRecordRepository = inventoryRecordRepository;
    }

    public InventoryRecord getInventoryBySku(String sku) {
        return inventoryRecordRepository.findBySku(sku)
                .orElseThrow(() -> new RuntimeException("Inventory record not found for SKU: " + sku));
    }

    @Auditable(action = "INVENTORY_ADJUST")
    @Transactional
    public InventoryRecord adjustInventory(String sku, InventoryAdjustmentRequest request) {
        InventoryRecord record = getInventoryBySku(sku);
        int newQuantity = record.getQuantity() + request.quantityChange();
        if (newQuantity < 0) {
            throw new IllegalStateException("Insufficient inventory for SKU: " + sku);
        }
        record.setQuantity(newQuantity);
        record.setLastUpdated(LocalDateTime.now());
        return inventoryRecordRepository.save(record);
    }

    @Transactional
    public Map<String, Object> reconcileInventory(List<Map<String, Object>> warehouseSnapshots,
                                                   Map<String, Integer> reservedStock,
                                                   Set<String> damagedSkus) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<String> discrepancies = new ArrayList<>();
        List<String> backorders = new ArrayList<>();
        int reconciled = 0;
        int skipped = 0;

        for (Map<String, Object> snapshot : warehouseSnapshots) {
            String sku = (String) snapshot.get("sku");
            String warehouseId = (String) snapshot.get("warehouseId");
            Integer reportedQty = (Integer) snapshot.get("quantity");

            if (sku == null || warehouseId == null) {
                log.warn("Skipping snapshot with missing sku or warehouseId");
                skipped++;
                continue;
            }

            // Skip damaged goods — they go through a separate write-off process
            if (damagedSkus != null && damagedSkus.contains(sku)) {
                log.info("Skipping damaged SKU {} in warehouse {}", sku, warehouseId);
                skipped++;
                continue;
            }

            Optional<InventoryRecord> existing = inventoryRecordRepository.findBySku(sku);
            if (existing.isEmpty()) {
                log.warn("No inventory record for SKU {}, creating new", sku);
                InventoryRecord newRecord = new InventoryRecord();
                newRecord.setSku(sku);
                newRecord.setWarehouseId(warehouseId);
                newRecord.setQuantity(reportedQty != null ? reportedQty : 0);
                newRecord.setLastUpdated(LocalDateTime.now());
                inventoryRecordRepository.save(newRecord);
                reconciled++;
                continue;
            }

            InventoryRecord record = existing.get();
            int systemQty = record.getQuantity();

            // Account for reserved stock
            int reserved = 0;
            if (reservedStock != null && reservedStock.containsKey(sku)) {
                reserved = reservedStock.get(sku);
            }
            int availableSystemQty = systemQty - reserved;

            if (reportedQty != null && reportedQty != availableSystemQty) {
                int diff = reportedQty - availableSystemQty;
                discrepancies.add(String.format("SKU %s: system=%d (reserved=%d), warehouse=%d, diff=%d",
                        sku, systemQty, reserved, reportedQty, diff));

                if (Math.abs(diff) > systemQty * 0.1 && Math.abs(diff) > 5) {
                    log.error("Large discrepancy for SKU {} in warehouse {}: diff={}",
                            sku, warehouseId, diff);
                } else {
                    record.setQuantity(reportedQty + reserved);
                    record.setLastUpdated(LocalDateTime.now());
                    inventoryRecordRepository.save(record);
                    reconciled++;
                }
            } else {
                reconciled++;
            }

            // Check for backorder conditions
            int effectiveQty = reportedQty != null ? reportedQty : availableSystemQty;
            if (effectiveQty <= 0 && reserved > 0) {
                backorders.add(sku);
                log.warn("Backorder condition: SKU {} has {} reserved but {} available",
                        sku, reserved, effectiveQty);
            } else if (effectiveQty < CRITICAL_STOCK_THRESHOLD) {
                log.warn("Critical stock level for SKU {}: {}", sku, effectiveQty);
            } else if (effectiveQty < LOW_STOCK_THRESHOLD) {
                log.info("Low stock for SKU {}: {}", sku, effectiveQty);
            }
        }

        result.put("reconciled", reconciled);
        result.put("skipped", skipped);
        result.put("discrepancies", discrepancies);
        result.put("backorders", backorders);
        result.put("reconciledAt", LocalDateTime.now());
        return result;
    }
}
