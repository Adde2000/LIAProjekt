package se.liaprojekt.listener;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import se.liaprojekt.dto.GraphResponse;
import se.liaprojekt.event.CourseCompletedEvent;
import se.liaprojekt.event.TestResultEvent;
import se.liaprojekt.model.Course;
import se.liaprojekt.model.TestResult;
import se.liaprojekt.repository.CourseRepository;
import se.liaprojekt.repository.TestResultRepository;
import se.liaprojekt.service.EmailService;
import se.liaprojekt.service.GraphService;

@Component
@RequiredArgsConstructor
public class EmailEventListener {

    private final EmailService emailService;
    private final TestResultRepository testResultRepository;
    private final CourseRepository courseRepository;
    private final GraphService graphService;

    @TransactionalEventListener
    public void onTestResult(TestResultEvent event) {

        TestResult result = testResultRepository
                .findById(event.testResultId())
                .orElseThrow();

        String email = graphService
                .getUserByEntraId(result.getUser().getEntraId())
                .mail();

        emailService.sendTestResultEmail(
                email,
                result.getScore()
        );
    }

    @TransactionalEventListener
    public void onCourseCompleted(CourseCompletedEvent event) {

        Course course = courseRepository
                .findById(event.courseId())
                .orElseThrow();

        GraphResponse graphUser = graphService
                .getUserByEntraId(event.entraId());

        emailService.sendCourseCompletedEmail(
                graphUser.mail(),
                course
        );
    }
}