package com.eshoppingzone.review.repository;

import com.eshoppingzone.review.entity.Review;
import com.eshoppingzone.review.enums.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByCustomerIdAndOrderItemId(Long customerId, Long orderItemId);

    Page<Review> findByProductIdAndStatusOrderByCreatedAtDesc(Long productId, ReviewStatus status, Pageable pageable);

    List<Review> findByProductIdAndStatus(Long productId, ReviewStatus status);

    Page<Review> findByStatusOrderByCreatedAtDesc(ReviewStatus status, Pageable pageable);

    Page<Review> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<Review> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    Optional<Review> findByIdAndCustomerId(Long id, Long customerId);
}
