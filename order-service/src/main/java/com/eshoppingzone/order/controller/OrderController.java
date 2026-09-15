package com.eshoppingzone.order.controller;

import com.eshoppingzone.order.entity.Order;
import com.eshoppingzone.order.entity.OrderItem;
import com.eshoppingzone.order.entity.OrderStatus;
import com.eshoppingzone.order.entity.PaymentStatus;
import com.eshoppingzone.order.repository.OrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderRepository orderRepository;

    public OrderController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @GetMapping
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrderById(@PathVariable Long id) {
        return orderRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/customer/{customerId}")
    public List<Order> getOrdersByCustomer(@PathVariable Long customerId) {
        return orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
    }

    @PostMapping
    public ResponseEntity<Order> createOrder(@RequestBody Order order) {
        if (order.getOrderNumber() == null || order.getOrderNumber().isBlank()) {
            order.setOrderNumber("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }
        if (order.getStatus() == null) {
            order.setStatus(OrderStatus.CONFIRMED);
        }
        if (order.getPaymentStatus() == null) {
            order.setPaymentStatus(PaymentStatus.PENDING);
        }

        if (order.getItems() != null) {
            BigDecimal calculatedTotal = BigDecimal.ZERO;
            for (OrderItem item : order.getItems()) {
                item.setOrder(order);
                if (item.getLineTotal() == null && item.getUnitPrice() != null && item.getQuantity() != null) {
                    item.setLineTotal(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
                }
                if (item.getLineTotal() != null) {
                    calculatedTotal = calculatedTotal.add(item.getLineTotal());
                }
            }
            if (order.getTotalAmount() == null) {
                order.setTotalAmount(calculatedTotal);
            }
        }

        Order savedOrder = orderRepository.save(order);
        return new ResponseEntity<>(savedOrder, HttpStatus.CREATED);
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<Order> updateOrderStatus(@PathVariable Long id, @RequestParam OrderStatus status) {
        return orderRepository.findById(id).map(order -> {
            order.setStatus(status);
            return ResponseEntity.ok(orderRepository.save(order));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelOrder(@PathVariable Long id) {
        return orderRepository.findById(id).map(order -> {
            order.setStatus(OrderStatus.CANCELLED);
            orderRepository.save(order);
            return ResponseEntity.noContent().<Void>build();
        }).orElse(ResponseEntity.notFound().build());
    }
}
