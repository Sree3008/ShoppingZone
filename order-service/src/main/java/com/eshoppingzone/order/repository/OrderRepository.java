package com.eshoppingzone.order.repository;

import com.eshoppingzone.order.entity.Order;
import com.eshoppingzone.order.entity.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByOrderNumber(String orderNumber);
    Optional<Order> findByIdempotencyKey(String idempotencyKey);
    List<Order> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
    Optional<Order> findByIdAndCustomerId(Long id, Long customerId);
    List<Order> findByMerchantIdOrderByCreatedAtDesc(Long merchantId);
    Optional<Order> findByIdAndMerchantId(Long id, Long merchantId);
    List<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdWithLock(@Param("id") Long id);
}
