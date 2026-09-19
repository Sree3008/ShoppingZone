package com.eshoppingzone.inventory.audit;

import com.eshoppingzone.inventory.audit.controller.AuditLogController;
import com.eshoppingzone.inventory.audit.entity.AuditLog;
import com.eshoppingzone.inventory.audit.repository.AuditLogRepository;
import com.eshoppingzone.inventory.audit.service.AuditLogService;
import com.eshoppingzone.inventory.dto.CreateInventoryRequest;
import com.eshoppingzone.inventory.dto.InventoryDto;
import com.eshoppingzone.inventory.dto.StockReservationItem;
import com.eshoppingzone.inventory.dto.StockReservationRequest;
import com.eshoppingzone.inventory.entity.Inventory;
import com.eshoppingzone.inventory.entity.StockMovement;
import com.eshoppingzone.inventory.repository.InventoryRepository;
import com.eshoppingzone.inventory.repository.StockMovementRepository;
import com.eshoppingzone.inventory.service.InventoryServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryAuditTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private StockMovementRepository stockMovementRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private AuditLogService auditLogService;
    private InventoryServiceImpl inventoryService;
    private AuditLogController auditLogController;

    private Inventory testInventory;

    @BeforeEach
    void setUp() {
        auditLogService = new AuditLogService(auditLogRepository, new ObjectMapper(), rabbitTemplate);
        inventoryService = new InventoryServiceImpl(inventoryRepository, stockMovementRepository, auditLogService);
        auditLogController = new AuditLogController(auditLogService);

        testInventory = new Inventory();
        testInventory.setId(1L);
        testInventory.setProductId(100L);
        testInventory.setAvailableStock(50);
        testInventory.setReservedStock(10);
    }

    @Test
    @DisplayName("1. Stock creation creates audit event with action STOCK_CREATED")
    void testStockCreationAudited() {
        when(inventoryRepository.findByProductId(100L)).thenReturn(Optional.empty());
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(invocation -> {
            Inventory inv = invocation.getArgument(0);
            inv.setId(1L);
            return inv;
        });
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateInventoryRequest request = new CreateInventoryRequest(100L, 50);
        InventoryDto dto = inventoryService.createInventory(request);

        assertNotNull(dto);
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog log = captor.getValue();
        assertEquals("STOCK_CREATED", log.getAction());
        assertEquals("INVENTORY", log.getResourceType());
        assertEquals("1", log.getResourceId());
        assertEquals("SUCCESS", log.getOutcome());
        assertEquals("INVENTORY_SERVICE", log.getServiceName());
    }

    @Test
    @DisplayName("2. Stock adjustment creates audit event with action STOCK_ADJUSTED")
    void testStockAdjustmentAudited() {
        when(inventoryRepository.findByProductIdWithLock(100L)).thenReturn(Optional.of(testInventory));
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InventoryDto dto = inventoryService.addStock(100L, 20, "Restock batch");
        assertNotNull(dto);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog log = captor.getValue();
        assertEquals("STOCK_ADJUSTED", log.getAction());
        assertEquals("INVENTORY", log.getResourceType());
        assertEquals("1", log.getResourceId());
        assertEquals("SUCCESS", log.getOutcome());
    }

    @Test
    @DisplayName("3. Stock reservation creates audit event with action STOCK_RESERVED")
    void testStockReservationAudited() {
        when(inventoryRepository.findByProductIdWithLock(100L)).thenReturn(Optional.of(testInventory));
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StockReservationRequest request = new StockReservationRequest(
                "ORD-999",
                List.of(new StockReservationItem(100L, 5))
        );

        inventoryService.reserveStock(request);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog log = captor.getValue();
        assertEquals("STOCK_RESERVED", log.getAction());
        assertEquals("ORDER_STOCK", log.getResourceType());
        assertEquals("ORD-999", log.getResourceId());
        assertEquals("SUCCESS", log.getOutcome());
    }

    @Test
    @DisplayName("4. Stock restock creates audit event with action STOCK_RESTOCKED")
    void testStockRestockAudited() {
        when(stockMovementRepository.existsByReferenceAndMovementType("RET-123", com.eshoppingzone.inventory.entity.MovementType.ADD_STOCK)).thenReturn(false);
        when(inventoryRepository.findByProductIdWithLock(100L)).thenReturn(Optional.of(testInventory));
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StockReservationRequest request = new StockReservationRequest(
                "RET-123",
                List.of(new StockReservationItem(100L, 2))
        );

        inventoryService.restock(request);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog log = captor.getValue();
        assertEquals("STOCK_RESTOCKED", log.getAction());
        assertEquals("RETURN_STOCK", log.getResourceType());
        assertEquals("RET-123", log.getResourceId());
        assertEquals("SUCCESS", log.getOutcome());
    }

    @Test
    @DisplayName("5. Audit log entity immutability: updates and deletes are rejected")
    void testImmutability() {
        AuditLog auditLog = new AuditLog();
        assertThrows(UnsupportedOperationException.class, auditLog::preUpdate);
        assertThrows(UnsupportedOperationException.class, auditLog::preRemove);
    }

    @Test
    @DisplayName("6. Admin can query inventory audit logs with pagination")
    void testAdminQueryAuditLogs() {
        AuditLog auditLog = new AuditLog();
        auditLog.setEventId("evt-inv-1");
        auditLog.setAction("STOCK_RESERVED");
        auditLog.setOutcome("SUCCESS");
        auditLog.setTimestamp(LocalDateTime.now());
        auditLog.setServiceName("INVENTORY_SERVICE");

        Page<AuditLog> paged = new PageImpl<>(List.of(auditLog));
        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(paged);

        ResponseEntity<?> response = auditLogController.searchAuditLogs(
                null, "STOCK_RESERVED", "ORDER_STOCK", "ORD-999", "INVENTORY_SERVICE", "SUCCESS", null, null, null, 0, 20, "timestamp", "desc"
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }
}
