package com.eshoppingzone.delivery.audit;

import com.eshoppingzone.delivery.audit.entity.AuditLog;
import com.eshoppingzone.delivery.audit.service.AuditLogService;
import com.eshoppingzone.delivery.client.OrderClient;
import com.eshoppingzone.delivery.client.PaymentClient;
import com.eshoppingzone.delivery.dto.AssignDeliveryRequest;
import com.eshoppingzone.delivery.dto.UpdateDeliveryStatusRequest;
import com.eshoppingzone.delivery.entity.Delivery;
import com.eshoppingzone.delivery.entity.DeliveryStatus;
import com.eshoppingzone.delivery.repository.DeliveryRepository;
import com.eshoppingzone.delivery.service.DeliveryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DeliveryAuditTest {

    @Mock
    private DeliveryRepository deliveryRepository;

    @Mock
    private OrderClient orderClient;

    @Mock
    private PaymentClient paymentClient;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private AuditLogService auditLogService;

    private DeliveryServiceImpl deliveryService;

    @BeforeEach
    void setUp() {
        deliveryService = new DeliveryServiceImpl(
                deliveryRepository,
                orderClient,
                paymentClient,
                rabbitTemplate,
                auditLogService
        );
    }

    @Test
    void testAssignDeliveryCreatesAuditLog() {
        AssignDeliveryRequest request = new AssignDeliveryRequest(10L, 50L, 30L, "Warehouse 1", "Home address", "1234567890");

        when(deliveryRepository.findByOrderId(10L)).thenReturn(Optional.empty());
        when(deliveryRepository.save(any(Delivery.class))).thenAnswer(inv -> {
            Delivery d = inv.getArgument(0);
            d.setId(1L);
            return d;
        });

        deliveryService.assignDelivery(request);

        ArgumentCaptor<String> actionCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditLogService, times(1)).logAction(
                actionCaptor.capture(),
                eq("DELIVERY"),
                eq("1"),
                eq("SUCCESS"),
                isNull(),
                any()
        );

        assertEquals("DELIVERY_ASSIGNED", actionCaptor.getValue());
    }

    @Test
    void testUpdateDeliveryStatusCreatesAuditLog() {
        Delivery delivery = new Delivery(10L, 50L, "Home address", "1234567890");
        delivery.setId(1L);
        delivery.setDeliveryAgentId(30L);
        delivery.setStatus(DeliveryStatus.ASSIGNED);

        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));
        when(deliveryRepository.save(any(Delivery.class))).thenReturn(delivery);

        UpdateDeliveryStatusRequest request = new UpdateDeliveryStatusRequest();
        request.setStatus("PICKED_UP");

        deliveryService.updateDeliveryStatusByAgent(30L, 1L, request);

        ArgumentCaptor<String> actionCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditLogService, times(1)).logAction(
                actionCaptor.capture(),
                eq("DELIVERY"),
                eq("1"),
                eq("SUCCESS"),
                isNull(),
                any()
        );

        assertEquals("DELIVERY_STATUS_CHANGED", actionCaptor.getValue());
    }

    @Test
    void testAuditLogImmutability() {
        AuditLog log = new AuditLog();
        assertThrows(UnsupportedOperationException.class, log::preUpdate);
        assertThrows(UnsupportedOperationException.class, log::preRemove);
    }
}
