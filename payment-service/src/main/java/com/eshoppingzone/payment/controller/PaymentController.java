package com.eshoppingzone.payment.controller;

import com.eshoppingzone.payment.dto.ApiResponse;
import com.eshoppingzone.payment.dto.PaymentDto;
import com.eshoppingzone.payment.dto.ProcessPaymentRequest;
import com.eshoppingzone.payment.entity.Payment;
import com.eshoppingzone.payment.entity.PaymentMethod;
import com.eshoppingzone.payment.entity.PaymentStatus;
import com.eshoppingzone.payment.repository.PaymentRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentRepository paymentRepository;

    public PaymentController(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @PostMapping("/process")
    public ResponseEntity<ApiResponse<PaymentDto>> processPayment(@Valid @RequestBody ProcessPaymentRequest request) {
        Optional<Payment> existingOpt = paymentRepository.findByOrderId(request.getOrderId());
        if (existingOpt.isPresent() && existingOpt.get().getStatus() == PaymentStatus.SUCCESS) {
            return ResponseEntity.ok(ApiResponse.success("Payment already completed", PaymentDto.fromEntity(existingOpt.get())));
        }

        Payment payment = existingOpt.orElseGet(Payment::new);
        payment.setOrderId(request.getOrderId());
        payment.setCustomerId(request.getCustomerId());
        payment.setAmount(request.getAmount());
        payment.setPaymentMethod(request.getPaymentMethod());

        String txnRef = "TXN-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();
        payment.setTransactionReference(txnRef);

        if (request.getPaymentMethod() == PaymentMethod.COD) {
            payment.setStatus(PaymentStatus.PENDING);
        } else {
            payment.setStatus(PaymentStatus.SUCCESS);
        }

        Payment saved = paymentRepository.save(payment);
        return ResponseEntity.ok(ApiResponse.success("Payment processed successfully", PaymentDto.fromEntity(saved)));
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<ApiResponse<PaymentDto>> getPaymentByOrderId(@PathVariable Long orderId) {
        return paymentRepository.findByOrderId(orderId)
                .map(payment -> ResponseEntity.ok(ApiResponse.success("Payment retrieved successfully", PaymentDto.fromEntity(payment))))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Payment not found for order ID: " + orderId)));
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<ApiResponse<List<PaymentDto>>> getPaymentsByCustomerId(@PathVariable Long customerId) {
        List<PaymentDto> payments = paymentRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)
                .stream()
                .map(PaymentDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Payments retrieved successfully", payments));
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<PaymentDto>>> getMyPayments(@RequestParam(required = false, defaultValue = "1") Long customerId) {
        return getPaymentsByCustomerId(customerId);
    }

    @PostMapping("/cod/{orderId}/complete")
    public ResponseEntity<ApiResponse<PaymentDto>> completeCodPayment(@PathVariable Long orderId) {
        Optional<Payment> opt = paymentRepository.findByOrderId(orderId);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Payment record not found for order ID: " + orderId));
        }

        Payment payment = opt.get();
        if (payment.getPaymentMethod() == PaymentMethod.COD) {
            payment.setStatus(PaymentStatus.SUCCESS);
            Payment saved = paymentRepository.save(payment);
            return ResponseEntity.ok(ApiResponse.success("COD payment completed successfully", PaymentDto.fromEntity(saved)));
        }

        return ResponseEntity.ok(ApiResponse.success("Payment already completed", PaymentDto.fromEntity(payment)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PaymentDto>>> getAllPayments() {
        List<PaymentDto> payments = paymentRepository.findAll()
                .stream()
                .map(PaymentDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("All payments retrieved successfully", payments));
    }
}
