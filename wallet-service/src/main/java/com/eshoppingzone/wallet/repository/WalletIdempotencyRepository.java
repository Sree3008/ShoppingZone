package com.eshoppingzone.wallet.repository;

import com.eshoppingzone.wallet.entity.WalletIdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WalletIdempotencyRepository extends JpaRepository<WalletIdempotencyRecord, Long> {

    Optional<WalletIdempotencyRecord> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);
}
