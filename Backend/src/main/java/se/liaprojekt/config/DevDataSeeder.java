package se.liaprojekt.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import se.liaprojekt.model.*;
import se.liaprojekt.repository.*;

import java.util.List;

@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevDataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final SectionRepository sectionRepository;
    private final TestQuestionRepository questionRepository;
    private final AiCharacterRepository aiCharacterRepository;

    @Override
    public void run(String... args) {

        String entraId = "dev-user-1";

        // =========================
        // USER
        // =========================
        User user = userRepository.findByEntraId(entraId)
                .orElseGet(() -> userRepository.save(
                        User.builder().entraId(entraId).build()
                ));

        // =========================
        // COURSE
        // =========================
        Course course = new Course();
        course.setTitle("Java Fundamentals");
        course.setDescription("Learn Java step by step");
        course.setCreatedBy("dev");

        course = courseRepository.save(course);

        // =========================
        // SECTIONS
        // =========================
        Section s1 = new Section();
        s1.setTitle("Introduction");
        s1.setOrderIndex(0);
        s1.setCourse(course);

        Section s2 = new Section();
        s2.setTitle("OOP Basics");
        s2.setOrderIndex(1);
        s2.setCourse(course);

        sectionRepository.saveAll(List.of(s1, s2));

        // =========================
        // AI CHARACTER (NYTT)
        // =========================
        AiCharacter tutor = new AiCharacter();
        tutor.setName("Lisa Andersson");
        tutor.setDescription("Arbetar med kundreskontran (kundfakturor, betalningar, påminnelser)");

        // 👇 VIKTIGT: ersätt med riktig från Azure senare
        tutor.setAssistantId("asst_dWAq1YYVSh7jbtLNriioRIyL");

        aiCharacterRepository.save(tutor);

        // =========================
        // LINK AI TO COURSE (om du har relation)
        // =========================
        course.setAiCharacters(List.of(tutor));
        courseRepository.save(course);

        // =========================
        // QUESTIONS FOR SECTION 1
        // =========================
        createQuestion(
                s1,
                "What is JVM?",
                List.of(
                        answer("Java Virtual Machine", true),
                        answer("JavaScript VM", false),
                        answer("Database Engine", false),
                        answer("Operating System", false)
                )
        );

        createQuestion(
                s1,
                "What is Java?",
                List.of(
                        answer("Programming language", true),
                        answer("Browser", false),
                        answer("OS", false),
                        answer("Database", false)
                )
        );

        createQuestion(
                s2,
                "What is encapsulation?",
                List.of(
                        answer("Hiding internal state and requiring all interaction through methods", true),
                        answer("A type of loop", false),
                        answer("A database concept", false),
                        answer("A Java package", false)
                )
        );

        createQuestion(
                s2,
                "What is inheritance?",
                List.of(
                        answer("A mechanism where one class acquires properties of another", true),
                        answer("A database join", false),
                        answer("A REST concept", false),
                        answer("A variable type", false)
                )
        );

        System.out.println("🔥 DEV DATA SEEDED");
    }

    // =========================
    // HELPER METHODS
    // =========================
    private void createQuestion(Section section, String text, List<TestAnswer> answers) {

        TestQuestion q = new TestQuestion();
        q.setQuestionText(text);
        q.setSection(section);

        answers.forEach(a -> a.setQuestion(q));
        q.setAnswers(answers);

        questionRepository.save(q);
    }

    private TestAnswer answer(String text, boolean correct) {
        TestAnswer a = new TestAnswer();
        a.setAnswerText(text);
        a.setIsCorrect(correct);
        return a;
    }
}
