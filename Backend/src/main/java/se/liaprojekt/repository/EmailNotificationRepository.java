package se.liaprojekt.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import se.liaprojekt.model.EmailNotification;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EmailNotificationRepository
        extends JpaRepository<EmailNotification, Long> {

    List<EmailNotification> findByType(String type);

    List<EmailNotification> findByStatus(String status);

    List<EmailNotification> findBySentAtBetween(
            LocalDateTime start,
            LocalDateTime end
    );
}
