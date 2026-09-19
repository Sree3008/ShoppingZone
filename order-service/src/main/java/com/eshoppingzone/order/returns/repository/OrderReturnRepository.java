package com.eshoppingzone.order.returns.repository;

import com.eshoppingzone.order.returns.entity.OrderReturn;
import com.eshoppingzone.order.returns.enums.ReturnStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderReturnRepository extends JpaRepository<OrderReturn, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM OrderReturn r WHERE r.id = :id")
    Optional<OrderReturn> findByIdWithLock(@Param("id") Long id);

    Optional<OrderReturn> findByReturnNumber(String returnNumber);

    List<OrderReturn> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    Optional<OrderReturn> findByIdAndCustomerId(Long id, Long customerId);

    List<OrderReturn> findByOrderIdOrderByCreatedAtDesc(Long orderId);

    List<OrderReturn> findByStatusOrderByCreatedAtDesc(ReturnStatus status);

    Optional<OrderReturn> findByIdempotencyKey(String idempotencyKey);

    List<OrderReturn> findByOrderItemId(Long orderItemId);

    @Query("SELECT COALESCE(SUM(r.quantity), 0) FROM OrderReturn r WHERE r.orderItemId = :orderItemId AND r.status NOT IN (com.eshoppingzone.order.returns.enums.ReturnStatus.RETURN_REJECTED, com.eshoppingzone.order.returns.enums.ReturnStatus.RETURN_CANCELLED)")
    Integer sumNonRejectedCancelledQuantityByOrderItemId(@Param("orderItemId") Long orderItemId);

    @Query("SELECT r FROM OrderReturn r WHERE r.orderItemId = :orderItemId AND r.status NOT IN (com.eshoppingzone.order.returns.enums.ReturnStatus.RETURN_REJECTED, com.eshoppingzone.order.returns.enums.ReturnStatus.RETURN_CANCELLED, com.eshoppingzone.order.returns.enums.ReturnStatus.RETURN_COMPLETED)")
    List<OrderReturn> findActiveReturnsByOrderItemId(@Param("orderItemId") Long orderItemId);
}
