package com.eshoppingzone.cart.repository;

import com.eshoppingzone.cart.entity.CartIdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CartIdempotencyRepository extends JpaRepository<CartIdempotencyRecord, Long> {

    Optional<CartIdempotencyRecord> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);
}
