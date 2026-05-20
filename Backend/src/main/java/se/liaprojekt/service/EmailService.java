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

    public void sendTestResultEmail(String email, int score) {

        if (!emailEnabled) {
            log.info(
                    "Email disabled - skipped {} email to {}",
                    EmailType.TEST_RESULT,
                    email
            );
            return;
        }

        EmailEvent event = new EmailEvent(
                email,
                "Test avklarat",
                """
                <h1>Grattis!</h1>
                <p>Du klarade testet med %d%%</p>
                """.formatted(score),
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
}