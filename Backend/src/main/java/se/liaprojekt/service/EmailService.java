package se.liaprojekt.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import se.liaprojekt.event.EmailEvent;
import se.liaprojekt.model.Course;
import se.liaprojekt.model.EmailType;
import se.liaprojekt.producer.EmailEventPublisher;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final EmailEventPublisher publisher;

    @Value("${app.email.enabled:true}")
    private boolean emailEnabled;

    public void sendTestResultEmail(String email, int score, String sectionName) {

        if (!emailEnabled) {
            log.info(
                    "Email disabled - skipped {} email to {}",
                    EmailType.TEST_RESULT,
                    email
            );
            return;
        }

        if (email == null || email.isBlank()) {
            log.warn("Skipping TEST_RESULT email — recipient address is empty");
            return;
        }

        EmailEvent event = new EmailEvent(
                email,
                "Test avklarat",
                """
                <h1>Grattis!</h1>
                <p>Du klarade testet till avsnitt %s%% med %d%%</p>
                """.formatted(sectionName, score),
                EmailType.TEST_RESULT
        );

        publisher.publish(event);
    }

    public void sendCourseCompletedEmail(String email, Course course) {

        if (!emailEnabled) {
            log.info(
                    "Email disabled - skipped {} email to {}",
                    EmailType.COURSE_COMPLETED,
                    email
            );
            return;
        }

        if (email == null || email.isBlank()) {
            log.warn("Skipping COURSE_COMPLETED email — recipient address is empty");
            return;
        }

        EmailEvent event = new EmailEvent(
                email,
                "Kurs avklarad 🎉",
                """
                <h1>Grattis!</h1>
                <p>Du har klarat kursen: %s</p>
                """.formatted(course.getTitle()),
                EmailType.COURSE_COMPLETED
        );

        publisher.publish(event);
    }

    public void sendWelcomeEmail(String email) {

        if (!emailEnabled) {
            log.info("Email disabled - skipped {} email to {}", EmailType.WELCOME_EMAIL, email);
            return;
        }

        if (email == null || email.isBlank()) {
            log.warn("Skipping WELCOME email — recipient address is empty");
            return;
        }

        EmailEvent event = new EmailEvent(
                email,
                "Välkommen!",
                """
                <h1>Välkommen!</h1>
                <p>Ditt konto har skapats.</p>
                """,
                EmailType.WELCOME_EMAIL
        );

        publisher.publish(event);
    }
}