package com.eshoppingzone.review.service;

import com.eshoppingzone.review.client.OrderClient;
import com.eshoppingzone.review.client.ProductClient;
import com.eshoppingzone.review.config.RabbitMQConfig;
import com.eshoppingzone.review.dto.*;
import com.eshoppingzone.review.entity.Review;
import com.eshoppingzone.review.enums.ReviewStatus;
import com.eshoppingzone.review.exception.DuplicateReviewException;
import com.eshoppingzone.review.exception.InvalidReviewStateException;
import com.eshoppingzone.review.exception.ResourceNotFoundException;
import com.eshoppingzone.review.repository.ReviewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReviewServiceImpl implements ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewServiceImpl.class);

    private final ReviewRepository reviewRepository;
    private final OrderClient orderClient;
    private final ProductClient productClient;
    private final RabbitTemplate rabbitTemplate;
    private final com.eshoppingzone.review.audit.service.AuditLogService auditLogService;

    @org.springframework.beans.factory.annotation.Autowired
    public ReviewServiceImpl(ReviewRepository reviewRepository,
                             OrderClient orderClient,
                             ProductClient productClient,
                             RabbitTemplate rabbitTemplate,
                             @org.springframework.beans.factory.annotation.Autowired(required = false) com.eshoppingzone.review.audit.service.AuditLogService auditLogService) {
        this.reviewRepository = reviewRepository;
        this.orderClient = orderClient;
        this.productClient = productClient;
        this.rabbitTemplate = rabbitTemplate;
        this.auditLogService = auditLogService;
    }

    public ReviewServiceImpl(ReviewRepository reviewRepository,
                             OrderClient orderClient,
                             ProductClient productClient,
                             RabbitTemplate rabbitTemplate) {
        this(reviewRepository, orderClient, productClient, rabbitTemplate, null);
    }

    private Map<String, Object> safeMeta(Object... keyValues) {
        Map<String, Object> map = new java.util.HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            if (i + 1 < keyValues.length && keyValues[i] != null && keyValues[i + 1] != null) {
                map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
            }
        }
        return map;
    }

    private void audit(String action, String resourceType, String resourceId, String outcome, String failureReason, Map<String, Object> metadata) {
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
    public ReviewDto createReview(Long customerId, String customerName, CreateReviewRequest request) {
        if (customerId == null) {
            throw new InvalidReviewStateException("Authenticated customer is required");
        }

        // 1. Validate rating
        if (request.getRating() == null || request.getRating() < 1 || request.getRating() > 5) {
            throw new InvalidReviewStateException("Rating must be between 1 and 5");
        }

        // 2. Validate comment
        if (request.getComment() == null || request.getComment().trim().length() < 3 || request.getComment().length() > 2000) {
            throw new InvalidReviewStateException("Comment must be between 3 and 2000 characters");
        }

        // 3. Application-level duplicate check
        if (reviewRepository.existsByCustomerIdAndOrderItemId(customerId, request.getOrderItemId())) {
            throw new DuplicateReviewException("You have already submitted a review for this purchased item");
        }

        // 4. Verify product exists using ProductClient
        try {
            ApiResponse<ProductDto> productResp = productClient.getProductInternal(request.getProductId());
            if (productResp == null || productResp.getData() == null) {
                throw new ResourceNotFoundException("Product not found with ID: " + request.getProductId());
            }
        } catch (ResourceNotFoundException rnfe) {
            throw rnfe;
        } catch (Exception e) {
            log.error("Failed to verify product existence for product {}: {}", request.getProductId(), e.getMessage());
            throw new ResourceNotFoundException("Product not found with ID: " + request.getProductId());
        }

        // 5. Verify order exists and belongs to customer
        OrderDto order;
        try {
            ApiResponse<OrderDto> orderResp = orderClient.getOrderInternal(request.getOrderId());
            if (orderResp == null || orderResp.getData() == null) {
                throw new ResourceNotFoundException("Order not found with ID: " + request.getOrderId());
            }
            order = orderResp.getData();
        } catch (ResourceNotFoundException rnfe) {
            throw rnfe;
        } catch (Exception e) {
            log.error("Failed to retrieve order {}: {}", request.getOrderId(), e.getMessage());
            throw new ResourceNotFoundException("Order not found with ID: " + request.getOrderId());
        }

        if (!customerId.equals(order.getCustomerId())) {
            throw new InvalidReviewStateException("Order does not belong to the authenticated customer");
        }

        // 6. Verify order status is DELIVERED
        if (!"DELIVERED".equalsIgnoreCase(order.getStatus())) {
            throw new InvalidReviewStateException("Reviews can only be submitted for DELIVERED orders. Current status: " + order.getStatus());
        }

        // 7. Verify order item belongs to order and matches requested product
        OrderItemDto matchingItem = order.getItems().stream()
                .filter(item -> item.getId().equals(request.getOrderItemId()))
                .findFirst()
                .orElseThrow(() -> new InvalidReviewStateException("Order item ID " + request.getOrderItemId() + " does not belong to order ID " + request.getOrderId()));

        if (!matchingItem.getProductId().equals(request.getProductId())) {
            throw new InvalidReviewStateException("Product ID " + request.getProductId() + " does not match order item's product ID " + matchingItem.getProductId());
        }

        // 8. Build Review entity
        Review review = new Review();
        review.setCustomerId(customerId);
        review.setCustomerName(customerName != null ? customerName : "Customer " + customerId);
        review.setProductId(request.getProductId());
        review.setOrderId(request.getOrderId());
        review.setOrderItemId(request.getOrderItemId());
        review.setRating(request.getRating());
        review.setTitle(request.getTitle());
        review.setComment(request.getComment().trim());
        review.setStatus(ReviewStatus.PENDING);
        review.setVerifiedPurchase(true);

        // 9. Save review catching DataIntegrityViolationException for concurrent duplicate protection
        Review saved;
        try {
            saved = reviewRepository.save(review);
        } catch (DataIntegrityViolationException dive) {
            log.warn("Database unique constraint violation on (customerId={}, orderItemId={})", customerId, request.getOrderItemId());
            throw new DuplicateReviewException("You have already submitted a review for this purchased item");
        }

        publishReviewEvent(saved, "REVIEW_CREATED", RabbitMQConfig.REVIEW_CREATED_ROUTING_KEY);
        log.info("Review created successfully: ID {}, Status PENDING for product {}", saved.getId(), saved.getProductId());
        audit("REVIEW_CREATED", "REVIEW", String.valueOf(saved.getId()), "SUCCESS", null,
                safeMeta("productId", saved.getProductId(), "orderId", saved.getOrderId(), "rating", saved.getRating(), "customerId", customerId));
        return ReviewDto.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReviewDto> getProductReviews(Long productId, Pageable pageable) {
        Pageable clamped = clampPageable(pageable);
        return reviewRepository.findByProductIdAndStatusOrderByCreatedAtDesc(productId, ReviewStatus.APPROVED, clamped)
                .map(ReviewDto::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductRatingSummaryDto getProductRatingSummary(Long productId) {
        List<Review> approvedReviews = reviewRepository.findByProductIdAndStatus(productId, ReviewStatus.APPROVED);

        Map<String, Long> distribution = new LinkedHashMap<>();
        for (int i = 1; i <= 5; i++) {
            distribution.put(String.valueOf(i), 0L);
        }

        if (approvedReviews.isEmpty()) {
            return new ProductRatingSummaryDto(productId, 0.0, 0L, distribution);
        }

        long sum = 0;
        for (Review r : approvedReviews) {
            int rating = r.getRating();
            sum += rating;
            String key = String.valueOf(rating);
            distribution.put(key, distribution.getOrDefault(key, 0L) + 1L);
        }

        long totalReviews = approvedReviews.size();
        BigDecimal avg = BigDecimal.valueOf(sum)
                .divide(BigDecimal.valueOf(totalReviews), 2, RoundingMode.HALF_UP);

        return new ProductRatingSummaryDto(productId, avg.doubleValue(), totalReviews, distribution);
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewDto getReviewById(Long id, Long requesterId, boolean isAdmin) {
        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with ID: " + id));

        // Public users can only see APPROVED reviews. Requester/owner or Admin can see non-approved reviews.
        if (review.getStatus() != ReviewStatus.APPROVED && !isAdmin && !review.getCustomerId().equals(requesterId)) {
            throw new ResourceNotFoundException("Review not found with ID: " + id);
        }

        return ReviewDto.fromEntity(review);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReviewDto> getMyReviews(Long customerId) {
        return reviewRepository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .filter(r -> r.getStatus() != ReviewStatus.HIDDEN)
                .map(ReviewDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ReviewDto updateReview(Long id, Long customerId, UpdateReviewRequest request) {
        if (request.getRating() == null || request.getRating() < 1 || request.getRating() > 5) {
            throw new InvalidReviewStateException("Rating must be between 1 and 5");
        }
        if (request.getComment() == null || request.getComment().trim().length() < 3 || request.getComment().length() > 2000) {
            throw new InvalidReviewStateException("Comment must be between 3 and 2000 characters");
        }

        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with ID: " + id));

        if (!review.getCustomerId().equals(customerId)) {
            throw new AccessDeniedException("You can only edit your own reviews");
        }

        review.setRating(request.getRating());
        review.setTitle(request.getTitle());
        review.setComment(request.getComment().trim());

        // If review was APPROVED, transition to PENDING for re-moderation
        if (review.getStatus() == ReviewStatus.APPROVED) {
            review.setStatus(ReviewStatus.PENDING);
            log.info("Edited approved review {}. Re-queued to PENDING for moderation.", review.getId());
        }

        Review saved = reviewRepository.save(review);
        publishReviewEvent(saved, "REVIEW_UPDATED", RabbitMQConfig.REVIEW_UPDATED_ROUTING_KEY);
        audit("REVIEW_UPDATED", "REVIEW", String.valueOf(saved.getId()), "SUCCESS", null,
                safeMeta("productId", saved.getProductId(), "rating", saved.getRating(), "customerId", customerId));
        return ReviewDto.fromEntity(saved);
    }

    @Override
    @Transactional
    public void deleteReview(Long id, Long customerId, boolean isAdmin) {
        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with ID: " + id));

        if (!isAdmin && !review.getCustomerId().equals(customerId)) {
            throw new AccessDeniedException("You can only delete your own reviews");
        }

        // Soft deletion: mark status as HIDDEN
        review.setStatus(ReviewStatus.HIDDEN);
        Review saved = reviewRepository.save(review);
        publishReviewEvent(saved, "REVIEW_DELETED", RabbitMQConfig.REVIEW_DELETED_ROUTING_KEY);
        log.info("Soft deleted review {}. Status set to HIDDEN.", id);
        audit("REVIEW_DELETED", "REVIEW", String.valueOf(id), "SUCCESS", null,
                safeMeta("productId", review.getProductId(), "customerId", customerId, "isAdmin", isAdmin));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReviewDto> getAllReviews(Pageable pageable) {
        Pageable clamped = clampPageable(pageable);
        return reviewRepository.findAllByOrderByCreatedAtDesc(clamped).map(ReviewDto::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReviewDto> getPendingReviews(Pageable pageable) {
        Pageable clamped = clampPageable(pageable);
        return reviewRepository.findByStatusOrderByCreatedAtDesc(ReviewStatus.PENDING, clamped).map(ReviewDto::fromEntity);
    }

    @Override
    @Transactional
    public ReviewDto approveReview(Long id) {
        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with ID: " + id));

        if (review.getStatus() != ReviewStatus.PENDING) {
            throw new InvalidReviewStateException("Only PENDING reviews can be approved. Current status: " + review.getStatus());
        }

        review.setStatus(ReviewStatus.APPROVED);
        Review saved = reviewRepository.save(review);
        publishReviewEvent(saved, "REVIEW_APPROVED", RabbitMQConfig.REVIEW_APPROVED_ROUTING_KEY);
        log.info("Review {} APPROVED by admin.", id);
        audit("REVIEW_APPROVED", "REVIEW", String.valueOf(id), "SUCCESS", null,
                safeMeta("productId", review.getProductId(), "rating", review.getRating()));
        return ReviewDto.fromEntity(saved);
    }

    @Override
    @Transactional
    public ReviewDto rejectReview(Long id, String reason) {
        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with ID: " + id));

        if (review.getStatus() != ReviewStatus.PENDING) {
            throw new InvalidReviewStateException("Only PENDING reviews can be rejected. Current status: " + review.getStatus());
        }

        review.setStatus(ReviewStatus.REJECTED);
        Review saved = reviewRepository.save(review);
        publishReviewEvent(saved, "REVIEW_REJECTED", RabbitMQConfig.REVIEW_REJECTED_ROUTING_KEY);
        log.info("Review {} REJECTED by admin. Reason: {}", id, reason);
        audit("REVIEW_REJECTED", "REVIEW", String.valueOf(id), "SUCCESS", null,
                safeMeta("productId", review.getProductId(), "reason", reason));
        return ReviewDto.fromEntity(saved);
    }

    @Override
    @Transactional
    public ReviewDto hideReview(Long id) {
        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with ID: " + id));

        review.setStatus(ReviewStatus.HIDDEN);
        Review saved = reviewRepository.save(review);
        publishReviewEvent(saved, "REVIEW_DELETED", RabbitMQConfig.REVIEW_DELETED_ROUTING_KEY);
        log.info("Review {} HIDDEN by admin.", id);
        audit("REVIEW_HIDDEN", "REVIEW", String.valueOf(id), "SUCCESS", null,
                safeMeta("productId", review.getProductId()));
        return ReviewDto.fromEntity(saved);
    }

    private Pageable clampPageable(Pageable pageable) {
        int page = Math.max(0, pageable.getPageNumber());
        int size = pageable.getPageSize();
        if (size <= 0) {
            size = 10;
        } else if (size > 50) {
            size = 50;
        }
        Sort sort = pageable.getSort().isSorted() ? pageable.getSort() : Sort.by(Sort.Direction.DESC, "createdAt");
        return PageRequest.of(page, size, sort);
    }

    private void publishReviewEvent(Review review, String eventType, String routingKey) {
        try {
            ReviewEvent event = new ReviewEvent(
                    eventType,
                    review.getId(),
                    review.getProductId(),
                    review.getOrderId(),
                    review.getCustomerId(),
                    review.getRating()
            );
            CorrelationData correlationData = new CorrelationData("review-" + review.getId() + "-" + eventType);
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, routingKey, event, correlationData);
            log.info("Published {} event for review {}", eventType, review.getId());
        } catch (Exception e) {
            log.warn("Failed to publish RabbitMQ event {} for review {}: {}", eventType, review.getId(), e.getMessage());
        }
    }
}
