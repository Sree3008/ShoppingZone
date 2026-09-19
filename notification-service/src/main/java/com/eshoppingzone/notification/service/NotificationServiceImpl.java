package com.eshoppingzone.notification.service;

import com.eshoppingzone.notification.dto.NotificationDto;
import com.eshoppingzone.notification.entity.Notification;
import com.eshoppingzone.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private final NotificationRepository notificationRepository;
    private final com.eshoppingzone.notification.audit.service.AuditLogService auditLogService;

    @org.springframework.beans.factory.annotation.Autowired
    public NotificationServiceImpl(NotificationRepository notificationRepository,
                                   @org.springframework.beans.factory.annotation.Autowired(required = false) com.eshoppingzone.notification.audit.service.AuditLogService auditLogService) {
        this.notificationRepository = notificationRepository;
        this.auditLogService = auditLogService;
    }

    public NotificationServiceImpl(NotificationRepository notificationRepository) {
        this(notificationRepository, null);
    }

    private java.util.Map<String, Object> safeMeta(Object... keyValues) {
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            if (i + 1 < keyValues.length && keyValues[i] != null && keyValues[i + 1] != null) {
                map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
            }
        }
        return map;
    }

    private void audit(String action, String resourceType, String resourceId, String outcome, String failureReason, java.util.Map<String, Object> metadata) {
        if (auditLogService != null) {
            try {
                auditLogService.logAction(action, resourceType, resourceId, outcome, failureReason, metadata);
            } catch (Exception e) {
                log.warn("Failed to write audit log: {}", e.getMessage());
            }
        }
    }

    @Override
    @Transactional
    public NotificationDto createNotification(Long userId, String title, String message, String type) {
        log.info("[NOTIFICATION-SERVICE] Persisting notification for userId: {}, type: {}, title: '{}'", userId, type, title);
        Notification notification = new Notification(userId, title, message, type);
        Notification saved = notificationRepository.save(notification);
        audit("NOTIFICATION_CREATED", "NOTIFICATION", String.valueOf(saved.getId()), "SUCCESS", null,
                safeMeta("userId", userId, "type", type, "title", title));
        return NotificationDto.fromEntity(saved);
    }

    @Override
    public List<NotificationDto> getMyNotifications(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(NotificationDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<NotificationDto> getMyUnreadNotifications(Long userId) {
        return notificationRepository.findByUserIdAndIsReadOrderByCreatedAtDesc(userId, false).stream()
                .map(NotificationDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public NotificationDto markAsRead(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found with id: " + notificationId));

        if (!notification.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized to update this notification");
        }

        notification.setRead(true);
        Notification saved = notificationRepository.save(notification);
        return NotificationDto.fromEntity(saved);
    }

    @Override
    @Transactional
    public void markAllAsRead(Long userId) {
        List<Notification> unread = notificationRepository.findByUserIdAndIsReadOrderByCreatedAtDesc(userId, false);
        for (Notification n : unread) {
            n.setRead(true);
        }
        notificationRepository.saveAll(unread);
    }

    @Override
    public List<NotificationDto> getUserNotifications(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(NotificationDto::fromEntity)
                .collect(Collectors.toList());
    }
}
