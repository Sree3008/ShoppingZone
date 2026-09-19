package com.eshoppingzone.cart.service;

import com.eshoppingzone.cart.client.ProductClient;
import com.eshoppingzone.cart.dto.AddToCartRequest;
import com.eshoppingzone.cart.dto.ApiResponse;
import com.eshoppingzone.cart.dto.CartDto;
import com.eshoppingzone.cart.dto.ProductSnapshotDto;
import com.eshoppingzone.cart.entity.Cart;
import com.eshoppingzone.cart.entity.CartItem;
import com.eshoppingzone.cart.exception.ResourceNotFoundException;
import com.eshoppingzone.cart.repository.CartItemRepository;
import com.eshoppingzone.cart.repository.CartRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class CartServiceImpl implements CartService {

    private static final Logger log = LoggerFactory.getLogger(CartServiceImpl.class);

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductClient productClient;
    private final CartIdempotencyService idempotencyService;

    private com.eshoppingzone.cart.audit.service.AuditLogService auditLogService;

    @org.springframework.beans.factory.annotation.Autowired
    public CartServiceImpl(CartRepository cartRepository,
                           CartItemRepository cartItemRepository,
                           ProductClient productClient,
                           @org.springframework.beans.factory.annotation.Autowired(required = false) CartIdempotencyService idempotencyService,
                           @org.springframework.beans.factory.annotation.Autowired(required = false) com.eshoppingzone.cart.audit.service.AuditLogService auditLogService) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productClient = productClient;
        this.idempotencyService = idempotencyService;
        this.auditLogService = auditLogService;
    }

    public CartServiceImpl(CartRepository cartRepository,
                           CartItemRepository cartItemRepository,
                           ProductClient productClient,
                           CartIdempotencyService idempotencyService) {
        this(cartRepository, cartItemRepository, productClient, idempotencyService, null);
    }

    public CartServiceImpl(CartRepository cartRepository,
                           CartItemRepository cartItemRepository,
                           ProductClient productClient) {
        this(cartRepository, cartItemRepository, productClient, null, null);
    }

    public void setAuditLogService(com.eshoppingzone.cart.audit.service.AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    private void auditLog(String action, String resourceType, String resourceId, String outcome,
                          String failureReason, java.util.Map<String, Object> metadata, String idempotencyKey) {
        if (auditLogService != null) {
            try {
                auditLogService.log(action, resourceType, resourceId, outcome, failureReason, metadata,
                        null, null, null, idempotencyKey);
            } catch (Exception ignored) {
            }
        }
    }

    private Cart getOrCreateCart(Long customerId) {
        return cartRepository.findByCustomerId(customerId)
                .orElseGet(() -> {
                    Cart cart = new Cart();
                    cart.setCustomerId(customerId);
                    return cartRepository.save(cart);
                });
    }

    @Override
    @Transactional
    public CartDto getMyCart(Long customerId) {
        Cart cart = getOrCreateCart(customerId);
        return CartDto.fromEntity(cart);
    }

    @Override
    @Transactional
    public CartDto addToCart(Long customerId, AddToCartRequest request) {
        return addToCart(customerId, request, null);
    }

    @Override
    @Transactional
    public CartDto addToCart(Long customerId, AddToCartRequest request, String idempotencyKey) {
        if (idempotencyService != null && idempotencyKey != null && !idempotencyKey.isBlank()) {
            String fingerprint = idempotencyService.computeAddToCartFingerprint(customerId, request);
            Optional<com.eshoppingzone.cart.entity.CartIdempotencyRecord> recordOpt =
                    idempotencyService.checkOrStartProcessing(idempotencyKey, "ADD_TO_CART", customerId, fingerprint);
            if (recordOpt.isPresent() && recordOpt.get().getStatus() == com.eshoppingzone.cart.entity.IdempotencyStatus.COMPLETED) {
                auditLog("IDEMPOTENCY_REPLAY", "CART", String.valueOf(customerId), "SUCCESS", null,
                        java.util.Map.of("action", "ADD_TO_CART", "productId", request.getProductId()), idempotencyKey);
                Optional<CartDto> cached = idempotencyService.parseResponseBody(recordOpt.get(), CartDto.class);
                if (cached.isPresent()) {
                    log.info("Returning cached idempotent cart response for key: {}", idempotencyKey);
                    return cached.get();
                }
                return getMyCart(customerId);
            }
        }

        try {
            CartDto result = executeAddToCart(customerId, request);
            auditLog("CART_ITEM_ADDED", "CART_ITEM", String.valueOf(request.getProductId()), "SUCCESS", null,
                    java.util.Map.of("customerId", customerId, "productId", request.getProductId(), "quantity", request.getQuantity()), idempotencyKey);
            if (idempotencyService != null && idempotencyKey != null && !idempotencyKey.isBlank()) {
                idempotencyService.markCompleted(idempotencyKey, result);
            }
            return result;
        } catch (Exception e) {
            auditLog("CART_ITEM_ADDED", "CART_ITEM", String.valueOf(request.getProductId()), "FAILURE", e.getMessage(),
                    java.util.Map.of("customerId", customerId, "productId", request.getProductId(), "quantity", request.getQuantity()), idempotencyKey);
            if (idempotencyService != null && idempotencyKey != null && !idempotencyKey.isBlank()
                    && !(e instanceof com.eshoppingzone.cart.exception.IdempotencyConflictException)) {
                idempotencyService.markFailed(idempotencyKey, e.getMessage());
            }
            throw e;
        }
    }

    private CartDto executeAddToCart(Long customerId, AddToCartRequest request) {
        Cart cart = getOrCreateCart(customerId);

        // Fetch product snapshot from Product Service
        ApiResponse<ProductSnapshotDto> productResponse;
        try {
            productResponse = productClient.getProductById(request.getProductId());
        } catch (feign.FeignException.NotFound e) {
            throw new ResourceNotFoundException("Product not found with id: " + request.getProductId());
        } catch (feign.FeignException e) {
            if (e.status() == 404) {
                throw new ResourceNotFoundException("Product not found with id: " + request.getProductId());
            }
            log.error("Error communicating with Product Service for productId {}: {}", request.getProductId(), e.getMessage());
            throw new RuntimeException("Failed to retrieve product details: " + e.getMessage(), e);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error retrieving product from ProductClient: {}", e.getMessage());
            throw new RuntimeException("Product Service is currently unavailable", e);
        }

        if (productResponse == null || !productResponse.isSuccess() || productResponse.getData() == null) {
            throw new ResourceNotFoundException("Product not found with id: " + request.getProductId());
        }

        ProductSnapshotDto productSnapshot = productResponse.getData();
        if (productSnapshot.getStatus() == null || !"ACTIVE".equalsIgnoreCase(productSnapshot.getStatus())) {
            throw new ResourceNotFoundException("Product is not currently available for purchase");
        }

        String productName = productSnapshot.getName();
        BigDecimal unitPrice = productSnapshot.getPrice();

        Optional<CartItem> existingItemOpt = cartItemRepository.findByCartAndProductId(cart, request.getProductId());

        if (existingItemOpt.isPresent()) {
            CartItem existing = existingItemOpt.get();
            existing.setQuantity(existing.getQuantity() + request.getQuantity());
            existing.setUnitPrice(unitPrice);
            existing.setProductName(productName);
            cartItemRepository.save(existing);
        } else {
            CartItem newItem = new CartItem();
            newItem.setCart(cart);
            newItem.setProductId(request.getProductId());
            newItem.setProductName(productName);
            newItem.setUnitPrice(unitPrice);
            newItem.setQuantity(request.getQuantity());
            cart.getItems().add(newItem);
            cartItemRepository.save(newItem);
        }

        Cart updatedCart = cartRepository.save(cart);
        return CartDto.fromEntity(updatedCart);
    }

    @Override
    @Transactional
    public CartDto updateCartItemQuantity(Long customerId, Long itemId, Integer quantity) {
        Cart cart = getOrCreateCart(customerId);
        CartItem item = cartItemRepository.findByIdAndCart(itemId, cart)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found with id: " + itemId));

        if (quantity <= 0) {
            cart.getItems().remove(item);
            cartItemRepository.delete(item);
        } else {
            item.setQuantity(quantity);
            cartItemRepository.save(item);
        }

        Cart updatedCart = cartRepository.save(cart);
        auditLog("CART_ITEM_UPDATED", "CART_ITEM", String.valueOf(itemId), "SUCCESS", null,
                java.util.Map.of("customerId", customerId, "cartItemId", itemId, "newQuantity", quantity), null);
        return CartDto.fromEntity(updatedCart);
    }

    @Override
    @Transactional
    public CartDto removeCartItem(Long customerId, Long itemId) {
        Cart cart = getOrCreateCart(customerId);
        CartItem item = cartItemRepository.findByIdAndCart(itemId, cart)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found with id: " + itemId));

        cart.getItems().remove(item);
        cartItemRepository.delete(item);

        Cart updatedCart = cartRepository.save(cart);
        auditLog("CART_ITEM_REMOVED", "CART_ITEM", String.valueOf(itemId), "SUCCESS", null,
                java.util.Map.of("customerId", customerId, "cartItemId", itemId), null);
        return CartDto.fromEntity(updatedCart);
    }

    @Override
    @Transactional
    public void clearCart(Long customerId) {
        Cart cart = getOrCreateCart(customerId);
        cart.getItems().clear();
        cartRepository.save(cart);
        auditLog("CART_CLEARED", "CART", String.valueOf(cart.getId()), "SUCCESS", null,
                java.util.Map.of("customerId", customerId), null);
    }

    @Override
    @Transactional(readOnly = true)
    public CartDto getCartByCustomerId(Long customerId) {
        Cart cart = cartRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found for customer: " + customerId));
        return CartDto.fromEntity(cart);
    }
}
