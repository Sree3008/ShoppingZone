package com.eshoppingzone.product.audit.repository;

import com.eshoppingzone.product.audit.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    Page<AuditLog> findByActorUserId(Long actorUserId, Pageable pageable);

    Page<AuditLog> findByResourceTypeAndResourceId(String resourceType, String resourceId, Pageable pageable);

    Optional<AuditLog> findByEventId(String eventId);
}
