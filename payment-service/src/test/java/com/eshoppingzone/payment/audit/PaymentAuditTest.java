package com.eshoppingzone.payment.audit;

import com.eshoppingzone.payment.audit.entity.AuditLog;
import com.eshoppingzone.payment.audit.repository.AuditLogRepository;
import com.eshoppingzone.payment.audit.service.AuditLogService;
import com.eshoppingzone.payment.client.OrderClient;
import com.eshoppingzone.payment.client.WalletClient;
import com.eshoppingzone.payment.dto.ProcessPaymentRequest;
import com.eshoppingzone.payment.dto.RefundRequest;
import com.eshoppingzone.payment.entity.Payment;
import com.eshoppingzone.payment.entity.PaymentMethod;
import com.eshoppingzone.payment.entity.PaymentStatus;
import com.eshoppingzone.payment.entity.Refund;
import com.eshoppingzone.payment.entity.RefundStatus;
import com.eshoppingzone.payment.repository.PaymentRepository;
import com.eshoppingzone.payment.repository.RefundRepository;
import com.eshoppingzone.payment.service.PaymentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PaymentAuditTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private WalletClient walletClient;

    @Mock
    private OrderClient orderClient;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private AuditLogRepository auditLogRepository;

    private PaymentServiceImpl paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentServiceImpl(
                paymentRepository,
                refundRepository,
                walletClient,
                orderClient,
                rabbitTemplate,
                null,
                auditLogService
        );
    }

    @Test
    void testCodPaymentCreatesAuditLog() {
        ProcessPaymentRequest request = new ProcessPaymentRequest();
        request.setOrderId(101L);
        request.setCustomerId(50L);
        request.setAmount(new BigDecimal("120.00"));
        request.setPaymentMethod(PaymentMethod.COD);

        when(paymentRepository.findByOrderId(101L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            p.setId(1L);
            return p;
        });

        paymentService.processPayment(request);

        ArgumentCaptor<String> actionCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> resourceTypeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> resourceIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> outcomeCaptor = ArgumentCaptor.forClass(String.class);

        verify(auditLogService, times(1)).logAction(
                actionCaptor.capture(),
                resourceTypeCaptor.capture(),
                resourceIdCaptor.capture(),
                outcomeCaptor.capture(),
                any(),
                any()
        );

        assertEquals("PAYMENT_PENDING", actionCaptor.getValue());
        assertEquals("PAYMENT", resourceTypeCaptor.getValue());
        assertEquals("1", resourceIdCaptor.getValue());
        assertEquals("SUCCESS", outcomeCaptor.getValue());
    }

    @Test
    void testCompleteCodPaymentCreatesAuditLog() {
        Payment payment = new Payment(1L, 101L, 50L, new BigDecimal("120.00"), PaymentMethod.COD, PaymentStatus.PENDING, "TXN-123");
        when(paymentRepository.findByOrderId(101L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);

        paymentService.completeCodPayment(101L);

        ArgumentCaptor<String> actionCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditLogService, times(1)).logAction(
                actionCaptor.capture(),
                eq("PAYMENT"),
                eq("1"),
                eq("SUCCESS"),
                isNull(),
                any()
        );

        assertEquals("PAYMENT_COD_COMPLETED", actionCaptor.getValue());
    }

    @Test
    void testApproveRefundCreatesAuditLog() {
        Refund refund = new Refund(10L, 1L, 101L, 50L, new BigDecimal("120.00"), "Damaged", RefundStatus.PENDING, "REF-123");
        Payment payment = new Payment(1L, 101L, 50L, new BigDecimal("120.00"), PaymentMethod.WALLET, PaymentStatus.SUCCESS, "TXN-123");

        when(refundRepository.findByIdWithLock(10L)).thenReturn(Optional.of(refund));
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(refundRepository.save(any(Refund.class))).thenReturn(refund);

        paymentService.approveRefund(10L);

        ArgumentCaptor<String> actionCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditLogService, times(1)).logAction(
                actionCaptor.capture(),
                eq("REFUND"),
                eq("10"),
                eq("SUCCESS"),
                isNull(),
                any()
        );

        assertEquals("REFUND_APPROVED", actionCaptor.getValue());
    }

    @Test
    void testAuditLogImmutability() {
        AuditLog log = new AuditLog();
        assertThrows(UnsupportedOperationException.class, log::preUpdate);
        assertThrows(UnsupportedOperationException.class, log::preRemove);
    }
}
