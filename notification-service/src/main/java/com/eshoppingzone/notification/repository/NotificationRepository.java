package com.eshoppingzone.notification.repository;

import com.eshoppingzone.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipientUserIdOrderByCreatedAtDesc(Long recipientUserId);

    List<Notification> findByRecipientRoleOrderByCreatedAtDesc(String recipientRole);

    List<Notification> findByRecipientUserIdOrRecipientRoleOrderByCreatedAtDesc(Long recipientUserId, String recipientRole);

    List<Notification> findAllByOrderByCreatedAtDesc();
}
