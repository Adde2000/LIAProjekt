package se.liaprojekt.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import se.liaprojekt.model.EmailNotification;
import se.liaprojekt.model.EmailStatus;
import se.liaprojekt.model.EmailType;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EmailNotificationRepository
        extends JpaRepository<EmailNotification, Long> {

    List<EmailNotification> findByType(EmailType type);

    List<EmailNotification> findByStatus(EmailStatus status);

    List<EmailNotification> findBySentAtBetween(
            LocalDateTime start,
            LocalDateTime end
    );

    boolean existsByUser_IdAndType(Long userId, EmailType type);
}
