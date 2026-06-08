package se.liaprojekt.listener;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import se.liaprojekt.dto.GraphResponse;
import se.liaprojekt.event.CourseCompletedEvent;
import se.liaprojekt.model.Course;
import se.liaprojekt.repository.CourseRepository;
import se.liaprojekt.service.EmailService;
import se.liaprojekt.service.GraphService;

@Component
@RequiredArgsConstructor
public class EmailEventListener {

    private final EmailService emailService;
    private final CourseRepository courseRepository;
    private final GraphService graphService;

    @Value("${app.allow-anonymous-events:false}")
    private boolean allowAnonymousEvents;

    @TransactionalEventListener
    public void onCourseCompleted(CourseCompletedEvent event) {

        Course course = courseRepository
                .findById(event.courseId())
                .orElseThrow();

        String entraId = event.entraId();

        if ("anonymousUser".equals(entraId)) {

            if (allowAnonymousEvents) {
                return;
            }

            throw new IllegalStateException(
                    "Anonymous users are not allowed"
            );
        }

        GraphResponse graphUser = graphService
                .getUserByEntraId(entraId);

        emailService.sendCourseCompletedEmail(
                graphUser.mail(),
                course
        );
    }
}