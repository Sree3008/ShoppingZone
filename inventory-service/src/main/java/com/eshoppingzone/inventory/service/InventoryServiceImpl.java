package com.eshoppingzone.inventory.service;

import com.eshoppingzone.inventory.dto.*;
import com.eshoppingzone.inventory.entity.Inventory;
import com.eshoppingzone.inventory.entity.MovementType;
import com.eshoppingzone.inventory.entity.StockMovement;
import com.eshoppingzone.inventory.exception.InsufficientStockException;
import com.eshoppingzone.inventory.exception.ResourceNotFoundException;
import com.eshoppingzone.inventory.repository.InventoryRepository;
import com.eshoppingzone.inventory.repository.StockMovementRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class InventoryServiceImpl implements InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryServiceImpl.class);

    private final InventoryRepository inventoryRepository;
    private final StockMovementRepository stockMovementRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.eshoppingzone.inventory.audit.service.AuditLogService auditLogService;

    public InventoryServiceImpl(InventoryRepository inventoryRepository, StockMovementRepository stockMovementRepository) {
        this.inventoryRepository = inventoryRepository;
        this.stockMovementRepository = stockMovementRepository;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public InventoryServiceImpl(InventoryRepository inventoryRepository,
                                StockMovementRepository stockMovementRepository,
                                @org.springframework.beans.factory.annotation.Autowired(required = false) com.eshoppingzone.inventory.audit.service.AuditLogService auditLogService) {
        this.inventoryRepository = inventoryRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.auditLogService = auditLogService;
    }

    public void setAuditLogService(com.eshoppingzone.inventory.audit.service.AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    private void auditLog(String action, String resourceType, String resourceId, String outcome,
                          String failureReason, java.util.Map<String, Object> metadata) {
        if (auditLogService != null) {
            try {
                auditLogService.log(action, resourceType, resourceId, outcome, failureReason, metadata,
                        null, null, null, null);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    @Transactional
    public InventoryDto createInventory(CreateInventoryRequest request) {
        Inventory inventory = inventoryRepository.findByProductId(request.getProductId())
                .orElseGet(() -> {
                    Inventory inv = new Inventory();
                    inv.setProductId(request.getProductId());
                    inv.setAvailableStock(0);
                    inv.setReservedStock(0);
                    return inv;
                });

        int additional = request.getInitialStock() != null ? request.getInitialStock() : 0;
        inventory.setAvailableStock(inventory.getAvailableStock() + additional);
        Inventory saved = inventoryRepository.save(inventory);

        if (additional > 0) {
            recordMovement(saved.getId(), saved.getProductId(), MovementType.INITIAL, additional, "INITIAL", "Initial stock setup");
        }

        auditLog("STOCK_CREATED", "INVENTORY", String.valueOf(saved.getId()), "SUCCESS", null,
                java.util.Map.of("productId", saved.getProductId(), "initialStock", additional));

        return InventoryDto.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryDto getInventoryByProductId(Long productId) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found for product ID: " + productId));
        return InventoryDto.fromEntity(inventory);
    }

    @Override
    @Transactional
    public InventoryDto addStock(Long productId, Integer quantity, String reason) {
        Inventory inventory = inventoryRepository.findByProductIdWithLock(productId)
                .orElseGet(() -> {
                    Inventory inv = new Inventory();
                    inv.setProductId(productId);
                    inv.setAvailableStock(0);
                    inv.setReservedStock(0);
                    return inventoryRepository.save(inv);
                });

        inventory.setAvailableStock(inventory.getAvailableStock() + quantity);
        Inventory saved = inventoryRepository.save(inventory);

        recordMovement(saved.getId(), productId, MovementType.ADD_STOCK, quantity, "MANUAL_ADD", reason != null ? reason : "Stock added by merchant");
        auditLog("STOCK_ADJUSTED", "INVENTORY", String.valueOf(saved.getId()), "SUCCESS", null,
                java.util.Map.of("productId", productId, "quantity", quantity, "type", "ADD_STOCK"));
        return InventoryDto.fromEntity(saved);
    }

    @Override
    @Transactional
    public InventoryDto reduceStock(Long productId, Integer quantity, String reason) {
        Inventory inventory = inventoryRepository.findByProductIdWithLock(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found for product ID: " + productId));

        if (inventory.getAvailableStock() < quantity) {
            auditLog("STOCK_ADJUST_FAILED", "INVENTORY", String.valueOf(inventory.getId()), "FAILURE", "Insufficient stock",
                    java.util.Map.of("productId", productId, "requested", quantity, "available", inventory.getAvailableStock()));
            throw new InsufficientStockException("Cannot reduce stock. Available: " + inventory.getAvailableStock() + ", requested: " + quantity);
        }

        inventory.setAvailableStock(inventory.getAvailableStock() - quantity);
        Inventory saved = inventoryRepository.save(inventory);

        recordMovement(saved.getId(), productId, MovementType.REDUCE_STOCK, quantity, "MANUAL_REDUCE", reason != null ? reason : "Stock reduced by merchant");
        auditLog("STOCK_ADJUSTED", "INVENTORY", String.valueOf(saved.getId()), "SUCCESS", null,
                java.util.Map.of("productId", productId, "quantity", quantity, "type", "REDUCE_STOCK"));
        return InventoryDto.fromEntity(saved);
    }

    @Override
    @Transactional
    public void reserveStock(StockReservationRequest request) {
        log.info("Attempting to reserve stock for order: {}", request.getOrderReference());

        if (request.getItems() == null || request.getItems().isEmpty()) {
            return;
        }

        // Deterministic lock acquisition order by productId to prevent deadlocks
        List<StockReservationItem> sortedItems = request.getItems().stream()
                .sorted((a, b) -> Long.compare(a.getProductId(), b.getProductId()))
                .collect(Collectors.toList());

        // 1. Lock and verify availability for all items in sorted order
        java.util.Map<Long, Inventory> lockedInventories = new java.util.HashMap<>();
        for (StockReservationItem item : sortedItems) {
            Inventory inventory = inventoryRepository.findByProductIdWithLock(item.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Inventory not found for product ID: " + item.getProductId()));

            if (inventory.getAvailableStock() < item.getQuantity()) {
                auditLog("STOCK_RESERVATION_FAILED", "ORDER_STOCK", request.getOrderReference(), "FAILURE",
                        "Insufficient stock for product ID: " + item.getProductId(),
                        java.util.Map.of("orderReference", request.getOrderReference(), "productId", item.getProductId()));
                throw new InsufficientStockException("Insufficient stock for product ID: " + item.getProductId()
                        + ". Available: " + inventory.getAvailableStock() + ", Requested: " + item.getQuantity());
            }
            lockedInventories.put(item.getProductId(), inventory);
        }

        // 2. Perform reservations atomically under locks
        for (StockReservationItem item : sortedItems) {
            Inventory inventory = lockedInventories.get(item.getProductId());
            inventory.setAvailableStock(inventory.getAvailableStock() - item.getQuantity());
            inventory.setReservedStock(inventory.getReservedStock() + item.getQuantity());
            inventoryRepository.save(inventory);

            recordMovement(inventory.getId(), item.getProductId(), MovementType.RESERVE, item.getQuantity(),
                    request.getOrderReference(), "Stock reserved for order");
        }
        log.info("Stock reserved successfully for order: {}", request.getOrderReference());
        auditLog("STOCK_RESERVED", "ORDER_STOCK", request.getOrderReference(), "SUCCESS", null,
                java.util.Map.of("orderReference", request.getOrderReference(), "itemCount", sortedItems.size()));
    }

    @Override
    @Transactional
    public void releaseStock(StockReservationRequest request) {
        log.info("Releasing reserved stock for order: {}", request.getOrderReference());
        if (request.getItems() == null || request.getItems().isEmpty()) {
            return;
        }

        List<StockReservationItem> sortedItems = request.getItems().stream()
                .sorted((a, b) -> Long.compare(a.getProductId(), b.getProductId()))
                .collect(Collectors.toList());

        for (StockReservationItem item : sortedItems) {
            inventoryRepository.findByProductIdWithLock(item.getProductId()).ifPresent(inventory -> {
                int releaseQty = Math.min(inventory.getReservedStock(), item.getQuantity());
                inventory.setReservedStock(inventory.getReservedStock() - releaseQty);
                inventory.setAvailableStock(inventory.getAvailableStock() + releaseQty);
                inventoryRepository.save(inventory);

                recordMovement(inventory.getId(), item.getProductId(), MovementType.RELEASE, releaseQty,
                        request.getOrderReference(), "Reserved stock released back to available");
            });
        }
        auditLog("STOCK_RELEASED", "ORDER_STOCK", request.getOrderReference(), "SUCCESS", null,
                java.util.Map.of("orderReference", request.getOrderReference()));
    }

    @Override
    @Transactional
    public void confirmStock(StockReservationRequest request) {
        log.info("Confirming stock consumption for order: {}", request.getOrderReference());
        if (request.getItems() == null || request.getItems().isEmpty()) {
            return;
        }

        List<StockReservationItem> sortedItems = request.getItems().stream()
                .sorted((a, b) -> Long.compare(a.getProductId(), b.getProductId()))
                .collect(Collectors.toList());

        for (StockReservationItem item : sortedItems) {
            inventoryRepository.findByProductIdWithLock(item.getProductId()).ifPresent(inventory -> {
                int confirmQty = Math.min(inventory.getReservedStock(), item.getQuantity());
                inventory.setReservedStock(inventory.getReservedStock() - confirmQty);
                inventoryRepository.save(inventory);

                recordMovement(inventory.getId(), item.getProductId(), MovementType.CONFIRM_CONSUMPTION, confirmQty,
                        request.getOrderReference(), "Stock consumption confirmed after successful payment");
            });
        }
        auditLog("STOCK_CONFIRMED", "ORDER_STOCK", request.getOrderReference(), "SUCCESS", null,
                java.util.Map.of("orderReference", request.getOrderReference()));
    }

    @Override
    @Transactional
    public void restock(StockReservationRequest request) {
        log.info("Restocking inventory for return: {}", request.getOrderReference());

        // Idempotency / duplicate check: prevent restocking the same return reference multiple times
        if (request.getOrderReference() != null && stockMovementRepository.existsByReferenceAndMovementType(request.getOrderReference(), MovementType.ADD_STOCK)) {
            log.warn("Restock request for reference {} has already been processed. Skipping duplicate restock.", request.getOrderReference());
            return;
        }

        if (request.getItems() == null || request.getItems().isEmpty()) {
            return;
        }

        List<StockReservationItem> sortedItems = request.getItems().stream()
                .sorted((a, b) -> Long.compare(a.getProductId(), b.getProductId()))
                .collect(Collectors.toList());

        for (StockReservationItem item : sortedItems) {
            Inventory inventory = inventoryRepository.findByProductIdWithLock(item.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Inventory not found for product ID: " + item.getProductId()));
            inventory.setAvailableStock(inventory.getAvailableStock() + item.getQuantity());
            inventoryRepository.save(inventory);

            recordMovement(inventory.getId(), item.getProductId(), MovementType.ADD_STOCK, item.getQuantity(),
                    request.getOrderReference(), "Stock restocked for return: " + request.getOrderReference());
        }
        log.info("Stock restocked successfully for return: {}", request.getOrderReference());
        auditLog("STOCK_RESTOCKED", "RETURN_STOCK", request.getOrderReference(), "SUCCESS", null,
                java.util.Map.of("returnReference", request.getOrderReference()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockMovementDto> getMovements(Long productId) {
        return stockMovementRepository.findByProductIdOrderByCreatedAtDesc(productId).stream()
                .map(StockMovementDto::fromEntity)
                .collect(Collectors.toList());
    }

    private void recordMovement(Long inventoryId, Long productId, MovementType type, Integer quantity, String reference, String description) {
        StockMovement movement = new StockMovement();
        movement.setInventoryId(inventoryId);
        movement.setProductId(productId);
        movement.setMovementType(type);
        movement.setQuantity(quantity);
        movement.setReference(reference);
        movement.setDescription(description);
        stockMovementRepository.save(movement);
    }
}
