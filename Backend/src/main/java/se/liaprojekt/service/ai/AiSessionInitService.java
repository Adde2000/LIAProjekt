package se.liaprojekt.service.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import se.liaprojekt.exception.BadRequestException;
import se.liaprojekt.exception.ResourceNotFoundException;
import se.liaprojekt.model.AiSession;
import se.liaprojekt.model.Course;
import se.liaprojekt.model.User;
import se.liaprojekt.repository.AiSessionRepository;
import se.liaprojekt.repository.CourseRepository;
import se.liaprojekt.repository.UserRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AiSessionInitService {

    private final AzureAssistantClient client;
    private final VectorStoreService vectorStoreService;

    private final AiSessionRepository sessionRepo;
    private final UserRepository userRepo;
    private final CourseRepository courseRepo;

    public AiSession createSession(
            Long userId,
            Long courseId
    ) {

        // =========================
        // GET USER
        // =========================

        User user = userRepo.findById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found: " + userId
                        )
                );

        // =========================
        // GET COURSE
        // =========================

        Course course = courseRepo.findById(courseId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Course not found: " + courseId
                        )
                );
        vectorStoreService.initializeCourseVectorStore(course);

        // =========================
        // VALIDATE ASSISTANT
        // =========================

        String assistantId = course.getAssistantId();

        if (assistantId == null || assistantId.isBlank()) {
            throw new BadRequestException(
                    "Course does not have an AI assistant assigned"
            );
        }

        // =========================
        // FIND EXISTING SESSION
        // =========================

        List<AiSession> existingSessions =
                sessionRepo.findAllByUser_IdAndCourse_Id(
                        userId,
                        courseId
                );

        // =========================
        // REUSE EXISTING SESSION
        // =========================

        if (!existingSessions.isEmpty()) {

            // OPTIONAL:
            // CLEAN UP DUPLICATES IF THEY EXIST

            if (existingSessions.size() > 1) {

                AiSession keepSession = existingSessions.get(0);

                List<AiSession> duplicates =
                        existingSessions.subList(
                                1,
                                existingSessions.size()
                        );

                sessionRepo.deleteAll(duplicates);

                return keepSession;
            }

            return existingSessions.get(0);
        }

        // =========================
        // CREATE NEW SESSION
        // =========================

        return createNewSession(
                user,
                course
        );
    }

    private AiSession createNewSession(
            User user,
            Course course
    ) {

        // =========================
        // CREATE AZURE THREAD
        // =========================

        String vectorStoreId = course.getVectorStoreId();

        if (vectorStoreId == null || vectorStoreId.isBlank()) {
            throw new IllegalStateException(
                    "Course has no vector store configured"
            );
        }

        String threadId = client.createThread(course.getVectorStoreId());

        // =========================
        // CREATE SESSION
        // =========================

        AiSession session = new AiSession();

        session.setThreadId(threadId);
        session.setUser(user);
        session.setCourse(course);

        return sessionRepo.save(session);
    }
}