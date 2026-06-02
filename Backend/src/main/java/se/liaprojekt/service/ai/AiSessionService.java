package se.liaprojekt.service.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import se.liaprojekt.model.AiSession;
import se.liaprojekt.model.Course;
import se.liaprojekt.repository.AiSessionRepository;
import se.liaprojekt.repository.CourseRepository;

@Service
@RequiredArgsConstructor
public class AiSessionService {

    private final AiSessionRepository repo;
    private final AzureAssistantClient client;
    private final CourseRepository courseRepo;

    // CREATE SESSION = CREATE THREAD
    public AiSession createSession(Long courseId) {

        Course course = courseRepo.findById(courseId)
                .orElseThrow();

        String threadId = client.createThread(course.getVectorStoreId());

        AiSession session = new AiSession();
        session.setThreadId(threadId);

        return repo.save(session);
    }

    public AiSession get(Long id) {
        return repo.findById(id).orElseThrow();
    }
}