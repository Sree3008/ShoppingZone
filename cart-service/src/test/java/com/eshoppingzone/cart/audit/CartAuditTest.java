package com.eshoppingzone.cart.audit;

import com.eshoppingzone.cart.audit.dto.AuditLogDto;
import com.eshoppingzone.cart.audit.entity.AuditLog;
import com.eshoppingzone.cart.audit.repository.AuditLogRepository;
import com.eshoppingzone.cart.audit.service.AuditLogService;
import com.eshoppingzone.cart.client.ProductClient;
import com.eshoppingzone.cart.dto.AddToCartRequest;
import com.eshoppingzone.cart.dto.ApiResponse;
import com.eshoppingzone.cart.dto.CartDto;
import com.eshoppingzone.cart.dto.ProductSnapshotDto;
import com.eshoppingzone.cart.entity.Cart;
import com.eshoppingzone.cart.entity.CartItem;
import com.eshoppingzone.cart.repository.CartItemRepository;
import com.eshoppingzone.cart.repository.CartRepository;
import com.eshoppingzone.cart.service.CartServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartAuditTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductClient productClient;

    private ObjectMapper objectMapper;
    private AuditLogService auditLogService;
    private CartServiceImpl cartService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        auditLogService = new AuditLogService(auditLogRepository, objectMapper);
        cartService = new CartServiceImpl(cartRepository, cartItemRepository, productClient, null, auditLogService);
    }

    @Test
    @DisplayName("AuditLog record immutability - updates and deletes are rejected")
    void testAuditLogImmutability() {
        AuditLog auditLog = new AuditLog();
        assertThrows(UnsupportedOperationException.class, auditLog::preUpdate);
        assertThrows(UnsupportedOperationException.class, auditLog::preRemove);
    }

    @Test
    @DisplayName("Adding to cart creates CART_ITEM_ADDED audit event with correct details")
    void testAddToCartAudit() {
        Long customerId = 100L;
        AddToCartRequest request = new AddToCartRequest();
        request.setProductId(50L);
        request.setQuantity(2);

        Cart cart = new Cart();
        cart.setId(1L);
        cart.setCustomerId(customerId);
        cart.setItems(new ArrayList<>());

        when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(cart));

        ProductSnapshotDto product = new ProductSnapshotDto();
        product.setId(50L);
        product.setName("Wireless Mouse");
        product.setPrice(new BigDecimal("29.99"));
        product.setStatus("ACTIVE");

        when(productClient.getProductById(50L)).thenReturn(ApiResponse.success(product));
        when(cartItemRepository.findByCartAndProductId(any(), eq(50L))).thenReturn(Optional.empty());
        when(cartItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(cartRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        CartDto result = cartService.addToCart(customerId, request, "cart-idem-1");
        assertNotNull(result);

        verify(auditLogRepository, times(1)).save(argThat(log ->
                "CART_ITEM_ADDED".equals(log.getAction()) &&
                "CART_ITEM".equals(log.getResourceType()) &&
                "50".equals(log.getResourceId()) &&
                "SUCCESS".equals(log.getOutcome()) &&
                "cart-idem-1".equals(log.getIdempotencyKey())
        ));
    }

    @Test
    @DisplayName("Updating cart item quantity creates CART_ITEM_UPDATED audit event")
    void testUpdateCartItemQuantityAudit() {
        Long customerId = 100L;
        Cart cart = new Cart();
        cart.setId(1L);
        cart.setCustomerId(customerId);

        CartItem item = new CartItem();
        item.setId(10L);
        item.setCart(cart);
        item.setProductId(50L);
        item.setQuantity(2);

        when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByIdAndCart(10L, cart)).thenReturn(Optional.of(item));
        when(cartItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(cartRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        cartService.updateCartItemQuantity(customerId, 10L, 5);

        verify(auditLogRepository, times(1)).save(argThat(log ->
                "CART_ITEM_UPDATED".equals(log.getAction()) &&
                "10".equals(log.getResourceId()) &&
                "SUCCESS".equals(log.getOutcome())
        ));
    }

    @Test
    @DisplayName("Removing cart item creates CART_ITEM_REMOVED audit event")
    void testRemoveCartItemAudit() {
        Long customerId = 100L;
        Cart cart = new Cart();
        cart.setId(1L);
        cart.setCustomerId(customerId);
        cart.setItems(new ArrayList<>());

        CartItem item = new CartItem();
        item.setId(10L);
        item.setCart(cart);

        when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByIdAndCart(10L, cart)).thenReturn(Optional.of(item));
        when(cartRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        cartService.removeCartItem(customerId, 10L);

        verify(auditLogRepository, times(1)).save(argThat(log ->
                "CART_ITEM_REMOVED".equals(log.getAction()) &&
                "10".equals(log.getResourceId()) &&
                "SUCCESS".equals(log.getOutcome())
        ));
    }

    @Test
    @DisplayName("Clearing cart creates CART_CLEARED audit event")
    void testClearCartAudit() {
        Long customerId = 100L;
        Cart cart = new Cart();
        cart.setId(1L);
        cart.setCustomerId(customerId);
        cart.setItems(new ArrayList<>());

        when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        cartService.clearCart(customerId);

        verify(auditLogRepository, times(1)).save(argThat(log ->
                "CART_CLEARED".equals(log.getAction()) &&
                "1".equals(log.getResourceId()) &&
                "SUCCESS".equals(log.getOutcome())
        ));
    }

    @Test
    @DisplayName("Searching audit logs as ADMIN returns paged results")
    void testSearchAuditLogs() {
        AuditLog sample = new AuditLog();
        sample.setId(1L);
        sample.setEventId(UUID.randomUUID().toString());
        sample.setServiceName("cart-service");
        sample.setAction("CART_ITEM_ADDED");
        sample.setResourceType("CART_ITEM");
        sample.setOutcome("SUCCESS");

        when(auditLogRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(Collections.singletonList(sample)));

        Page<AuditLogDto> results = auditLogService.searchAuditLogs(
                null, "CART_ITEM_ADDED", null, null, null, null, null, null, null, PageRequest.of(0, 20));

        assertNotNull(results);
        assertEquals(1, results.getTotalElements());
        assertEquals("CART_ITEM_ADDED", results.getContent().get(0).getAction());
    }
}
