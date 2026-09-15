package com.eshoppingzone.cart.controller;

import com.eshoppingzone.cart.entity.Cart;
import com.eshoppingzone.cart.entity.CartItem;
import com.eshoppingzone.cart.repository.CartItemRepository;
import com.eshoppingzone.cart.repository.CartRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/cart")
public class CartController {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;

    public CartController(CartRepository cartRepository, CartItemRepository cartItemRepository) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
    }

    @GetMapping
    public List<Cart> getAllCarts() {
        return cartRepository.findAll();
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<Cart> getCartByCustomerId(@PathVariable Long customerId) {
        return cartRepository.findByCustomerId(customerId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> {
                    Cart newCart = new Cart(null, customerId);
                    return ResponseEntity.ok(cartRepository.save(newCart));
                });
    }

    @PostMapping("/customer/{customerId}/items")
    public ResponseEntity<Cart> addItemToCart(@PathVariable Long customerId, @RequestBody CartItem item) {
        Cart cart = cartRepository.findByCustomerId(customerId)
                .orElseGet(() -> cartRepository.save(new Cart(null, customerId)));

        Optional<CartItem> existingItem = cartItemRepository.findByCartAndProductId(cart, item.getProductId());
        if (existingItem.isPresent()) {
            CartItem cartItem = existingItem.get();
            cartItem.setQuantity(cartItem.getQuantity() + item.getQuantity());
            if (item.getUnitPrice() != null) {
                cartItem.setUnitPrice(item.getUnitPrice());
            }
            cartItemRepository.save(cartItem);
        } else {
            item.setCart(cart);
            cartItemRepository.save(item);
        }

        Cart updatedCart = cartRepository.findByCustomerId(customerId).orElse(cart);
        return new ResponseEntity<>(updatedCart, HttpStatus.CREATED);
    }

    @PutMapping("/items/{itemId}")
    public ResponseEntity<CartItem> updateCartItemQuantity(@PathVariable Long itemId, @RequestParam Integer quantity) {
        return cartItemRepository.findById(itemId).map(item -> {
            item.setQuantity(quantity);
            return ResponseEntity.ok(cartItemRepository.save(item));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<Void> removeCartItem(@PathVariable Long itemId) {
        if (cartItemRepository.existsById(itemId)) {
            cartItemRepository.deleteById(itemId);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/customer/{customerId}")
    public ResponseEntity<Void> clearCart(@PathVariable Long customerId) {
        Optional<Cart> cartOpt = cartRepository.findByCustomerId(customerId);
        if (cartOpt.isPresent()) {
            Cart cart = cartOpt.get();
            cart.getItems().clear();
            cartRepository.save(cart);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
