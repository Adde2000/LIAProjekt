package se.liaprojekt.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import se.liaprojekt.event.CourseCompletedEvent;
import se.liaprojekt.event.TestResultEvent;
import se.liaprojekt.exception.ResourceNotFoundException;
import se.liaprojekt.model.Course;
import se.liaprojekt.model.TestResult;
import se.liaprojekt.repository.TestResultRepository;
import se.liaprojekt.service.CourseService;
import se.liaprojekt.service.EmailService;
import se.liaprojekt.service.GraphService;

@Slf4j
@Component
@RequiredArgsConstructor
public class TestResultListener {

    private final TestResultRepository testResultRepository;
    private final CourseService courseService;
    private final EmailService emailService;
    private final GraphService graphService;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${app.allow-anonymous-events:false}")
    private boolean allowAnonymousEvents;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTestResult(TestResultEvent event) {

        // =========================
        // FETCH TEST RESULT
        // =========================
        TestResult result = testResultRepository
                .findById(event.testResultId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Test result not found: " + event.testResultId()
                        )
                );

        log.info("HANDLE_TEST_RESULT_EVENT | testResultId={}",
                result.getId()
        );

        // =========================
        // FETCH EMAIL FROM GRAPH
        // =========================
        String entraId = result.getUser().getEntraId();

        if ("anonymousUser".equals(entraId)) {

            log.info("Skipping email for anonymous user");

            if (allowAnonymousEvents) {
                return;
            }

            throw new IllegalStateException(
                    "Anonymous users are not allowed"
            );
        }

        String email = graphService
                .getUserByEntraId(entraId)
                .mail();

        // =========================
        // SEND TEST RESULT EMAIL
        // =========================
        emailService.sendTestResultEmail(
                email,
                result.getScore()
        );

        // =========================
        // CHECK COURSE COMPLETION
        // =========================
        Course course = result.getSection().getCourse();

        boolean completed = courseService.isCourseCompleted(
                result.getUser().getEntraId(),
                course
        );

        // =========================
        // PUBLISH COURSE COMPLETED EVENT
        // =========================
        if (completed) {

            log.info("COURSE_COMPLETED_EVENT | entraId={} courseId={}",
                    result.getUser().getEntraId(),
                    course.getId()
            );

            eventPublisher.publishEvent(
                    new CourseCompletedEvent(
                            result.getUser().getEntraId(),
                            course.getId()
                    )
            );
        }
    }
}