package com.eshoppingzone.delivery.audit.repository;

import com.eshoppingzone.delivery.audit.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {
    Optional<AuditLog> findByEventId(String eventId);
}
