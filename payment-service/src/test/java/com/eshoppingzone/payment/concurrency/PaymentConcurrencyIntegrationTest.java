package com.eshoppingzone.payment.concurrency;

import com.eshoppingzone.payment.client.OrderClient;
import com.eshoppingzone.payment.client.WalletClient;
import com.eshoppingzone.payment.dto.ApiResponse;
import com.eshoppingzone.payment.dto.WalletDto;
import com.eshoppingzone.payment.entity.Payment;
import com.eshoppingzone.payment.entity.PaymentMethod;
import com.eshoppingzone.payment.entity.PaymentStatus;
import com.eshoppingzone.payment.entity.Refund;
import com.eshoppingzone.payment.entity.RefundStatus;
import com.eshoppingzone.payment.exception.InvalidRefundException;
import com.eshoppingzone.payment.repository.PaymentRepository;
import com.eshoppingzone.payment.repository.RefundRepository;
import com.eshoppingzone.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
class PaymentConcurrencyIntegrationTest {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:payconcdb;DB_CLOSE_DELAY=-1;MODE=MySQL");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("spring.cloud.config.enabled", () -> "false");
        registry.add("spring.cloud.discovery.enabled", () -> "false");
    }

    @MockBean
    private WalletClient walletClient;

    @MockBean
    private OrderClient orderClient;

    @MockBean
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private RefundRepository refundRepository;

    private Long orderId = 777L;
    private Long customerId = 42L;
    private Long refundId;

    @BeforeEach
    void setUp() {
        refundRepository.deleteAll();
        paymentRepository.deleteAll();

        when(walletClient.debit(any())).thenReturn(ApiResponse.success("Debited", new WalletDto(1L, 1L, new BigDecimal("99000.00"))));
        when(walletClient.credit(any())).thenReturn(ApiResponse.success("Credited", new WalletDto(2L, customerId, new BigDecimal("1000.00"))));

        Payment payment = new Payment();
        payment.setOrderId(orderId);
        payment.setCustomerId(customerId);
        payment.setAmount(new BigDecimal("350.00"));
        payment.setPaymentMethod(PaymentMethod.WALLET);
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setTransactionReference("TXN-CONC-777");
        Payment savedPayment = paymentRepository.save(payment);

        Refund refund = new Refund();
        refund.setPaymentId(savedPayment.getId());
        refund.setOrderId(orderId);
        refund.setCustomerId(customerId);
        refund.setAmount(new BigDecimal("350.00"));
        refund.setReason("Return #RET-777: Defective");
        refund.setStatus(RefundStatus.PENDING);
        refund.setRefundReference("REF-CONC-777");
        Refund savedRefund = refundRepository.save(refund);
        refundId = savedRefund.getId();
    }

    @Test
    @DisplayName("Two concurrent requests to approve the same refund: exactly 1 SUCCESS, 1 FAILURE, no duplicate refund side effect")
    void testConcurrentRefundApprovalRace() throws Exception {
        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    paymentService.approveRefund(refundId);
                    successCount.incrementAndGet();
                } catch (InvalidRefundException ire) {
                    failureCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Concurrent refund approval threads timed out");
        executor.shutdown();

        assertEquals(1, successCount.get(), "Exactly one refund approval must succeed");
        assertEquals(1, failureCount.get(), "Exactly one refund approval must fail due to state conflict");

        // Verify database state
        Refund finalRefund = refundRepository.findById(refundId).orElseThrow();
        assertEquals(RefundStatus.APPROVED, finalRefund.getStatus(), "Final refund status must be APPROVED");

        Payment finalPayment = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertEquals(PaymentStatus.REFUNDED, finalPayment.getStatus(), "Payment status must be REFUNDED");
    }
}
