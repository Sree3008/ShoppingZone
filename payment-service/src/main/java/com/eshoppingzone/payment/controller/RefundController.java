package com.eshoppingzone.payment.controller;

import com.eshoppingzone.payment.dto.ApiResponse;
import com.eshoppingzone.payment.dto.RefundDto;
import com.eshoppingzone.payment.dto.RefundRequest;
import com.eshoppingzone.payment.entity.Payment;
import com.eshoppingzone.payment.entity.PaymentStatus;
import com.eshoppingzone.payment.entity.Refund;
import com.eshoppingzone.payment.entity.RefundStatus;
import com.eshoppingzone.payment.repository.PaymentRepository;
import com.eshoppingzone.payment.repository.RefundRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/refunds")
public class RefundController {

    private final RefundRepository refundRepository;
    private final PaymentRepository paymentRepository;

    public RefundController(RefundRepository refundRepository, PaymentRepository paymentRepository) {
        this.refundRepository = refundRepository;
        this.paymentRepository = paymentRepository;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RefundDto>> requestRefund(
            @RequestParam(required = false) Long customerId,
            @Valid @RequestBody RefundRequest request) {

        Optional<Payment> paymentOpt = paymentRepository.findByOrderId(request.getOrderId());
        if (paymentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Payment not found for order ID: " + request.getOrderId()));
        }

        Payment payment = paymentOpt.get();
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Refund can only be requested for completed payments"));
        }

        Long actualCustomerId = customerId != null ? customerId :
                (request.getCustomerId() != null ? request.getCustomerId() : payment.getCustomerId());

        String refNum = "REF-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();
        Refund refund = new Refund();
        refund.setPaymentId(payment.getId());
        refund.setOrderId(request.getOrderId());
        refund.setCustomerId(actualCustomerId);
        refund.setAmount(request.getAmount());
        refund.setReason(request.getReason() != null ? request.getReason() : "Customer requested refund");
        refund.setStatus(RefundStatus.PENDING);
        refund.setRefundReference(refNum);

        Refund saved = refundRepository.save(refund);
        return ResponseEntity.ok(ApiResponse.success("Refund requested successfully", RefundDto.fromEntity(saved)));
    }

    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<List<RefundDto>>> getPendingRefunds() {
        List<RefundDto> pending = refundRepository.findByStatusOrderByCreatedAtDesc(RefundStatus.PENDING)
                .stream()
                .map(RefundDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Pending refunds retrieved successfully", pending));
    }

    @PutMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<RefundDto>> approveRefund(@PathVariable Long id) {
        Optional<Refund> refundOpt = refundRepository.findById(id);
        if (refundOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Refund request not found with id: " + id));
        }

        Refund refund = refundOpt.get();
        if (refund.getStatus() != RefundStatus.PENDING) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Refund is not in PENDING state. Current status: " + refund.getStatus()));
        }

        refund.setStatus(RefundStatus.APPROVED);
        Refund savedRefund = refundRepository.save(refund);

        if (refund.getPaymentId() != null) {
            paymentRepository.findById(refund.getPaymentId()).ifPresent(payment -> {
                payment.setStatus(PaymentStatus.REFUNDED);
                paymentRepository.save(payment);
            });
        }

        return ResponseEntity.ok(ApiResponse.success("Refund approved and processed", RefundDto.fromEntity(savedRefund)));
    }

    @PutMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<RefundDto>> rejectRefund(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {

        Optional<Refund> refundOpt = refundRepository.findById(id);
        if (refundOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Refund request not found with id: " + id));
        }

        Refund refund = refundOpt.get();
        if (refund.getStatus() != RefundStatus.PENDING) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Refund is not in PENDING state. Current status: " + refund.getStatus()));
        }

        String reason = (body != null && body.containsKey("reason")) ? body.get("reason") : "Refund request rejected by admin";
        refund.setStatus(RefundStatus.REJECTED);
        refund.setReason(refund.getReason() != null ? refund.getReason() + " (Rejected: " + reason + ")" : reason);

        Refund saved = refundRepository.save(refund);
        return ResponseEntity.ok(ApiResponse.success("Refund rejected", RefundDto.fromEntity(saved)));
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<ApiResponse<List<RefundDto>>> getRefundsByCustomerId(@PathVariable Long customerId) {
        List<RefundDto> refunds = refundRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)
                .stream()
                .map(RefundDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Refunds retrieved successfully", refunds));
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<RefundDto>>> getMyRefunds(@RequestParam(required = false, defaultValue = "1") Long customerId) {
        return getRefundsByCustomerId(customerId);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RefundDto>> getRefundById(@PathVariable Long id) {
        return refundRepository.findById(id)
                .map(r -> ResponseEntity.ok(ApiResponse.success("Refund retrieved successfully", RefundDto.fromEntity(r))))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Refund not found with id: " + id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<RefundDto>>> getAllRefunds() {
        List<RefundDto> refunds = refundRepository.findAll()
                .stream()
                .map(RefundDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("All refunds retrieved successfully", refunds));
    }
}
