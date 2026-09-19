package com.eshoppingzone.payment.repository;

import com.eshoppingzone.payment.entity.Refund;
import com.eshoppingzone.payment.entity.RefundStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RefundRepository extends JpaRepository<Refund, Long> {
    List<Refund> findByStatusOrderByCreatedAtDesc(RefundStatus status);
    List<Refund> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
    Optional<Refund> findByOrderId(Long orderId);
    List<Refund> findByOrderIdOrderByCreatedAtDesc(Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Refund r WHERE r.id = :id")
    Optional<Refund> findByIdWithLock(@Param("id") Long id);
}
