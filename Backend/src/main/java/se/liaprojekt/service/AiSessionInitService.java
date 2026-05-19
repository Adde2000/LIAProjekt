package se.liaprojekt.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import se.liaprojekt.model.*;
import se.liaprojekt.repository.*;

@Service
@RequiredArgsConstructor
public class AiSessionInitService {

    private final AzureAssistantClient client;

    private final AiSessionRepository sessionRepo;
    private final AiCharacterRepository characterRepo;
    private final UserRepository userRepo;
    private final CourseRepository courseRepo;

    public AiSession createSession(
            Long userId,
            Long courseId,
            Long characterId
    ) {

        User user = userRepo.findById(userId)
                .orElseThrow();

        Course course = courseRepo.findById(courseId)
                .orElseThrow();

        AiCharacter character = characterRepo.findById(characterId)
                .orElseThrow();

        boolean valid = character.getCourses()
                .stream()
                .anyMatch(c -> c.getId().equals(courseId));

        if (!valid) {
            throw new RuntimeException("Character does not belong to course");
        }

        return sessionRepo
                .findByUser_IdAndCourse_IdAndAiCharacter_Id(
                        userId,
                        courseId,
                        characterId
                )
                .orElseGet(() -> createNewSession(user, course, character));
    }

    private AiSession createNewSession(
            User user,
            Course course,
            AiCharacter character
    ) {

        String threadId = client.createThread();

        AiSession session = new AiSession();

        session.setThreadId(threadId);
        session.setUser(user);
        session.setCourse(course);
        session.setAiCharacter(character);

        return sessionRepo.save(session);
    }
}