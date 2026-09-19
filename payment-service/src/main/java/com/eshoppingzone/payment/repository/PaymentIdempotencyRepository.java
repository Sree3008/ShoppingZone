package com.eshoppingzone.payment.repository;

import com.eshoppingzone.payment.entity.PaymentIdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentIdempotencyRepository extends JpaRepository<PaymentIdempotencyRecord, Long> {

    Optional<PaymentIdempotencyRecord> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);
}
