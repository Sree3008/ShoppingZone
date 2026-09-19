package com.eshoppingzone.notification.audit;

import com.eshoppingzone.notification.audit.entity.AuditLog;
import com.eshoppingzone.notification.audit.service.AuditLogService;
import com.eshoppingzone.notification.dto.NotificationDto;
import com.eshoppingzone.notification.entity.Notification;
import com.eshoppingzone.notification.repository.NotificationRepository;
import com.eshoppingzone.notification.service.NotificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class NotificationAuditTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private AuditLogService auditLogService;

    private NotificationServiceImpl notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationServiceImpl(
                notificationRepository,
                auditLogService
        );
    }

    @Test
    void testCreateNotificationCreatesAuditLog() {
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            n.setId(10L);
            return n;
        });

        NotificationDto dto = notificationService.createNotification(50L, "Order Shipped", "Your order #10 is on its way", "ORDER_SHIPPED");

        assertNotNull(dto);

        ArgumentCaptor<String> actionCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditLogService, times(1)).logAction(
                actionCaptor.capture(),
                eq("NOTIFICATION"),
                eq("10"),
                eq("SUCCESS"),
                isNull(),
                any()
        );

        assertEquals("NOTIFICATION_CREATED", actionCaptor.getValue());
    }

    @Test
    void testAuditLogImmutability() {
        AuditLog log = new AuditLog();
        assertThrows(UnsupportedOperationException.class, log::preUpdate);
        assertThrows(UnsupportedOperationException.class, log::preRemove);
    }
}
