package com.eshoppingzone.product.audit;

import com.eshoppingzone.product.audit.controller.AuditLogController;
import com.eshoppingzone.product.audit.entity.AuditLog;
import com.eshoppingzone.product.audit.repository.AuditLogRepository;
import com.eshoppingzone.product.audit.service.AuditLogService;
import com.eshoppingzone.product.dto.CreateProductRequest;
import com.eshoppingzone.product.dto.ProductDto;
import com.eshoppingzone.product.dto.UpdateProductRequest;
import com.eshoppingzone.product.entity.Product;
import com.eshoppingzone.product.entity.ProductStatus;
import com.eshoppingzone.product.repository.CategoryRepository;
import com.eshoppingzone.product.repository.ProductRepository;
import com.eshoppingzone.product.service.ProductServiceImpl;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductAuditTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private AuditLogService auditLogService;
    private ProductServiceImpl productService;
    private AuditLogController auditLogController;

    private Product testProduct;

    @BeforeEach
    void setUp() {
        auditLogService = new AuditLogService(auditLogRepository, new ObjectMapper(), rabbitTemplate);
        productService = new ProductServiceImpl(productRepository, categoryRepository, auditLogService);
        auditLogController = new AuditLogController(auditLogService);

        testProduct = new Product();
        testProduct.setId(10L);
        testProduct.setMerchantId(50L);
        testProduct.setName("Wireless Earbuds");
        testProduct.setDescription("Noise cancelling earbuds");
        testProduct.setCategory("Electronics");
        testProduct.setPrice(new BigDecimal("99.99"));
        testProduct.setStatus(ProductStatus.PENDING_APPROVAL);
    }

    @Test
    @DisplayName("1. Product creation creates audit event with PRODUCT_CREATED")
    void testProductCreationAudited() {
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            p.setId(10L);
            return p;
        });
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateProductRequest request = new CreateProductRequest();
        request.setName("Wireless Earbuds");
        request.setDescription("Noise cancelling earbuds");
        request.setCategory("Electronics");
        request.setPrice(new BigDecimal("99.99"));
        request.setImageUrl("http://image.url");

        ProductDto dto = productService.createProduct(50L, request);
        assertNotNull(dto);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog log = captor.getValue();
        assertEquals("PRODUCT_CREATED", log.getAction());
        assertEquals("PRODUCT", log.getResourceType());
        assertEquals("10", log.getResourceId());
        assertEquals(50L, log.getActorUserId());
        assertEquals("SUCCESS", log.getOutcome());
        assertEquals("PRODUCT_SERVICE", log.getServiceName());
    }

    @Test
    @DisplayName("2. Product update creates audit event with PRODUCT_UPDATED")
    void testProductUpdateAudited() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(testProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateProductRequest request = new UpdateProductRequest();
        request.setName("Wireless Earbuds Pro");
        request.setDescription("Improved ANC earbuds");
        request.setCategory("Electronics");
        request.setPrice(new BigDecimal("129.99"));
        request.setImageUrl("http://image.url/pro");

        ProductDto dto = productService.updateProduct(10L, 50L, request);
        assertNotNull(dto);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog log = captor.getValue();
        assertEquals("PRODUCT_UPDATED", log.getAction());
        assertEquals("PRODUCT", log.getResourceType());
        assertEquals("10", log.getResourceId());
        assertEquals(50L, log.getActorUserId());
        assertEquals("SUCCESS", log.getOutcome());
    }

    @Test
    @DisplayName("3. Admin product approval creates audit event with PRODUCT_APPROVED")
    void testProductApprovalAudited() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(testProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductDto dto = productService.approveProduct(10L);
        assertNotNull(dto);
        assertEquals(ProductStatus.ACTIVE, dto.getStatus());

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog log = captor.getValue();
        assertEquals("PRODUCT_APPROVED", log.getAction());
        assertEquals("PRODUCT", log.getResourceType());
        assertEquals("10", log.getResourceId());
        assertEquals("SUCCESS", log.getOutcome());
    }

    @Test
    @DisplayName("4. Admin product deactivation creates audit event with PRODUCT_DEACTIVATED")
    void testProductDeactivationAudited() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(testProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductDto dto = productService.deactivateProductByAdmin(10L);
        assertNotNull(dto);
        assertEquals(ProductStatus.DEACTIVATED, dto.getStatus());

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog log = captor.getValue();
        assertEquals("PRODUCT_DEACTIVATED", log.getAction());
        assertEquals("PRODUCT", log.getResourceType());
        assertEquals("10", log.getResourceId());
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
    @DisplayName("6. Admin can query product audit logs with pagination")
    void testAdminQueryAuditLogs() {
        AuditLog auditLog = new AuditLog();
        auditLog.setEventId("evt-product-1");
        auditLog.setAction("PRODUCT_CREATED");
        auditLog.setOutcome("SUCCESS");
        auditLog.setTimestamp(LocalDateTime.now());
        auditLog.setServiceName("PRODUCT_SERVICE");

        Page<AuditLog> paged = new PageImpl<>(List.of(auditLog));
        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(paged);

        ResponseEntity<?> response = auditLogController.searchAuditLogs(
                50L, "PRODUCT_CREATED", "PRODUCT", "10", "PRODUCT_SERVICE", "SUCCESS", null, null, null, 0, 20, "timestamp", "desc"
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }
}
