package com.eshoppingzone.payment.audit.specification;

import com.eshoppingzone.payment.audit.entity.AuditLog;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AuditLogSpecification {

    public static Specification<AuditLog> filter(
            String actorUserId,
            String action,
            String resourceType,
            String resourceId,
            String serviceName,
            String outcome,
            String correlationId,
            LocalDateTime startDate,
            LocalDateTime endDate) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (actorUserId != null && !actorUserId.isBlank()) {
                predicates.add(cb.equal(root.get("actorUserId"), actorUserId.trim()));
            }
            if (action != null && !action.isBlank()) {
                predicates.add(cb.equal(root.get("action"), action.trim()));
            }
            if (resourceType != null && !resourceType.isBlank()) {
                predicates.add(cb.equal(root.get("resourceType"), resourceType.trim()));
            }
            if (resourceId != null && !resourceId.isBlank()) {
                predicates.add(cb.equal(root.get("resourceId"), resourceId.trim()));
            }
            if (serviceName != null && !serviceName.isBlank()) {
                predicates.add(cb.equal(root.get("serviceName"), serviceName.trim()));
            }
            if (outcome != null && !outcome.isBlank()) {
                predicates.add(cb.equal(root.get("outcome"), outcome.trim()));
            }
            if (correlationId != null && !correlationId.isBlank()) {
                predicates.add(cb.equal(root.get("correlationId"), correlationId.trim()));
            }
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("timestamp"), endDate));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
