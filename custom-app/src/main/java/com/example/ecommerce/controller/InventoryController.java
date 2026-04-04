package com.example.ecommerce.controller;

import com.example.ecommerce.dto.InventoryAdjustmentRequest;
import com.example.ecommerce.entity.InventoryRecord;
import com.example.ecommerce.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/{sku}")
    public ResponseEntity<InventoryRecord> getInventory(@PathVariable String sku) {
        return ResponseEntity.ok(inventoryService.getInventoryBySku(sku));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{sku}/adjust")
    public ResponseEntity<InventoryRecord> adjustInventory(@PathVariable String sku,
                                                           @Valid @RequestBody InventoryAdjustmentRequest request) {
        return ResponseEntity.ok(inventoryService.adjustInventory(sku, request));
    }
}
