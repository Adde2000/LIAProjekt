package se.liaprojekt.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import se.liaprojekt.event.EmailEvent;
import se.liaprojekt.model.Course;
import se.liaprojekt.model.EmailType;
import se.liaprojekt.producer.EmailEventPublisher;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final EmailEventPublisher publisher;

    public void sendTestResultEmail(String email, int score) {

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